import { MobilityLearningRun } from './MobilityRun';
import { lifecycleAt, positionMetrics } from './mobility-display';

describe('Mobility lifecycle display', () => {
  const run = {events: [
    {event:'TRAINING_STARTED',sim_time_ms:40,model_version:'v0001'},
    {event:'TRAINING_COMPLETE',sim_time_ms:40,model_version:'v0001'},
    {event:'QUALITY_DECISION',sim_time_ms:104,model_version:'v0001',decision:'PASS'},
    {event:'PROMOTED',sim_time_ms:104,model_version:'v0001'},
    {event:'FALLBACK',sim_time_ms:110,model_version:'v0001'},
    {event:'TRAINING_STARTED',sim_time_ms:110,model_version:'v0002'},
    {event:'TRAINING_COMPLETE',sim_time_ms:110,model_version:'v0002'}
  ],evaluations:[{model:'LSTM',model_version:'v0001',target_ms:41,position_error_m:3},
    {model:'LSTM',model_version:'v0001',target_ms:104,position_error_m:4},
    {model:'LSTM',model_version:'v0002',target_ms:111,position_error_m:5}]} as unknown as MobilityLearningRun;
  it('does not show future qualification, selected ownership or errors during evaluation',()=>{
    const state=lifecycleAt(run,41);expect(state.state).toBe('EVALUATING');expect(state.owner).toBe('HoldPosition');
    expect(state.matched).toBe(1);expect(state.promotions).toBe(0);
  });
  it('shows activation and subsequent fallback with the new independent version',()=>{
    expect(lifecycleAt(run,104).owner).toBe('LSTM');
    const state=lifecycleAt(run,110);expect(state.owner).toBe('HoldPosition');expect(state.version).toBe('v0002');
    expect(state.state).toBe('EVALUATING');expect(state.matched).toBe(0);expect(state.fallbacks).toBe(1);
  });
  it('distinguishes no evaluation from zero error and calculates position RMSE',()=>{
    expect(positionMetrics([]).mean).toBeNull();
    expect(positionMetrics(run.evaluations.slice(0,2)).mean).toBe(3.5);
    expect(positionMetrics(run.evaluations.slice(0,2)).rmse).toBeCloseTo(Math.sqrt(12.5));
  });
});
