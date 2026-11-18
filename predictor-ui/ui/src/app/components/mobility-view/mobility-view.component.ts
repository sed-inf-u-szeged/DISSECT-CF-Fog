import { Component, ElementRef, Input, OnChanges, OnDestroy, ViewChild } from '@angular/core';
import { MobilityRun, MobilityLoad, MobilityNode, MobilityMethod, PositionPrediction, MobilityFrame, MobilityLearningRun, PositionEvaluation } from '../../shared/MobilityRun';
import { MapTrack, orderedMapNodes, lastKnownIndex, NODE_COLOR } from '../../shared/mobility-map';
import { lifecycleAt, positionMetrics, recentMovement, stateLabel } from '../../shared/mobility-display';

type LoadMetric = 'running_tasks' | 'waiting_assignment_bytes';

@Component({
  selector: 'app-mobility-view',
  templateUrl: './mobility-view.component.html',
  styleUrls: ['./mobility-view.component.scss', './mobility-view-layout.scss', './mobility-view-axes.scss']
})
export class MobilityViewComponent implements OnChanges, OnDestroy {
  private sourceRun!: MobilityRun;
  private activeRun!: MobilityRun;
  deviceId = 0;
  selectedNodeId = '';
  mapTracks:MapTrack[]=[];
  get mapNodes(){return this.sourceRun.nodes;}
  @Input() set run(value: MobilityRun) {
    this.sourceRun = value;
    this.deviceId = value.devices?.[0]?.id ?? value.observations[0]?.device_id ?? 0;
    this.activeRun = value.devices?.[0]?.data ?? value;
    this.mapTracks=value.devices?.length ? value.devices.map(d=>({id:d.id,observations:d.data.observations})) : [{id:this.deviceId,observations:value.observations}];
    this.loadFrames = undefined;
    this.errorCache = undefined;
  }
  get run(): MobilityRun { return this.activeRun; }
  get devices() { return this.sourceRun?.devices ?? []; }
  get nodeLoad() { return this.sourceRun?.nodeLoad; }
  seekLoadTime(time: number): void {
    const i=this.run.frames.findIndex(f=>f.time_ms>=time);
    this.choose(i<0 ? this.run.frames.length-1 : i);
  }
  selectDevice(id: number): void {
    const device = this.devices.find(d => d.id === id);
    if (!device) return;
    if (id === this.deviceId) {
      if (this.hasObservation) this.selectNode(this.observation.current_node);
      return;
    }
    this.pause();
    this.deviceId = id;
    this.activeRun = device.data;
    this.errorVersion = '';
    this.model = this.run.learning ? 'selected' : 'velocity';
    if (this.hasObservation) this.selectNode(this.observation.current_node);
    if (this.versionDetails) this.versionDetails.nativeElement.open = false;
  }
  @ViewChild('content') content?: ElementRef<HTMLElement>;
  @ViewChild('mobilitySection') mobilitySection?: ElementRef<HTMLElement>;
  @ViewChild('positionErrors') positionErrors?: ElementRef<HTMLElement>;
  @ViewChild('observedNodes') observedNodes?: ElementRef<HTMLElement>;
  @ViewChild('nodeLoadSection', {read:ElementRef}) nodeLoadSection?: ElementRef<HTMLElement>;
  @ViewChild('versionDetails') versionDetails?: ElementRef<HTMLDetailsElement>;
  showNodeLoad(): void { this.nodeLoadSection?.nativeElement.scrollIntoView({behavior:'smooth',block:'start'}); }
  showSection(section:'mobility'|'errors'|'nodes'):void {
    const target=section==='mobility'?this.mobilitySection:section==='errors'?this.positionErrors:this.observedNodes;
    target?.nativeElement.scrollIntoView({behavior:'smooth',block:'start'});
  }
  index = 0;
  model: string = 'velocity';
  errorVersion = '';
  errorWindowMinutes = 0;
  get methods(): MobilityMethod[] { return ['HoldPosition', 'ConstantVelocity', 'LSTM', ...(this.run.comparison?.methods ?? [])]; }
  readonly methodColors: Record<MobilityMethod, string> = { HoldPosition: '#ffd174', ConstantVelocity: '#61b8ac', LSTM: '#ff7395', 'IMM/Kalman': '#8ac6ff', XGBoost: '#c99aff', TCN: '#ff9b65' };
  readonly stateLabel = stateLabel;
  showActual = true;
  showFuture = true;
  showTruth = false;
  pathWindowMinutes = 10;
  timer?: ReturnType<typeof setInterval>;
  loadMetric: LoadMetric = 'waiting_assignment_bytes';
  loadWindowMinutes = 0;
  loadNodeFilter = '';
  hiddenMethods = new Set<MobilityMethod>();
  get orderedNodes() { return orderedMapNodes(this.sourceRun.nodes); }
  nodeColor(_name: string): string { return NODE_COLOR; }
  cityName(name: string): string { return name.replace(/_(fog[12]|cloud)$/, ''); }
  nodeRole(node: MobilityNode): string { return node.role === 'cloud' || node.name.endsWith('_cloud') ? 'Cloud' : 'Fog'; }
  selectNode(name:string):void {
    if(!this.sourceRun.nodes.some(n=>n.name===name))return;
    this.selectedNodeId=name;
    this.loadNodeFilter=name;
  }
  get visibleLoadNodes() { return this.orderedNodes.filter(n=>!this.loadNodeFilter || n.name===this.loadNodeFilter); }
  selectObservedNode(name: string): void {
    if (name) this.selectNode(name);
    else this.loadNodeFilter = '';
  }
  currentLoad(name: string) { return this.frame.nodes.find(n=>n.node===name); }
  get visibleMethods() { return this.methods.filter(m=>!this.hiddenMethods.has(m)); }
  toggleErrorMethod(method: MobilityMethod): void {
    if (this.hiddenMethods.has(method)) this.hiddenMethods.delete(method);
    else if (this.visibleMethods.length > 1) this.hiddenMethods.add(method);
  }
  readonly ticks = [0, 1, 2, 3];
  readonly metrics: { label: string; key: keyof MobilityLoad }[] = [
    { label: 'Device uploads (bytes)', key: 'device_upload_completed_bytes_derived' },
    { label: 'Offload sent (bytes)', key: 'offload_sent_bytes' },
    { label: 'Offload received (bytes)', key: 'offload_received_bytes' },
    { label: 'Queued data (bytes)', key: 'waiting_assignment_bytes' },
    { label: 'Running tasks', key: 'running_tasks' },
    { label: 'Completed tasks', key: 'completed_tasks' }
  ];
  // Completed run data is immutable. Keep only the currently rendered paths;
  // the shared node-load cache survives a device switch at the same frame.
  private loadFrames?: MobilityFrame[];
  private loadMaxima: Record<LoadMetric, number> = { running_tasks: 3, waiting_assignment_bytes: 300 };
  private loadPathIndex = -1;
  private loadWindow = -1;
  private visibleLoadFrames: MobilityFrame[] = [];
  private loadPaths = new Map<string, string>();
  private errorCache?: {
    run: MobilityRun; learning?: MobilityLearningRun; frames: MobilityFrame[]; time: number; version: string; visible: string; window: number;
    start: number; end: number; hasHistory: boolean;
    rows: PositionEvaluation[]; comparisonRows: PositionEvaluation[]; maximum: number; paths: Map<MobilityMethod, string>;
    metrics: ({model: MobilityMethod} & ReturnType<typeof positionMetrics>)[];
  };

  ngOnChanges(): void {
    this.pause();
    this.model = this.run.learning ? 'selected' : 'velocity';
    this.errorVersion = '';
    this.errorWindowMinutes = 0;
    this.showTruth = false;
    this.pathWindowMinutes = 10;
    this.hiddenMethods.clear();
    this.loadWindowMinutes = this.run.nodes.length > 2 ? 30 : 0;
    this.loadNodeFilter = '';
    const first = this.run.baselines?.predictions[0]?.origin_ms ?? this.run.learning?.predictions[0]?.origin_ms ?? this.run.predictions[0]?.origin_ms;
    this.index = Math.max(0, this.run.frames.findIndex(f => f.time_ms === first));
    this.selectedNodeId=this.sourceRun.nodes.some(n=>n.name===this.observation.current_node) ? this.observation.current_node : this.orderedNodes[0]?.name ?? '';
    this.loadNodeFilter = this.selectedNodeId;
    this.content?.nativeElement.scrollTo({top: 0});
    if (this.versionDetails) this.versionDetails.nativeElement.open = false;
  }
  ngOnDestroy(): void { this.pause(); }
  get frame() { return this.run.frames[this.index]; }
  get hasObservation() { return lastKnownIndex(this.run.observations,this.frame.time_ms)>=0; }
  get observation() { return this.run.observations[lastKnownIndex(this.run.observations,this.frame.time_ms)]
    ?? {device_id:this.deviceId, time_ms:this.frame.time_ms, latitude:+(this.run.props['device_start_latitude_deg']??this.run.props['start_latitude_deg']),
      longitude:+(this.run.props['device_start_longitude_deg']??this.run.props['start_longitude_deg']), previous_node:'NONE', current_node:'NONE'}; }
  get forecast() { return this.final ? undefined : this.run.predictions.find(p => p.origin_ms <= this.frame.time_ms && p.target_ms > this.frame.time_ms); }
  get evaluation() { return this.run.evaluations.find(e => e.origin_ms === this.forecast?.origin_ms); }
  get matured() { return this.run.evaluations.filter(e => e.target_ms <= this.frame.time_ms).slice(-1)[0]; }
  get final() { return this.frame.sample_kind === 'FINAL'; }
  get shownForecast(): PositionPrediction | undefined {
    if (this.final) return undefined;
    const learning = this.run.learning;
    if (this.run.comparison?.methods.some(m => m === this.model)) return this.run.comparison.predictions.find(p => p.origin_ms <= this.frame.time_ms && p.target_ms > this.frame.time_ms && p.model === this.model);
    const early = this.run.baselines?.predictions.find(p=>p.origin_ms<=this.frame.time_ms && p.target_ms>this.frame.time_ms
      && p.model===(this.model==='selected'?'HoldPosition':this.method));
    if (this.model==='hold' || this.model==='velocity') { if(early) return early; }
    if (learning) return this.model === 'selected'
      ? learning.selectedPredictions.find(p => p.origin_ms <= this.frame.time_ms && p.target_ms > this.frame.time_ms)
        ?? (this.frame.time_ms < (learning.selectedPredictions[0]?.origin_ms ?? Infinity) ? early : undefined)
      : learning.predictions.find(p => p.origin_ms <= this.frame.time_ms && p.target_ms > this.frame.time_ms && p.model === this.method);
    const p = this.forecast;
    return p ? { model: this.method, model_version: '', device_id: this.observation.device_id,
      origin_ms: p.origin_ms, target_ms: p.target_ms, origin_node: p.origin_node,
      predicted_latitude_deg: this.model === 'hold' ? p.hold_latitude : p.velocity_latitude,
      predicted_longitude_deg: this.model === 'hold' ? p.hold_longitude : p.velocity_longitude } : undefined;
  }
  get method(): MobilityMethod { return this.model === 'hold' ? 'HoldPosition' : this.model === 'lstm' ? 'LSTM' : this.run.comparison?.methods.find(m => m === this.model) ?? 'ConstantVelocity'; }
  get targetObservation() { return this.run.observations.find(o => o.time_ms === this.shownForecast?.target_ms); }
  get originCoverage() { return this.run.nodes.find(n => n.name === this.shownForecast?.origin_node); }
  get lifecycle() { return this.run.learning ? lifecycleAt(this.run.learning, this.frame.time_ms) : undefined; }
  get strategyText() {
    const p = this.run.props;
    return p['mobility_strategy'] === 'random_walk'
      ? `RandomWalk · ${p['speed_min_m_s']}–${p['speed_max_m_s']} m/s · radius ${p['radius_m']} m`
      : `Nomadic · ${p['speed_m_s']} m/s · repeat ${p['route_repeats'] ?? 'not recorded'}`;
  }
  get latestPositionEvaluation() {
    const data = this.run.learning;
    if (this.run.comparison?.methods.some(m => m === this.model)) return this.run.comparison.evaluations.filter(e => e.model === this.model && e.target_ms <= this.frame.time_ms).at(-1);
    const recorded=(this.model === 'selected' ? data?.selectedEvaluations : data?.evaluations.filter(e => e.model === this.method))
      ?.filter(e => e.target_ms <= this.frame.time_ms).at(-1);
    const baseline=this.run.baselines?.evaluations.filter(e=>e.target_ms<=this.frame.time_ms
      && e.model===(this.model==='selected'?'HoldPosition':this.method)).at(-1);
    return this.model==='hold'||this.model==='velocity' ? baseline??recorded : recorded??baseline;
  }
  private get errorChart() {
    const {learning, frames} = this.run, time = this.frame.time_ms, version = this.errorVersion, visible=this.visibleMethods.join(','), window=this.errorWindowMinutes;
    if (this.errorCache && this.errorCache.run === this.run && this.errorCache.learning === learning && this.errorCache.frames === frames
      && this.errorCache.time === time && this.errorCache.version === version && this.errorCache.visible === visible && this.errorCache.window === window) return this.errorCache;
    const comparisonRows = [...(learning?.evaluations ?? []), ...(this.run.comparison?.evaluations ?? [])].filter(e => e.target_ms <= time
      && (!version || e.model_version === version));
    const targets=new Set(comparisonRows.map(e=>e.origin_ms+'|'+e.target_ms));
    const history=(this.run.baselines ? [...this.run.baselines.evaluations.filter(e=>e.target_ms<=time
      && (!version || targets.has(e.origin_ms+'|'+e.target_ms))),
      ...comparisonRows.filter(e=>!['HoldPosition','ConstantVelocity'].includes(e.model))] : comparisonRows).sort((a,b)=>a.target_ms-b.target_ms);
    const start=window ? Math.max(frames[0].time_ms,time-window*60000)
      : Math.min(history[0]?.target_ms??time,time);
    const rows=window ? history.filter(e=>e.target_ms>=start) : history;
    let largest = 0;
    for (const row of rows) if (!this.hiddenMethods.has(row.model)) largest = Math.max(largest, row.position_error_m);
    const unit = largest ? 10 ** Math.floor(Math.log10(largest)) : 1;
    this.errorCache = {run:this.run, learning, frames, time, version, visible, window, start,
      end:Math.max(start+60000,time),hasHistory:history.length>0,rows,comparisonRows,
      maximum: largest ? Math.ceil(largest / unit) * unit : 1,
      paths: new Map<MobilityMethod, string>(),
      metrics: this.methods.map(model => ({model, ...positionMetrics(comparisonRows.filter(r => r.model === model))}))};
    return this.errorCache;
  }
  get errorRows() { return this.errorChart.rows; }
  get hasErrorHistory() { return this.errorChart.hasHistory; }
  get comparisonMetrics() { return this.errorChart.metrics; }
  comparisonTime(model: MobilityMethod): number | null {
    const rows = this.errorChart.comparisonRows.filter(r => r.model === model && r.predict_wall_ms !== undefined);
    return rows.length ? rows.reduce((s,r) => s + r.predict_wall_ms!, 0) / rows.length : null;
  }
  get selectedMetrics() { return positionMetrics(this.run.learning?.selectedEvaluations.filter(e => e.target_ms <= this.frame.time_ms) ?? []); }
  get errorMaximum() { return this.errorChart.maximum; }
  get errorTicks(): number[] {
    const maximum = this.errorMaximum, raw = maximum / 4;
    const unit = 10 ** Math.floor(Math.log10(raw));
    const step = ([1, 2, 2.5, 5, 10].find(n => n * unit >= raw) ?? 10) * unit;
    const values = Array.from({length: Math.floor(maximum / step) + 1}, (_, i) => i * step);
    if (maximum - values[values.length - 1] > step * .01) values.push(maximum);
    return values;
  }
  errorTick(value: number) { return value.toLocaleString('en-US', {maximumFractionDigits: 6}); }
  errorTime(i: number) {
    const {start,end}=this.errorChart;
    return start+(end-start)*i/3;
  }
  errorX(time: number) { return 60+800*(time-this.errorTime(0))/(this.errorTime(3)-this.errorTime(0)); }
  errorY(value: number) { return 180 - value / this.errorMaximum * 150; }
  errorPath(model: MobilityMethod) {
    const chart = this.errorChart;
    const cached = chart.paths.get(model);
    if (cached !== undefined) return cached;
    const rows = chart.rows.filter(e => e.model === model);
    const path = rows.map((row, i) => `${i && rows[i - 1].model_version === row.model_version ? 'L' : 'M'}${this.errorX(row.target_ms)},${this.errorY(row.position_error_m)}`).join(' ');
    chart.paths.set(model, path);
    return path;
  }
  get errorEndpoints() { return this.visibleMethods.flatMap(model=>{
    const row=this.errorRows.filter(e=>e.model===model).at(-1);
    return row ? [{model,x:(this.errorX(row.target_ms)-60)/8,y:row.position_error_m/this.errorMaximum*100,
      title:`${model}: ${row.position_error_m.toFixed(2)} m at ${(row.target_ms/60000).toFixed(2)} min`}] : [];
  }); }
  jumpToVersion(time: number): void { this.choose(this.run.frames.findIndex(f => f.sample_kind === 'OBSERVATION' && f.time_ms === time)); }
  get pathObservations() {return recentMovement(this.run.observations,this.frame.time_ms,this.pathWindowMinutes);}
  get predictionAtObservation():boolean {
    const p=this.shownForecast,o=this.observation;
    return !!p && Math.abs(p.predicted_latitude_deg-o.latitude)<1e-9 && Math.abs(p.predicted_longitude_deg-o.longitude)<1e-9;
  }
  get distance() {
    const p = this.shownForecast, node = this.originCoverage; if (!p || !node) return 0;
    const a = p.predicted_latitude_deg * Math.PI / 180;
    const b = p.predicted_longitude_deg * Math.PI / 180;
    const c = node.latitude * Math.PI / 180, d = node.longitude * Math.PI / 180;
    return 12756274 * Math.asin(Math.min(1, Math.sqrt(Math.sin((a - c) / 2) ** 2 + Math.cos(a) * Math.cos(c) * Math.sin((b - d) / 2) ** 2)));
  }
  get exitWarning() { return !!this.shownForecast && !!this.originCoverage && this.distance > this.originCoverage.radius; }
  get connectionEvents() { return this.run.observations.filter(o => o.time_ms <= this.frame.time_ms && o.current_node !== o.previous_node).slice(-3).reverse(); }
  get transferEvents() { return this.run.transfers.filter(t => t.event_sequence <= this.frame.nodes[0].offload_event_sequence).slice(-3).reverse(); }
  get finalCompleted() { return this.run.frames.slice(-1)[0].nodes.reduce((n, row) => n + row.completed_tasks, 0); }
  name(n: string) { return n === 'NONE' ? 'Disconnected' : n; }
  minutes(t: number) { return (t / 60000).toFixed(2); }
  correctMatured() { const e = this.matured; return !!e && (this.model === 'velocity' ? e.velocity_exit : e.hold_exit) === e.actual_exit; }
  number(v: number | string) { return typeof v === 'number' ? v.toLocaleString('en-US') : v; }
  choose(i: number): void { this.pause(); this.index = Math.max(0, Math.min(this.run.frames.length - 1, i)); }
  seek(event: Event): void { this.choose(Number((event.target as HTMLInputElement).value)); }
  nextConnection(): void {
    const o = this.run.observations.find(o => o.time_ms > this.frame.time_ms && o.current_node !== o.previous_node);
    this.choose(o ? this.run.frames.findIndex(f => f.sample_kind === 'OBSERVATION' && f.time_ms === o.time_ms) : this.run.frames.length - 1);
  }
  play(): void {
    if (this.timer) { this.pause(); return; }
    if (this.index === this.run.frames.length - 1) this.index = 0;
    this.timer = setInterval(() => { this.index++; if (this.index >= this.run.frames.length - 1) this.pause(); }, 650);
  }
  pause(): void { if (this.timer) clearInterval(this.timer); this.timer = undefined; }
  chartX(t: number) { const frames = this.run.frames, a = frames[0].time_ms, b = frames[frames.length - 1].time_ms; return 60 + 800 * (t - a) / (b - a); }
  chartTime(i: number) { const frames = this.run.frames, a = frames[0].time_ms, b = frames[frames.length - 1].time_ms; return a + (b - a) * i / 3; }
  loadTime(i: number): number {
    const first=this.run.frames[0].time_ms;
    const end=this.loadWindowMinutes ? Math.max(first+1,this.frame.time_ms) : this.run.frames.at(-1)!.time_ms;
    const start=this.loadWindowMinutes ? Math.max(first,end-this.loadWindowMinutes*60000) : first;
    return start+(end-start)*i/3;
  }
  loadX(t: number) { return 50+370*(t-this.loadTime(0))/Math.max(1,this.loadTime(3)-this.loadTime(0)); }
  private ensureLoadScales(): void {
    const frames = this.run.frames;
    if (this.loadFrames === frames && this.loadPathIndex===this.index && this.loadWindow===this.loadWindowMinutes) return;
    const known=frames.slice(0,this.index+1).filter(f=>f.sample_kind==='OBSERVATION');
    const first=known.findIndex(f=>f.time_ms>=this.loadTime(0));
    // Carry a step across the left edge only if an observation exists in the window.
    // Never extend the last observation through the unsampled final drain interval.
    this.visibleLoadFrames=first<0 ? [] : known.slice(Math.max(0,first-1));
    let tasks=1, waiting=1;
    for (const frame of this.visibleLoadFrames) for (const node of frame.nodes) {
      tasks=Math.max(tasks,node.running_tasks);
      waiting=Math.max(waiting,node.waiting_assignment_bytes);
    }
    this.loadMaxima={running_tasks:Math.ceil(tasks/3)*3, waiting_assignment_bytes:Math.ceil(waiting/300)*300};
    this.loadFrames=frames; this.loadPathIndex=this.index; this.loadWindow=this.loadWindowMinutes;
    this.loadPaths.clear();
  }
  maximum(key: LoadMetric) { this.ensureLoadScales(); return this.loadMaxima[key]; }
  chartY(v: number, key: LoadMetric) { return 140-v/this.maximum(key)*110; }
  chartPath(node: string, key: LoadMetric) {
    this.ensureLoadScales();
    const cacheKey=JSON.stringify([node,key]), cached=this.loadPaths.get(cacheKey);
    if (cached!==undefined) return cached;
    let path='', connected=false;
    for (const frame of this.visibleLoadFrames) {
      const value=frame.nodes.find(n=>n.node===node)?.[key];
      if (value===undefined || !Number.isFinite(value)) { connected=false; continue; }
      const x=this.loadX(Math.max(frame.time_ms,this.loadTime(0))), y=this.chartY(value,key);
      path+=connected ? `H${x}V${y}` : `M${x},${y}`; connected=true;
    }
    this.loadPaths.set(cacheKey,path);
    return path;
  }
}
