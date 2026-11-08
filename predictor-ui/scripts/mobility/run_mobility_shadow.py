"""One no-UI development scenario with synchronous frozen LSTM shadow inference."""
import argparse
from datetime import datetime,timezone
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import uuid
import numpy as np
from run_mobility_batch import prepare,MAIN,BASE
from build_mobility_windows import windows_for_run,read_json,write_json,require,sha256
from check_mobility_run import rows,validate_run
from check_dataset_plan import read_properties
from mobility_learning import position_errors, error_metrics
from mobility_shadow_evaluation import MODELS, EVALUATION_FIELDS


def audit_evaluations(output, samples, predictions, inputs):
    """Post-run independent recomputation; never supplies targets to live inference."""
    evaluated,header=rows(output/'shadow_evaluations.csv')
    require(header==EVALUATION_FIELDS and len(evaluated)==len(predictions)==336,'Evaluation row/header mismatch')
    sample_lookup={int(r['time_ms']):r for r in samples}
    forecast_lookup={(p['model'],int(p['origin_ms'])):p for p in predictions}
    require(len(forecast_lookup)==len(predictions),'Duplicate forecast')
    seen=set();errors={model:[] for model in MODELS}
    for e in evaluated:
        key=(e['model'],int(e['origin_ms']))
        require(key in forecast_lookup and key not in seen,'Duplicate or unissued evaluation')
        seen.add(key);p=forecast_lookup[key];target=int(p['target_ms'])
        require(int(e['target_ms'])==target==int(e['evaluated_at_sim_ms'])
                and target==key[1]+60000,'Evaluation happened at the wrong simulation time')
        require(e['device_id']==p['device_id']=='0' and e['origin_node']==p['origin_node'],
                'Evaluation forecast identity mismatch')
        actual=sample_lookup[target]
        require(e['run_id']==actual['run_id'] and e['device_id']==actual['device_id'],
                'Evaluation observation identity mismatch')
        for field in ('predicted_latitude_deg','predicted_longitude_deg'):
            require(float(e[field])==float(p[field]),'Evaluation changed the issued forecast')
        require(float(e['actual_latitude_deg'])==float(actual['latitude_deg'])
                and float(e['actual_longitude_deg'])==float(actual['longitude_deg']),
                'Evaluation did not use the actual target observation')
        error=float(position_errors(np.array([[float(p['predicted_latitude_deg']),float(p['predicted_longitude_deg'])]]),
                                    np.array([[float(actual['latitude_deg']),float(actual['longitude_deg'])]]))[0])
        require(np.isfinite(float(e['position_error_m'])) and np.isclose(float(e['position_error_m']),error,
                rtol=1e-10,atol=1e-7),'Position error recomputation failed')
        errors[e['model']].append(error)
    require(seen==set(forecast_lookup),'Unmatched forecast')
    events=[json.loads(line) for line in (output/'shadow_events.jsonl').read_text().splitlines()]
    origins={w['origin_ms']:w for w in inputs};targets={w['target_ms']:w for w in inputs}
    expected=[]
    def add(kind,now,**details):
        expected.append(dict(sequence=len(expected)+1,event=kind,sim_time_ms=now,**details))
    for r in samples:
        now=int(r['time_ms']);add('OBSERVATION',now,run_id=r['run_id'],device_id=r['device_id'])
        if now in targets:
            add('EVALUATION',now,origin_ms=targets[now]['origin_ms'],target_ms=now,models=list(MODELS))
        if now in origins:
            add('PREDICTION',now,origin_ms=now,target_ms=origins[now]['target_ms'],models=list(MODELS))
    require(events==expected,'Stream event order mismatch: observe, evaluate old forecast, issue next')
    summary=read_json(output/'shadow_metrics.json')
    require(summary['status']=='COMPLETE' and summary['evaluation']=='TARGET_TIME_MATCH'
            and summary['role']=='development_integration_check' and summary['horizon_ms']==60000
            and summary['prediction_origins']==112 and summary['evaluation_rows']==336
            and summary['pending_forecasts']==0 and summary['earth_radius_m']==6378137.
            and summary['error_definition']=='great_circle_position_error_m'
            and set(summary['metrics'])==set(MODELS),'Evaluation summary protocol mismatch')
    for model in MODELS:
        metrics=error_metrics(errors[model]);require(metrics['count']==112,'Unequal method counts')
        require(set(summary['metrics'][model])==set(metrics),'Metric names mismatch')
        for name,value in metrics.items():
            require(np.isclose(summary['metrics'][model][name],value,rtol=1e-10,atol=1e-7),
                    'Summary metric recomputation failed: '+model+'/'+name)
    return {'evaluation':'TARGET_TIME_MATCH','evaluation_rows':336,'matched_origins':112,
            'pending_forecasts':0,'event_order':'PASSED','error_recomputation':'PASSED',
            'metrics':summary['metrics']}


def execute_owned(command,root,log,env):
    with log.open('wb') as stream:
        p=subprocess.Popen(command,cwd=root,env=env,stdout=stream,stderr=subprocess.STDOUT,
                           start_new_session=os.name!='nt')
        try:return p.wait(timeout=300)
        except BaseException:
            if os.name=='nt':subprocess.run(['taskkill','/PID',str(p.pid),'/T','/F'],capture_output=True,check=False)
            else:
                try:os.killpg(p.pid,signal.SIGKILL)
                except ProcessLookupError:pass
            p.wait();raise


def audit_shadow(output,case):
    worker=read_json(output/'shadow-worker.json')
    require(worker['status']=='COMPLETE' and worker['observations']==121
            and worker['prediction_origins']==112 and worker['evaluation']=='TARGET_TIME_MATCH'
            and worker['evaluation_rows']==336 and worker['pending_forecasts']==0
            and worker['training'] is False and worker['scaler_fitting'] is False
            and worker['connection_control'] is False,'Incomplete/unfrozen worker run')
    samples,_=rows(output/'mobility_samples.csv');received,_=rows(output/'shadow_received_samples.csv')
    require(samples==received,'Worker did not receive exact simulator samples')
    run={'case_id':case['case_id'],'route_id':'development_triangle','split':'development','run_id':output.name}
    expected,skipped=windows_for_run(run,samples,[47.73,18.27])
    actual=[json.loads(line) for line in (output/'shadow_inputs.jsonl').read_text().splitlines()]
    require(len(expected)==len(actual)==112 and skipped==1,'Window count mismatch')
    for a,b in zip(actual,expected):
        require(a['run_id']==output.name and a['device_id']=='0'
                and a['origin_ms']==b['origin_ms'] and a['target_ms']==b['target_ms']
                and a['history_time_ms']==b['history_time_ms'],'History alignment mismatch')
        require(np.array_equal(np.asarray(a['x']),np.asarray(b['x'])),'Online/offline features differ')
        require(max(a['history_time_ms'])==a['origin_ms']<a['target_ms'],'Future sample in history')
    predictions,_=rows(output/'shadow_predictions.csv');receipts,_=rows(output/'shadow_receipts.csv')
    require(len(predictions)==336 and len(receipts)==112,'Prediction/receipt count mismatch')
    seen=set();lookup={}
    for p in predictions:
        key=(p['model'],int(p['origin_ms']))
        require(key not in seen and p['model'] in ('HoldPosition','ConstantVelocity','LSTM'),'Duplicate/unknown prediction')
        seen.add(key);lookup[key]=p
        require(int(p['target_ms'])==int(p['origin_ms'])+60000,'Prediction horizon mismatch')
        require(all(np.isfinite(float(p[k])) and abs(float(p[k]))<=limit for k,limit in
                    [('predicted_latitude_deg',90),('predicted_longitude_deg',180)]),'Invalid GPS prediction')
    for r,b in zip(receipts,expected):
        now=int(r['origin_ms']);require(now==b['origin_ms'] and int(r['received_at_sim_ms'])==now
                and int(r['target_ms'])==b['target_ms'],'Reply received after simulation advanced')
        for model in ('HoldPosition','ConstantVelocity','LSTM'):
            require((model,now) in lookup,'Missing model prediction')
        p=lookup['LSTM',now]
        require(float(r['predicted_latitude'])==float(p['predicted_latitude_deg'])
                and float(r['predicted_longitude'])==float(p['predicted_longitude_deg']),'Java received different forecast')
    scoring=audit_evaluations(output,samples,predictions,actual)
    return {'observations':121,'prediction_origins':112,'prediction_rows':336,
            'feature_parity':'EXACT','reply_timing':'SAME_SIMULATION_TICK',
            **scoring}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--training',type=Path,required=True)
    parser.add_argument('--project-root',type=Path,default=Path(__file__).resolve().parents[3])
    parser.add_argument('--java',default='java');args=parser.parse_args()
    session=state=None
    try:
        root=args.project_root.resolve();training=args.training.resolve()
        t=read_json(training/'training.json')
        require(t['status']=='COMPLETE' and t['mode']=='pilot_training','Use a completed training run')
        for name,digest in t['artifact_sha256'].items():
            from evaluate_mobility_test import child
            require(sha256(child(training,name))==digest,'Training artifact changed: '+name)
        java=shutil.which(args.java);require(java is not None,'JDK java not found')
        session=root/'simulator/sim_res/mobility-shadow'/(
            datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex[:12])
        session.mkdir(parents=True,exist_ok=False)
        state={'schema_version':1,'status':'PREPARING','training_path':str(training),
               'training_manifest_sha256':sha256(training/'training.json'),
               'scope':'STEP2_TARGET_TIME_EVALUATION','scenario_role':'development_integration_check',
               'training':False,'connection_control':False}
        write_json(session/'shadow-session.json',state);print('Shadow session: '+str(session),flush=True)
        shutil.copytree(training,session/'model')
        scripts=session/'scripts';scripts.mkdir()
        for name in ('mobility_shadow_worker.py','mobility_shadow_evaluation.py','run_mobility_shadow.py','mobility_learning.py',
                     'build_mobility_windows.py','evaluate_mobility_test.py','check_mobility_run.py',
                     'check_dataset_plan.py','run_mobility_batch.py'):
            shutil.copyfile(Path(__file__).parent/name,scripts/name)
        scenario=session/'scenario.properties'
        shutil.copyfile(root/'simulator/src/main/resources/demo/mobility/triangle.properties',scenario)
        props=read_properties(scenario)
        case={'case_id':props['scenario_name'],'seed':int(props['seed']),'speed_m_s':float(props['speed_m_s'])}
        classpath,xml=prepare(root,session,java)
        parent=session/'output';parent.mkdir()
        command=[java,'-cp',classpath,MAIN,'--no-ui','--scenario='+str(scenario),str(xml),str(parent)]
        state.update(status='RUNNING',command=command,python=sys.executable)
        write_json(session/'shadow-session.json',state)
        env=os.environ.copy();env.update(MOBILITY_SHADOW_PYTHON=sys.executable,
            MOBILITY_SHADOW_SCRIPT=str(scripts/'mobility_shadow_worker.py'),MOBILITY_SHADOW_MODEL=str(session/'model'))
        code=execute_owned(command,root,session/'simulation.log',env)
        outputs=list(parent.glob('mobility-*'));require(len(outputs)==1,'Expected one simulation output')
        output=outputs[0];state['output_directory']=str(output.relative_to(session));state['exit_code']=code
        require(code==0,'Simulation/worker failed; inspect simulation.log and output/*/shadow-worker.log')
        log=(session/'simulation.log').read_text(encoding='utf-8',errors='replace')
        require('MOBILITY_SHADOW_INFERENCE_OK' in log and 'MOBILITY_SIMULATION_OK' in log,'Completion marker missing')
        checks=validate_run(output,case,scenario);shadow=audit_shadow(output,case)
        require(sha256(training/'training.json')==state['training_manifest_sha256'],'Training manifest changed')
        for name,digest in t['artifact_sha256'].items():
            require(sha256(child(training,name))==digest and sha256(child(session/'model',name))==digest,
                    'Frozen training artifact changed')
        state.update(status='COMPLETE',simulation_checks=checks,shadow_checks=shadow)
        state['output_sha256']={p.name:sha256(p) for p in output.iterdir() if p.is_file()}
        write_json(session/'shadow-session.json',state)
        print('observations=121 prediction_origins=112 methods=3')
        print('feature_parity=EXACT reply_timing=SAME_SIMULATION_TICK accounting=PASSED')
        print('matched_origins=112 evaluation_rows=336 pending_forecasts=0')
        print('evaluation=TARGET_TIME_MATCH event_order=PASSED error_recomputation=PASSED')
        print('training=DISABLED connection_control=DISABLED')
        print('model,mean_error_m,rmse_m,p90_error_m')
        for model in MODELS:
            m=shadow['metrics'][model]
            print('%s,%.3f,%.3f,%.3f'%(model,m['mean_error_m'],m['rmse_m'],m['p90_error_m']))
        print('MOBILITY_SHADOW_STEP2_OK');print('Session manifest: '+str(session/'shadow-session.json'))
        return 0
    except Exception as error:
        if state is not None:
            state.update(status='FAILED',error=str(error));write_json(session/'shadow-session.json',state)
        print('MOBILITY_SHADOW_STEP2_FAILED: '+str(error),file=sys.stderr);return 1


if __name__=='__main__':sys.exit(main())
