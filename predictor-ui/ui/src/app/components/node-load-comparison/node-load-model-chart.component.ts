import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, ElementRef, EventEmitter, Input, NgZone, OnChanges, OnDestroy, Output, ViewChild } from '@angular/core';
import Chart from 'chart.js/auto';
import annotationPlugin from 'chartjs-plugin-annotation';
import { buildLoadTimelineChart, LoadTimeline, LoadTimelineTrace } from '../../shared/node-load-timeline';
import { LoadCanvasPoint, LoadChartFilters } from '../../shared/node-load-chart';

Chart.register(annotationPlugin);

@Component({
  selector:'app-node-load-model-chart',standalone:true,imports:[CommonModule],
  templateUrl:'./node-load-model-chart.component.html',styleUrls:['./node-load-model-chart.component.scss']
})
export class NodeLoadModelChartComponent implements AfterViewInit,OnChanges,OnDestroy {
  @Input() view!:LoadTimeline;
  @Input() trace!:LoadTimelineTrace;
  @Input() nodeId='';
  @Input() expanded=false;
  @Input() filters:LoadChartFilters={actual:true,test:true,future:true,commonY:true};
  @Output() expand=new EventEmitter<string>();
  @ViewChild('canvas') canvas?:ElementRef<HTMLCanvasElement>;
  private chart?:Chart<'line',LoadCanvasPoint[]>;
  constructor(private zone:NgZone) {}

  ngAfterViewInit():void {this.draw();}
  ngOnChanges():void {if(this.canvas) this.draw();}
  ngOnDestroy():void {this.zone.runOutsideAngular(()=>{this.chart?.destroy();this.chart=undefined;});}

  private draw():void {
    // Chart.js watches document subtree changes for canvas attachment. Its
    // observers and pointer/resize handlers must not start Angular checks.
    // Inputs and the Expand button still update through Angular normally.
    this.zone.runOutsideAngular(()=>this.drawCanvas());
  }

  private drawCanvas():void {
    if(!this.canvas || !this.view || !this.trace) return;
    const v=this.view,plot=buildLoadTimelineChart(v,this.trace,this.filters.commonY);
    const datasets=plot.series.map(s=>({label:s.label,data:s.points,borderColor:s.color,backgroundColor:s.color,
      hidden:!this.filters[s.key],borderWidth:1.5,
      borderDash:s.key==='actual'?[]:s.key==='test'?[7,4]:[3,3],
      order:s.key==='actual'?2:s.key==='test'?1:0,fill:false,tension:0,spanGaps:false,
      pointRadius:(context:{raw:unknown})=>(context.raw as LoadCanvasPoint)?.isolated ? 2 : 0,
      pointHoverRadius:3,pointHitRadius:7}));
    const now=v.time/60000;
    const annotations:Record<string,any>={replay:{type:'line',xMin:now,xMax:now,
      borderColor:'#ffd174',borderWidth:1,borderDash:[3,5],adjustScaleRange:false,
      label:{display:true,content:'Now',position:'end',color:'#ffd174',backgroundColor:'#26262d',font:{size:10}}}};
    if(!v.final) annotations['futureArea']={type:'box',xMin:now,xMax:plot.maxX,
      backgroundColor:'#ef5b7d0c',borderWidth:0,drawTime:'beforeDatasetsDraw',adjustScaleRange:false};
    const options:any={responsive:true,maintainAspectRatio:false,animation:false,normalized:false,
      interaction:{mode:'nearest',intersect:false},
      plugins:{legend:{display:false},annotation:{annotations},tooltip:{
        callbacks:{title:(items:{raw:unknown}[])=>{
          const p=items[0]?.raw as LoadCanvasPoint|undefined;
          return p ? `Simulation time ${(p.time/60000).toFixed(2)} min` : '';
        },label:(item:{dataset:{label?:string};raw:unknown})=>{
          const p=item.raw as LoadCanvasPoint;
          const result=[`${item.dataset.label}: ${p.y==null ? '—' : p.y.toLocaleString('en-US',{maximumFractionDigits:4})} bytes`];
          if(p.origin!==undefined) result.push(`Issued ${this.minutes(p.origin)} min · ${p.version} · lead ${p.lead} step(s)`);
          return result;
        }}
      }},
      scales:{
        x:{type:'linear',min:plot.minX,max:plot.maxX,grid:{color:'#ffffff08'},ticks:{color:'#a8abb8',maxTicksLimit:8,font:{size:10},
          callback:(value:number|string)=>Number(value).toLocaleString('en-US',{maximumFractionDigits:2})},
          title:{display:true,text:'Simulation time (min)',color:'#a8abb8',font:{size:10}}},
        y:{type:'linear',min:plot.minY,max:plot.maxY,grid:{color:'#ffffff0d'},ticks:{color:'#a8abb8',maxTicksLimit:4,font:{size:10}}}
      }};
    if(this.chart) {this.chart.data.datasets=datasets;this.chart.options=options;this.chart.update('none');}
    else this.chart=new Chart(this.canvas.nativeElement,{type:'line',data:{datasets},options});
  }

  metric(value:number|null|undefined):string {return value==null ? '—' : value.toLocaleString('en-US',{maximumFractionDigits:2});}
  get progressTitle():string {
    const p=this.trace.progress;
    if(!p) return '';
    if(p.phase==='retraining-data') return this.view.final?'Run ended · retraining pending':p.value===p.total?'Data ready · awaiting retraining':'Next training · data';
    return p.title+(p.version?' · '+p.version:'');
  }
  get progressCount():string {
    const p=this.trace.progress;
    if(!p) return '';
    if(p.phase==='training') return this.view.final?'No completion recorded':'Training in progress';
    return `${p.value} / ${p.total} ${p.phase==='evaluating'?'targets':p.phase==='retraining-data'?'new samples':'samples'}`
      +(p.phase==='retraining-data' && p.value!==null && p.value<p.total?` · ${p.total-p.value} remaining`:'');
  }
  get progressNote():string {
    const p=this.trace.progress;
    if(!p) return '';
    if(p.phase==='retraining-data') return this.view.final?'No further training recorded.':p.value===p.total?'Waiting for a training event.':`Earliest ${this.minutes(p.eligibleAtMs!)} simulation min.`;
    if(p.phase==='evaluating') return this.view.final && p.value!==null && p.value<p.total?'Run ended before evaluation completed.':p.value===p.total?'Awaiting qualification decision.':'';
    return '';
  }
  minutes(time:number):string {return (time/60000).toFixed(2);}
}
