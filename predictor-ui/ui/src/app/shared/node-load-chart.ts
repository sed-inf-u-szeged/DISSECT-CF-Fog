import { LoadComparison, LoadPlotPoint } from './node-load-comparison';

export type LoadTrace = LoadComparison['traces'][number];
export type LoadChartRange = 'context' | 'forecast';
export type LoadTimeAxis = 'absolute' | 'relative';
export interface LoadChartFilters { actual: boolean; test: boolean; future: boolean; commonY: boolean; }
export interface LoadCanvasPoint { x: number; y: number | null; time: number; isolated?: boolean; origin?: number; version?: string; lead?: number; }
export const LOAD_CHART_COLORS = {actual:'#e8be6d', test:'#7185ef', future:'#ef5b7d'};

// Coordinates change; saved origin/target identities and forecast values do not.
// Null separators prevent a chart library from bridging missing samples/vectors.
export function loadCanvasPoints(points: LoadPlotPoint[], anchor: number, interval: number): LoadCanvasPoint[] {
  const result: LoadCanvasPoint[]=[];
  const connected=(a:LoadPlotPoint|undefined,b:LoadPlotPoint|undefined)=>
    !!a && !!b && a.group===b.group && b.time-a.time===interval;
  points.forEach((p,i)=> {
    if(i && !connected(points[i-1],p)) result.push({x:(p.time-anchor)/60000,y:null,time:p.time});
    result.push({x:(p.time-anchor)/60000,y:p.value,time:p.time,
      ...(p.origin===undefined ? {} : {origin:p.origin,version:p.version,lead:p.lead}),
      isolated:!connected(points[i-1],p) && !connected(p,points[i+1])});
  });
  return result;
}

export function buildLoadChart(view:LoadComparison, trace:LoadTrace, commonY=true, range:LoadChartRange='context') {
  const compared=commonY ? view.traces : [trace];
  // Crop the viewport, not the stored series or scoring window. A one-step
  // forecast gets three preceding samples instead of a whole run of context.
  const start=range==='forecast' && view.origin!==undefined
    ? Math.max(view.start,view.anchor-Math.max(3,Math.min(16,view.horizon/4))*view.interval) : view.start;
  // Hidden curves keep their scale. Unobserved future actuals are never in view.
  const visible=(points:LoadPlotPoint[])=>points.filter(p=>p.time>=start && p.time<=view.end).map(p=>p.value);
  const values=[...visible(view.actual),...compared.flatMap(t=>visible([...t.testPoints,...t.futurePoints]))];
  const maximum=Math.max(1,...values),minimum=Math.min(0,...values);
  const magnitude=10**Math.floor(Math.log10(Math.max(maximum,Math.abs(minimum))));
  return {
    minX:(start-view.anchor)/60000,maxX:(view.end-view.anchor)/60000,
    minY:Math.floor(minimum/magnitude)*magnitude,maxY:Math.ceil(maximum/magnitude)*magnitude,
    series:([
      {key:'actual',label:'Actual',points:view.actual},
      {key:'test',label:'Test · earlier issued prediction',points:trace.testPoints},
      {key:'future',label:'Future · selected forecast',points:trace.futurePoints}
    ] as const).map(s=>({...s,color:LOAD_CHART_COLORS[s.key],
      points:loadCanvasPoints(s.points,view.anchor,view.interval)}))
  };
}
