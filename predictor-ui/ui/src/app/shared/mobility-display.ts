import { MobilityLearningRun, MobilityMethod, PositionEvaluation } from './MobilityRun';

/** A display window only: retain recorded coordinates and never include future observations.
 * Anchor to the last observation so final task draining does not erase the path. */
export function recentMovement<T extends {time_ms?:number}>(rows:T[], time:number, minutes:number):T[] {
  const known=rows.filter(row=>row.time_ms!==undefined && row.time_ms<=time);
  if(!minutes || !known.length) return known;
  const start=known[known.length-1].time_ms!-minutes*60000;
  return known.filter(row=>row.time_ms!>=start);
}

/** Replay state uses only events already observed at the selected simulation time. */
export function lifecycleAt(run: MobilityLearningRun, time: number) {
  let version = '', state = 'WARMUP', decision = 'WAITING_FOR_EVALUATION';
  let owner: MobilityMethod = 'HoldPosition';
  let promotions = 0, fallbacks = 0;
  for (const event of run.events) {
    if (event.sim_time_ms > time) break;
    if (event.event === 'TRAINING_STARTED') {
      version = event.model_version!; state = 'TRAINING'; decision = 'WAITING_FOR_EVALUATION';
    }
    if (event.model_version === version) {
      if (event.event === 'TRAINING_COMPLETE') state = 'EVALUATING';
      if (event.event === 'QUALITY_DECISION') { decision = event.decision!; state = decision === 'PASS' ? 'READY' : 'FAILED'; }
      if (event.event === 'PROMOTED') state = 'ACTIVE';
      if (event.event === 'MONITORED') state = event.state!;
      if (event.event === 'FALLBACK') state = 'DEGRADED';
    }
    if (event.event === 'PROMOTED') { owner = 'LSTM'; promotions++; }
    if (event.event === 'FALLBACK') { owner = 'HoldPosition'; fallbacks++; }
  }
  const matched = run.evaluations.filter(e => e.model === 'LSTM' && e.model_version === version && e.target_ms <= time).length;
  return { version, state, decision, owner, promotions, fallbacks, matched };
}

export function positionMetrics(rows: PositionEvaluation[]) {
  return { count: rows.length,
    mean: rows.length ? rows.reduce((sum, r) => sum + r.position_error_m, 0) / rows.length : null,
    rmse: rows.length ? Math.sqrt(rows.reduce((sum, r) => sum + r.position_error_m ** 2, 0) / rows.length) : null };
}

export function stateLabel(state: string): string {
  const labels: Record<string, string> = { WARMUP: 'Collecting history', TRAINING: 'Training', EVALUATING: 'Evaluating',
    READY: 'Ready', ACTIVE: 'Active', FAILED: 'Rejected', DEGRADED: 'Degraded',
    WAITING_FOR_EVALUATION: 'Awaiting 64 targets', PASS: 'Passed', REJECT: 'Rejected' };
  return labels[state] ?? state;
}
