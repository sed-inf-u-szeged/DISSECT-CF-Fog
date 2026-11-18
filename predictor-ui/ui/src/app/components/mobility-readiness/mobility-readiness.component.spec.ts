import {MobilityReadinessComponent} from './mobility-readiness.component';
describe('Mobility preparation progress',()=>{
  it('counts observed training windows without pretending that baselines train',()=>{
    const c=new MobilityReadinessComponent();c.sampleCount=1;c.methods=['TCN'];
    expect(c.cards.map(r=>r.model)).toEqual(['LSTM','TCN']);expect(c.cards[0].value).toBe(0);
    c.sampleCount=40;expect(c.cards[0].value).toBe(31);expect(c.cards[0].maximum).toBe(32);
    expect(c.cards[0].note).toContain('40 / 41 observations');
  });
  it('reports real training without fabricating an epoch percentage and then counts matured targets',()=>{
    const c=new MobilityReadinessComponent();c.methods=['TCN'];
    c.live={serving:'HoldPosition',candidate:'v0001',state:'TRAINING',matched:0,promotions:0,fallbacks:0,error_m:null,error_target_ms:0,observations:41};
    expect(c.cards[0].value).toBeNull();expect(c.cards[0].state).toBe('Training');
    c.live.state='EVALUATING';c.live.matched=3;
    expect(c.cards[0].value).toBe(3);expect(c.cards[0].maximum).toBe(64);
    expect(c.cards[1].state).toBe('Shadow comparison');expect(c.cards[1].maximum).toBe(0);
  });
});
