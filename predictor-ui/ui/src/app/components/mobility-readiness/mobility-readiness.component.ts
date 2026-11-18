import {Component, Input} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MobilityLearningRun, MobilityComparisonRun, ComparisonMethod} from '../../shared/MobilityRun';
import {MobilityLiveEvent} from '../../shared/MobilityJob';
import {lifecycleAt, stateLabel} from '../../shared/mobility-display';

@Component({selector:'app-mobility-readiness',standalone:true,imports:[CommonModule],
  template:`<p class="note">Position baselines need no training: HoldPosition uses one observation; ConstantVelocity uses two. Errors require the later, real target position.</p>
    <div class="cards"><section *ngFor="let c of cards"><strong>{{c.model}} · {{c.state}}</strong>
      <p>{{c.note}}</p><progress *ngIf="c.maximum" [max]="c.maximum" [attr.value]="c.value" [attr.aria-label]="c.model+' '+c.state"></progress>
    </section></div>`,
  styles:[`:host{display:block;margin-bottom:16px}.note,section p{font-size:12px;line-height:1.5;color:#b8c5da;margin:8px 0}.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:10px}section{border:1px solid #42434b;border-radius:4px;padding:12px}strong{font-size:13px;color:#ffd174}progress{width:100%;height:6px;accent-color:#ffd174}`]})
export class MobilityReadinessComponent {
  @Input() learning?:MobilityLearningRun;
  @Input() comparison?:MobilityComparisonRun;
  @Input() time=0;
  @Input() live?:MobilityLiveEvent['lifecycle'];
  @Input() sampleCount=0;
  @Input() methods:ComparisonMethod[]=[];
  get cards():{model:string;state:string;note:string;maximum:number;value:number|null}[] {
    const samples=this.learning?.samples.filter(s=>s.time_ms<=this.time).length ?? this.live?.observations ?? this.sampleCount;
    const control=this.learning?.events.filter(e=>e.event==='CONTROL' && e.sim_time_ms<=this.time).at(-1);
    const required=control?.window_size ?? this.live?.required_windows ?? 32;
    const available=control?.available_windows ?? this.live?.available_windows ?? Math.max(0,samples-9);
    const status=this.learning ? lifecycleAt(this.learning,this.time) : undefined;
    const state=status?.state ?? this.live?.state ?? 'WARMUP';
    const version=status?.version ?? this.live?.candidate ?? '';
    const matched=status?.matched ?? this.live?.matched ?? 0;
    const names=['LSTM',...(this.comparison?.methods ?? this.methods)];
    return names.map(model=>{
      const fitted=model==='LSTM' ? !!version : this.learning
        ? !!this.comparison?.versions.some(v=>v.model===model && v.training_cutoff_sim_ms<=this.time) : !!version;
      if(state==='TRAINING') return {model,state:'Training',note:'Fitting the observed training snapshot. Wall-clock progress is not recorded as a percentage.',maximum:1,value:null};
      if(!fitted) return {model,state:'Collecting history',note:`${samples} / ${required+9} observations · ${Math.min(available,required)} / ${required} training windows ready.`,maximum:required,value:Math.min(available,required)};
      if(model!=='LSTM') return {model,state:'Shadow comparison',note:model==='IMM/Kalman'?'Fixed parameters; forecasts use the shared observation history.':'Trained on the shared snapshot. Errors appear when real target positions arrive.',maximum:0,value:0};
      if(state==='EVALUATING') return {model,state:'Evaluating',note:`${Math.min(64,matched)} / 64 real targets observed. Candidate forecasts and errors are already visible.`,maximum:64,value:Math.min(64,matched)};
      return {model,state:stateLabel(state),note:['FAILED','DEGRADED'].includes(state)?'Waiting for the scheduled retraining. Baselines continue; earlier candidate errors remain visible.':'Training and initial evaluation complete. Later real targets continue to be monitored.',maximum:0,value:0};
    });
  }
}
