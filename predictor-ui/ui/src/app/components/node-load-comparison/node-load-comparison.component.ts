import { CommonModule } from '@angular/common';
import { Component, ElementRef, EventEmitter, Input, OnChanges, Output, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LoadNode, NodeLoadRun } from '../../shared/NodeLoadRun';
import { buildLoadTimeline, LoadTimeline, LoadTimelineTrace } from '../../shared/node-load-timeline';
import { LoadChartFilters } from '../../shared/node-load-chart';
import { NodeLoadModelChartComponent } from './node-load-model-chart.component';
import { AlignedLoadScores, buildAlignedLoadScores } from '../../shared/node-load-aligned';

@Component({
  selector:'app-node-load-comparison',standalone:true,imports:[CommonModule,FormsModule,NodeLoadModelChartComponent],
  templateUrl:'./node-load-comparison.component.html',styleUrls:['./node-load-comparison.component.scss']
})
export class NodeLoadComparisonComponent implements OnChanges {
  @Input() data!:NodeLoadRun;
  @Input() node!:LoadNode;
  @Input() time=0;
  @Input() final=false;
  @Output() seekTime=new EventEmitter<number>();
  @ViewChild('detailDialog') detailDialog?:ElementRef<HTMLDialogElement>;
  hidden=new Set<string>();
  filters:LoadChartFilters={actual:true,test:true,future:true,commonY:true};
  detailModel='';
  comparisonOrigin=-1;
  private oldNode?:LoadNode;
  private oldData?:NodeLoadRun;
  private cache?:{key:string;value:LoadTimeline};
  private scoreCache?:{key:string;value:AlignedLoadScores};

  ngOnChanges():void {
    if(this.oldData!==this.data || this.oldNode!==this.node) {
      // Live batches replace the data container; preserve model visibility and filters.
      if(this.oldData?.run_id!==this.data.run_id || this.oldNode?.node_id!==this.node.node_id) {
        this.hidden=new Set();this.closeDetail();this.comparisonOrigin=-1;
      }
      this.cache=undefined;
      this.scoreCache=undefined;
      this.oldData=this.data;this.oldNode=this.node;
    }
    if(this.comparisonOrigin>this.time) this.comparisonOrigin=-1;
  }
  get alignedScores():AlignedLoadScores {
    const key=JSON.stringify([this.time,this.comparisonOrigin]);
    if(this.scoreCache?.key===key) return this.scoreCache.value;
    const value=buildAlignedLoadScores(this.data,this.node,this.time,this.comparisonOrigin);
    this.scoreCache={key,value};return value;
  }
  get view():LoadTimeline {
    const key=JSON.stringify([this.time,this.final]);
    if(this.cache?.key===key) return this.cache.value;
    const value=buildLoadTimeline(this.data,this.node,this.time,this.final);
    this.cache={key,value};return value;
  }
  // Observations belong to the node, not to a fitted/qualified model. Keep the
  // selected rows visible during warm-up, training and gaps between forecasts.
  get charts():LoadTimelineTrace[] {return this.view.traces.filter(t=>!this.hidden.has(t.model));}
  get endedBeforeTraining():boolean {return this.final && this.view.traces.some(t=>t.collecting);}
  get detailTrace():LoadTimelineTrace|undefined {return this.view.traces.find(t=>t.model===this.detailModel);}
  trackModel(_:number,t:LoadTimelineTrace):string {return t.model;}
  toggle(model:string):void {this.hidden.has(model) ? this.hidden.delete(model) : this.hidden.add(model);}
  setFilter(key:keyof LoadChartFilters,value:boolean):void {this.filters={...this.filters,[key]:value};}
  openDetail(model:string):void {
    if(!this.view.traces.some(t=>t.model===model)) return;
    this.detailModel=model;
    if(!this.detailDialog?.nativeElement.open) this.detailDialog?.nativeElement.showModal();
  }
  closeDetail():void {this.detailDialog?.nativeElement.close();this.detailModel='';}
  minutes(t:number):string {return (t/60000).toFixed(2);}
  metric(value:number|null|undefined):string {return value==null?'—':value.toLocaleString('en-US',{maximumFractionDigits:2});}
  cityName():string {return this.node.node_id.replace(/_(fog\d+|cloud)$/,'');}
  role():string {return this.node.node_id.endsWith('_cloud') ? 'Cloud' : this.node.node_id.includes('_fog') ? 'Fog' : 'Node';}
}
