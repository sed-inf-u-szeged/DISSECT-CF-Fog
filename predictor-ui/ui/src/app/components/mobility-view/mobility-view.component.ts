import { Component, Input, OnChanges, OnDestroy } from '@angular/core';
import { MobilityRun, MobilityLoad, MobilityNode } from '../../shared/MobilityRun';

@Component({
  selector: 'app-mobility-view',
  templateUrl: './mobility-view.component.html',
  styleUrls: ['./mobility-view.component.scss']
})
export class MobilityViewComponent implements OnChanges, OnDestroy {
  @Input() run!: MobilityRun;
  index = 0;
  model: 'velocity' | 'hold' = 'velocity';
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

  ngOnChanges(): void {
    this.pause();
    this.index = Math.max(0, this.run.frames.findIndex(f => f.time_ms === this.run.predictions[0]?.origin_ms));
    const rings = this.run.nodes.map(n => this.coverage(n));
    const points = rings.flat().concat(this.run.observations.map(o => this.local(o.latitude, o.longitude)));
    this.run.predictions.forEach(p => points.push(this.local(p.velocity_latitude, p.velocity_longitude), this.local(p.hold_latitude, p.hold_longitude)));
    this.extent = [Math.min(...points.map(p => p[0])) - 100, Math.max(...points.map(p => p[0])) + 100,
      Math.min(...points.map(p => p[1])) - 100, Math.max(...points.map(p => p[1])) + 100];
    this.rings = rings.map((points, i) => { const node = this.run.nodes[i], [x, y] = this.project(this.local(node.latitude, node.longitude));
      return { points: this.points(points), x, y, node }; });
  }
  ngOnDestroy(): void { this.pause(); }
  get frame() { return this.run.frames[this.index]; }
  get observation() { return this.run.observations.filter(o => o.time_ms <= this.frame.time_ms).slice(-1)[0]; }
  get forecast() { return this.final ? undefined : this.run.predictions.find(p => p.origin_ms === this.frame.time_ms); }
  get evaluation() { return this.run.evaluations.find(e => e.origin_ms === this.forecast?.origin_ms); }
  get matured() { return this.run.evaluations.filter(e => e.target_ms <= this.frame.time_ms).slice(-1)[0]; }
  get final() { return this.frame.sample_kind === 'FINAL'; }
  get currentPoint() { return this.project(this.local(this.observation.latitude, this.observation.longitude)); }
  get futurePoint() {
    const p = this.forecast;
    return p ? this.project(this.local(this.model === 'velocity' ? p.velocity_latitude : p.hold_latitude,
      this.model === 'velocity' ? p.velocity_longitude : p.hold_longitude)) : this.currentPoint;
  }
  get truthPoint() { const e = this.evaluation; return e ? this.project(this.local(e.actual_latitude, e.actual_longitude)) : this.currentPoint; }
  get trajectory() { return this.points(this.run.observations.filter(o => o.time_ms <= this.frame.time_ms).map(o => this.local(o.latitude, o.longitude))); }
  get distance() {
    const p = this.forecast; if (!p) return 0;
    const a = (this.model === 'velocity' ? p.velocity_latitude : p.hold_latitude) * Math.PI / 180;
    const b = (this.model === 'velocity' ? p.velocity_longitude : p.hold_longitude) * Math.PI / 180;
    const c = p.node_latitude * Math.PI / 180, d = p.node_longitude * Math.PI / 180;
    return 12756274 * Math.asin(Math.min(1, Math.sqrt(Math.sin((a - c) / 2) ** 2 + Math.cos(a) * Math.cos(c) * Math.sin((b - d) / 2) ** 2)));
  }
  get exitWarning() { return !!this.forecast && this.distance > this.forecast.radius_m; }
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
  chartX(t: number) { const a = this.run.frames[0].time_ms, b = this.run.frames.slice(-1)[0].time_ms; return 60 + 800 * (t - a) / (b - a); }
  chartTime(i: number) { const a = this.run.frames[0].time_ms, b = this.run.frames.slice(-1)[0].time_ms; return a + (b - a) * i / 3; }
  maximum(key: 'running_tasks' | 'waiting_assignment_bytes') {
    const step = key === 'running_tasks' ? 3 : 300;
    return Math.ceil(Math.max(1, ...this.run.frames.flatMap(f => f.nodes.map(n => n[key]))) / step) * step;
  }
  chartY(v: number, key: 'running_tasks' | 'waiting_assignment_bytes') { return 180 - v / this.maximum(key) * 150; }
  chartPath(node: string, key: 'running_tasks' | 'waiting_assignment_bytes') {
    return this.run.frames.slice(0, this.index + 1).filter(f => f.sample_kind === 'OBSERVATION').map((f,i) => {
      const value = f.nodes.find(n => n.node === node)![key];
      return i ? `H${this.chartX(f.time_ms)}V${this.chartY(value,key)}` : `M${this.chartX(f.time_ms)},${this.chartY(value,key)}`;
    }).join('');
  }
}
