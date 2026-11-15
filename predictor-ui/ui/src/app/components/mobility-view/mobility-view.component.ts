import { Component, ElementRef, Input, OnChanges, OnDestroy, ViewChild } from '@angular/core';
import { MobilityRun, MobilityLoad, MobilityNode, MobilityMethod, PositionPrediction, MobilityFrame, MobilityLearningRun, PositionEvaluation } from '../../shared/MobilityRun';
import { lifecycleAt, positionMetrics, stateLabel } from '../../shared/mobility-display';

type LoadMetric = 'running_tasks' | 'waiting_assignment_bytes';

@Component({
  selector: 'app-mobility-view',
  templateUrl: './mobility-view.component.html',
  styleUrls: ['./mobility-view.component.scss']
})
export class MobilityViewComponent implements OnChanges, OnDestroy {
  private sourceRun!: MobilityRun;
  private activeRun!: MobilityRun;
  deviceId = 0;
  @Input() set run(value: MobilityRun) {
    this.sourceRun = value;
    this.deviceId = value.devices?.[0]?.id ?? value.observations[0]?.device_id ?? 0;
    this.activeRun = value.devices?.[0]?.data ?? value;
    this.loadFrames = undefined;
    this.errorCache = undefined;
  }
  get run(): MobilityRun { return this.activeRun; }
  get devices() { return this.sourceRun?.devices ?? []; }
  selectDevice(id: number): void {
    const device = this.devices.find(d => d.id === id);
    if (!device) return;
    this.pause();
    this.deviceId = id;
    this.activeRun = device.data;
    this.errorVersion = '';
    this.showTruth = false;
    this.model = this.run.learning ? 'selected' : 'velocity';
    this.updateProjection();
    if (this.versionDetails) this.versionDetails.nativeElement.open = false;
  }
  @ViewChild('content') content?: ElementRef<HTMLElement>;
  @ViewChild('versionDetails') versionDetails?: ElementRef<HTMLDetailsElement>;
  index = 0;
  model: string = 'velocity';
  errorVersion = '';
  get methods(): MobilityMethod[] { return ['HoldPosition', 'ConstantVelocity', 'LSTM', ...(this.run.comparison?.methods ?? [])]; }
  readonly methodColors: Record<MobilityMethod, string> = { HoldPosition: '#ffd174', ConstantVelocity: '#61b8ac', LSTM: '#ff7395', 'IMM/Kalman': '#8ac6ff', XGBoost: '#c99aff', TCN: '#ff9b65' };
  readonly stateLabel = stateLabel;
  showActual = true;
  showFuture = true;
  showTruth = false;
  timer?: ReturnType<typeof setInterval>;
  readonly colors = ['#7774ff', '#61b8ac'];
  readonly ticks = [0, 1, 2, 3];
  readonly metrics: { label: string; key: keyof MobilityLoad }[] = [
    { label: 'Device uploads / B', key: 'device_upload_completed_bytes_derived' },
    { label: 'Offload sent / B', key: 'offload_sent_bytes' },
    { label: 'Offload received / B', key: 'offload_received_bytes' },
    { label: 'Waiting data / B', key: 'waiting_assignment_bytes' },
    { label: 'Running tasks', key: 'running_tasks' },
    { label: 'Completed tasks', key: 'completed_tasks' }
  ];
  rings: { points: string; x: number; y: number; node: MobilityNode }[] = [];
  private extent = [0, 1, 0, 1];
  // Completed run data is immutable. Keep only the currently rendered paths;
  // the shared node-load cache survives a device switch at the same frame.
  private loadFrames?: MobilityFrame[];
  private loadMaxima: Record<LoadMetric, number> = { running_tasks: 3, waiting_assignment_bytes: 300 };
  private loadPathIndex = -1;
  private loadPaths = new Map<string, string>();
  private errorCache?: {
    learning?: MobilityLearningRun; frames: MobilityFrame[]; time: number; version: string;
    rows: PositionEvaluation[]; maximum: number; paths: Map<MobilityMethod, string>;
    metrics: ({model: MobilityMethod} & ReturnType<typeof positionMetrics>)[];
  };

  ngOnChanges(): void {
    this.pause();
    this.model = this.run.learning ? 'selected' : 'velocity';
    this.errorVersion = '';
    this.showTruth = false;
    const first = this.run.learning?.predictions[0]?.origin_ms ?? this.run.predictions[0]?.origin_ms;
    this.index = Math.max(0, this.run.frames.findIndex(f => f.time_ms === first));
    this.updateProjection();
    this.content?.nativeElement.scrollTo({top: 0});
    if (this.versionDetails) this.versionDetails.nativeElement.open = false;
  }
  updateProjection(): void {
    const rings = this.run.nodes.map(n => this.coverage(n));
    const points = rings.flat().concat(this.run.observations.map(o => this.local(o.latitude, o.longitude)));
    const prediction = this.shownForecast;
    if (prediction) points.push(this.local(prediction.predicted_latitude_deg, prediction.predicted_longitude_deg));
    this.extent = [Math.min(...points.map(p => p[0])) - 100, Math.max(...points.map(p => p[0])) + 100,
      Math.min(...points.map(p => p[1])) - 100, Math.max(...points.map(p => p[1])) + 100];
    this.rings = rings.map((points, i) => { const node = this.run.nodes[i], [x, y] = this.project(this.local(node.latitude, node.longitude));
      return { points: this.points(points), x, y, node }; });
  }
  ngOnDestroy(): void { this.pause(); }
  get frame() { return this.run.frames[this.index]; }
  get hasObservation() { return this.run.observations.some(o => o.time_ms <= this.frame.time_ms); }
  get observation() { return this.run.observations.filter(o => o.time_ms <= this.frame.time_ms).slice(-1)[0]
    ?? {device_id:this.deviceId, time_ms:this.frame.time_ms, latitude:+this.run.props['start_latitude_deg'],
      longitude:+this.run.props['start_longitude_deg'], previous_node:'NONE', current_node:'NONE'}; }
  get forecast() { return this.final ? undefined : this.run.predictions.find(p => p.origin_ms <= this.frame.time_ms && p.target_ms > this.frame.time_ms); }
  get evaluation() { return this.run.evaluations.find(e => e.origin_ms === this.forecast?.origin_ms); }
  get matured() { return this.run.evaluations.filter(e => e.target_ms <= this.frame.time_ms).slice(-1)[0]; }
  get final() { return this.frame.sample_kind === 'FINAL'; }
  get shownForecast(): PositionPrediction | undefined {
    if (this.final) return undefined;
    const learning = this.run.learning;
    if (this.run.comparison?.methods.some(m => m === this.model)) return this.run.comparison.predictions.find(p => p.origin_ms <= this.frame.time_ms && p.target_ms > this.frame.time_ms && p.model === this.model);
    if (learning) return this.model === 'selected'
      ? learning.selectedPredictions.find(p => p.origin_ms <= this.frame.time_ms && p.target_ms > this.frame.time_ms)
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
    return (this.model === 'selected' ? data?.selectedEvaluations : data?.evaluations.filter(e => e.model === this.method))
      ?.filter(e => e.target_ms <= this.frame.time_ms).slice(-1)[0];
  }
  private get errorChart() {
    const {learning, frames} = this.run, time = this.frame.time_ms, version = this.errorVersion;
    if (this.errorCache && this.errorCache.learning === learning && this.errorCache.frames === frames
      && this.errorCache.time === time && this.errorCache.version === version) return this.errorCache;
    const rows = [...(learning?.evaluations ?? []), ...(this.run.comparison?.evaluations ?? [])].filter(e => e.target_ms <= time
      && (!version || e.model_version === version));
    let largest = 0;
    for (const row of rows) largest = Math.max(largest, row.position_error_m);
    const unit = largest ? 10 ** Math.floor(Math.log10(largest)) : 1;
    this.errorCache = {learning, frames, time, version, rows,
      maximum: largest ? Math.ceil(largest / unit) * unit : 1,
      paths: new Map<MobilityMethod, string>(),
      metrics: this.methods.map(model => ({model, ...positionMetrics(rows.filter(r => r.model === model))}))};
    return this.errorCache;
  }
  get errorRows() { return this.errorChart.rows; }
  get comparisonMetrics() { return this.errorChart.metrics; }
  comparisonTime(model: MobilityMethod): number | null {
    const rows = this.errorChart.rows.filter(r => r.model === model && r.predict_wall_ms !== undefined);
    return rows.length ? rows.reduce((s,r) => s + r.predict_wall_ms!, 0) / rows.length : null;
  }
  get selectedMetrics() { return positionMetrics(this.run.learning?.selectedEvaluations.filter(e => e.target_ms <= this.frame.time_ms) ?? []); }
  get errorMaximum() { return this.errorChart.maximum; }
  errorTick(value: number) { return value.toFixed(Math.max(0, Math.min(6, 1 - Math.floor(Math.log10(this.errorMaximum))))); }
  errorY(value: number) { return 180 - value / this.errorMaximum * 150; }
  errorPath(model: MobilityMethod) {
    const chart = this.errorChart;
    const cached = chart.paths.get(model);
    if (cached !== undefined) return cached;
    const rows = chart.rows.filter(e => e.model === model);
    const path = rows.map((row, i) => `${i && rows[i - 1].model_version === row.model_version ? 'L' : 'M'}${this.chartX(row.target_ms)},${this.errorY(row.position_error_m)}`).join(' ');
    chart.paths.set(model, path);
    return path;
  }
  jumpToVersion(time: number): void { this.choose(this.run.frames.findIndex(f => f.sample_kind === 'OBSERVATION' && f.time_ms === time)); }
  get currentPoint() { return this.project(this.local(this.observation.latitude, this.observation.longitude)); }
  get futurePoint() {
    const p = this.shownForecast;
    return p ? this.project(this.local(p.predicted_latitude_deg, p.predicted_longitude_deg)) : this.currentPoint;
  }
  get truthPoint() { const o = this.targetObservation; return o ? this.project(this.local(o.latitude, o.longitude)) : this.currentPoint; }
  get trajectory() { return this.points(this.run.observations.filter(o => o.time_ms <= this.frame.time_ms).map(o => this.local(o.latitude, o.longitude))); }
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
  get xTicks() { const e = this.extent; return this.ticks.map(i => e[0] + (e[1] - e[0]) * i / 3); }
  get yTicks() { const e = this.extent; return this.ticks.map(i => e[2] + (e[3] - e[2]) * i / 3); }
  axisX(v: number) { return this.project([v, 0])[0]; }
  axisY(v: number) { return this.project([0, v])[1]; }
  name(n: string) { return n === 'NONE' ? 'Disconnected' : n; }
  minutes(t: number) { return (t / 60000).toFixed(2); }
  correctMatured() { const e = this.matured; return !!e && (this.model === 'velocity' ? e.velocity_exit : e.hold_exit) === e.actual_exit; }
  number(v: number | string) { return typeof v === 'number' ? v.toLocaleString('en-US') : v; }
  choose(i: number): void { this.pause(); this.index = Math.max(0, Math.min(this.run.frames.length - 1, i)); this.updateProjection(); }
  seek(event: Event): void { this.choose(Number((event.target as HTMLInputElement).value)); }
  nextConnection(): void {
    const o = this.run.observations.find(o => o.time_ms > this.frame.time_ms && o.current_node !== o.previous_node);
    this.choose(o ? this.run.frames.findIndex(f => f.sample_kind === 'OBSERVATION' && f.time_ms === o.time_ms) : this.run.frames.length - 1);
  }
  play(): void {
    if (this.timer) { this.pause(); return; }
    if (this.index === this.run.frames.length - 1) this.index = 0;
    this.timer = setInterval(() => { this.index++; this.updateProjection(); if (this.index >= this.run.frames.length - 1) this.pause(); }, 650);
  }
  pause(): void { if (this.timer) clearInterval(this.timer); this.timer = undefined; }
  private local(lat: number, lon: number): number[] {
    const n = this.run.nodes[0], r = Math.PI / 180;
    return [(lon - n.longitude) * r * 6378137 * Math.cos(n.latitude * r), (lat - n.latitude) * r * 6378137];
  }
  private coverage(n: MobilityNode): number[][] {
    const r = Math.PI / 180, a = n.latitude * r, b = n.longitude * r, q = n.radius / 6378137;
    return Array.from({length: 121}, (_, i) => { const bearing = i * Math.PI / 60;
      const lat = Math.asin(Math.sin(a) * Math.cos(q) + Math.cos(a) * Math.sin(q) * Math.cos(bearing));
      const lon = b + Math.atan2(Math.sin(bearing) * Math.sin(q) * Math.cos(a), Math.cos(q) - Math.sin(a) * Math.sin(lat));
      return this.local(lat / r, lon / r); });
  }
  private project(p: number[]): number[] {
    const [a,b,c,d] = this.extent, s = Math.min(800 / (b - a), 330 / (d - c));
    return [450 + (p[0] - (a + b) / 2) * s, 190 - (p[1] - (c + d) / 2) * s];
  }
  private points(p: number[][]) { return p.map(p => this.project(p).join(',')).join(' '); }
  chartX(t: number) { const frames = this.run.frames, a = frames[0].time_ms, b = frames[frames.length - 1].time_ms; return 60 + 800 * (t - a) / (b - a); }
  chartTime(i: number) { const frames = this.run.frames, a = frames[0].time_ms, b = frames[frames.length - 1].time_ms; return a + (b - a) * i / 3; }
  private ensureLoadScales(): void {
    const frames = this.run.frames;
    if (this.loadFrames === frames) return;
    let tasks = 1, waiting = 1;
    // One traversal per loaded frame array, never once per plotted point.
    for (const frame of frames) for (const node of frame.nodes) {
      tasks = Math.max(tasks, node.running_tasks);
      waiting = Math.max(waiting, node.waiting_assignment_bytes);
    }
    this.loadMaxima = {running_tasks: Math.ceil(tasks / 3) * 3,
      waiting_assignment_bytes: Math.ceil(waiting / 300) * 300};
    this.loadFrames = frames;
    this.loadPathIndex = -1;
    this.loadPaths.clear();
  }
  maximum(key: LoadMetric) { this.ensureLoadScales(); return this.loadMaxima[key]; }
  chartY(v: number, key: LoadMetric) { return 180 - v / this.maximum(key) * 150; }
  chartPath(node: string, key: LoadMetric) {
    this.ensureLoadScales();
    if (this.loadPathIndex !== this.index) {
      this.loadPathIndex = this.index;
      this.loadPaths.clear();
    }
    const cacheKey = JSON.stringify([node, key]), cached = this.loadPaths.get(cacheKey);
    if (cached !== undefined) return cached;
    const path = this.run.frames.slice(0, this.index + 1).filter(f => f.sample_kind === 'OBSERVATION').map((f,i) => {
      const value = f.nodes.find(n => n.node === node)![key];
      return i ? `H${this.chartX(f.time_ms)}V${this.chartY(value,key)}` : `M${this.chartX(f.time_ms)},${this.chartY(value,key)}`;
    }).join('');
    this.loadPaths.set(cacheKey, path);
    return path;
  }
}
