"""One UI-owned mobility run, using the existing audited multi-device runner."""
import argparse
import contextlib
import json
import math
import os
from pathlib import Path
import signal
import sys


def validate_config(value):
    if not isinstance(value, dict):
        raise ValueError('Expected a mobility configuration')
    allowed = {'strategy', 'devices', 'minutes', 'drainMinutes', 'seed', 'latitude', 'longitude',
               'speedMin', 'speedMax', 'radius', 'speed', 'waypoints', 'repeatRoute', 'models',
               'loadModels', 'loadProfile', 'loadEpochs', 'infrastructure', 'cityPlacement', 'cityCoverageKm', 'deviceWorkload'}
    if set(value) - allowed:
        raise ValueError('Unknown mobility setting')
    cfg = dict(value)
    cfg['deviceWorkload'] = cfg.get('deviceWorkload', 'constant')
    if cfg['deviceWorkload'] not in ('constant', 'periodic-burst'):
        raise ValueError('Choose Constant or Periodic bursts for device workload')
    from mobility_comparison_models import validate_methods
    cfg["models"] = validate_methods(cfg.get("models", []))
    from node_load_worker import validate_options
    cfg['loadModels'] = cfg.get('loadModels', [])
    cfg['loadProfile'] = cfg.get('loadProfile', 'candidate')
    cfg['loadEpochs'] = cfg.get('loadEpochs', 40)
    validate_options(cfg['loadModels'], cfg['loadProfile'], cfg['loadEpochs'])
    if cfg.get('strategy') not in ('random-walk', 'nomadic'):
        raise ValueError('Choose Random walk or Nomadic')
    def number(name, low, high, integer=False):
        x = cfg.get(name)
        if isinstance(x, bool) or not isinstance(x, (int, float)) or not math.isfinite(x) or not low <= x <= high or (integer and int(x) != x):
            raise ValueError('%s must be %s to %s' % (name, low, high))
        cfg[name] = int(x) if integer else float(x)
    for key, low, high in [('devices', 1, 100), ('minutes', 104, 1440), ('drainMinutes', 1, 1440), ('seed', 0, 2147483647)]:
        number(key, low, high, True)
    number('latitude', -85, 85); number('longitude', -180, 180)
    cfg['infrastructure'] = cfg.get('infrastructure', 'two-node')
    if cfg['infrastructure'] not in ('two-node', 'hungary-cities'):
        raise ValueError('Unknown infrastructure')
    if cfg['infrastructure'] == 'hungary-cities':
        cfg['cityPlacement'] = cfg.get('cityPlacement', 'distributed')
        cities = ('Sopron_fog2', 'Szombathely_fog2', 'Gyor_fog1', 'Szeged_fog2', 'Pecs_fog2',
                  'Kecskemet_fog1', 'Miskolc_fog2', 'Debrecen_fog2', 'Gyongyos_fog1')
        if cfg['cityPlacement'] not in ('distributed', *cities):
            raise ValueError('Choose a fog city or distribute devices across all nine fog cities')
        cfg['cityCoverageKm'] = cfg.get('cityCoverageKm', 20)
        number('cityCoverageKm', 1, 100, True)
    if cfg['strategy'] == 'random-walk':
        number('speedMin', 0, 50); number('speedMax', cfg['speedMin'], 50); number('radius', 1, 10000)
        if cfg['radius'] <= cfg['speedMax'] * 60:
            raise ValueError('Radius must exceed maximum speed × 60 seconds')
    else:
        number('speed', .1, 50)
        if type(cfg.get('repeatRoute')) is not bool:
            raise ValueError('repeatRoute must be boolean')
        points = cfg.get('waypoints')
        if not isinstance(points, list) or not 2 <= len(points) <= 100:
            raise ValueError('Provide 2 to 100 waypoints')
        for point in points:
            if not isinstance(point, list) or len(point) != 2 or any(type(x) not in (int, float) or not math.isfinite(x) or abs(x) > 10000 for x in point):
                raise ValueError('Each waypoint must be an east/north pair within ±10,000 m')
        if points[0] != [0, 0]:
            raise ValueError('The first waypoint must be 0,0')
        distances = [math.dist(a,b) for a,b in zip(points,points[1:])]
        if min(distances) < 1 or sum(distances) < 100:
            raise ValueError('Adjacent waypoints must be at least 1 m apart; route length at least 100 m')
        if cfg['repeatRoute'] and (len(points) < 3 or points[-1] != [0, 0]):
            raise ValueError('A repeating route must return to 0,0')
    return cfg


def scenario_text(cfg):
    cfg = validate_config(cfg)
    data = dict(scenario_name='ui-' + cfg['strategy'], seed=cfg['seed'],
                mobility_strategy=cfg['strategy'].replace('-', '_'), start_latitude_deg=cfg['latitude'],
                start_longitude_deg=cfg['longitude'])
    data['infrastructure'] = cfg['infrastructure']
    data['device_workload'] = cfg['deviceWorkload']
    if cfg['infrastructure'] == 'hungary-cities':
        data.update(city_placement=cfg['cityPlacement'], city_coverage_km=cfg['cityCoverageKm'])
    if cfg['strategy'] == 'random-walk':
        data.update(speed_min_m_s=cfg['speedMin'], speed_max_m_s=cfg['speedMax'], radius_m=cfg['radius'])
    else:
        data.update(speed_m_s=cfg['speed'], route_repeats=str(cfg['repeatRoute']).lower(),
                    waypoints_m=';'.join(','.join(str(x) for x in p) for p in cfg['waypoints']))
    return ''.join('%s=%s\n' % item for item in data.items())


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True, type=Path)
    parser.add_argument('--session', required=True, type=Path)
    parser.add_argument('--project-root', required=True, type=Path)
    args = parser.parse_args(argv)
    session = args.session.resolve()
    session.mkdir(parents=True, exist_ok=True)
    state = {'schema_version':1, 'stage':'PREPARING'}
    def progress(stage, **details):
        state.update(stage=stage, **details)
        temp = session / 'progress.tmp'
        temp.write_text(json.dumps(state, allow_nan=False) + '\n', encoding='utf-8')
        os.replace(temp, session / 'progress.json')
    def stop(*_):
        # The JVM uses its own process group; terminate all descendants explicitly.
        try:
            import psutil
            for child in reversed(psutil.Process().children(recursive=True)):
                try: child.kill()
                except psutil.Error: pass
        finally:
            raise KeyboardInterrupt()
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    try:
        cfg = validate_config(json.loads(args.config.read_text(encoding='utf-8-sig')))
        root = args.project_root.resolve()
        jar = root / 'simulator/target/dissect-cf-fog-1.0.0-SNAPSHOT-jar-with-dependencies.jar'
        if not jar.is_file() or not (root / 'simulator/target/classes').is_dir():
            raise ValueError('Build the simulator first: mvn -f simulator/pom.xml package -DskipTests')
        (session / 'effective-config.json').write_text(json.dumps(cfg, indent=2) + '\n', encoding='utf-8')
        os.environ['MOBILITY_LIVE_FILE'] = str(session / 'live.jsonl')
        progress('PREPARING')
        with (session / 'runner.log').open('w', encoding='utf-8', buffering=1) as log, contextlib.redirect_stdout(log), contextlib.redirect_stderr(log):
            from run_mobility_multi import main as run
            result = run(['--project-root', str(root), '--devices', str(cfg['devices']), '--minutes', str(cfg['minutes']),
                          '--drain-minutes', str(cfg['drainMinutes']), '--strategy', cfg['strategy'], '--repeats', '1',
                          '--load-profile', cfg['loadProfile'], '--load-epochs', str(cfg['loadEpochs']),
                          '--load-models', *cfg['loadModels'], '--comparison-models', *cfg['models']],
                         scenario_override=scenario_text(cfg), progress=progress)
        return result
    except KeyboardInterrupt:
        progress('CANCELLED')
        return 130
    except Exception as error:
        progress('FAILED', error=str(error))
        print('Mobility UI run failed: ' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
