'use strict';
const fs = require('node:fs');
const path = require('node:path');
const {spawn} = require('node:child_process');
const {randomUUID} = require('node:crypto');
const {StringDecoder} = require('node:string_decoder');

class LiveJournal {
  constructor() { this.offset = 0; this.pending = ''; this.decoder = new StringDecoder('utf8'); this.sequence = 0; this.meta = null; this.points = new Map(); this.latest = new Map(); this.load = null; }
  accept(row) {
    if (!row || row.sequence !== this.sequence + 1) throw Error('Live preview sequence is incomplete');
    if (row.type === 'META') {
      if (this.meta || row.schema_version !== 1 || !Number.isInteger(row.device_count) || row.device_count < 1 || row.device_count > 100) throw Error('Invalid preview metadata');
      this.meta = row;
    } else if (row.type === 'FRAME') {
      if (!this.meta || !Number.isInteger(row.device_id) || row.device_id < 0 || row.device_id >= this.meta.device_count || !Number.isFinite(row.time_ms) || !Number.isFinite(row.latitude) || !Number.isFinite(row.longitude)) throw Error('Invalid preview frame');
      const old = this.latest.get(row.device_id);
      if (old && row.time_ms <= old.time_ms) throw Error('Preview device time moved backwards');
      this.latest.set(row.device_id, row);
      if (row.sampled) {
        const points = this.points.get(row.device_id) || [];
        if (points.length >= this.meta.expected_observations) throw Error('Too many preview observations');
        points.push({type:'POINT', device_id:row.device_id, time_ms:row.time_ms, latitude:row.latitude, longitude:row.longitude});
        this.points.set(row.device_id, points);
      }
    } else if (row.type === 'LOAD') { if (!Array.isArray(row.nodes)) throw Error('Invalid live node load'); this.load = row; }
    else throw Error('Unknown live preview event');
    this.sequence = row.sequence;
  }
  async read(file) {
    let handle;
    try { handle = await fs.promises.open(file, 'r'); }
    catch (e) { if (e.code === 'ENOENT') return []; throw e; }
    try {
      const buffer = Buffer.allocUnsafe(256 * 1024);
      const {bytesRead} = await handle.read(buffer, 0, buffer.length, this.offset);
      this.offset += bytesRead;
      this.pending += this.decoder.write(buffer.subarray(0, bytesRead));
      const lines = this.pending.split('\n'); this.pending = lines.pop();
      if (this.pending.length > 65536) throw Error('Live preview record exceeds its size limit');
      const rows = [];
      for (const line of lines) { if (!line.trim()) continue; const row = JSON.parse(line); this.accept(row); rows.push(row); }
      return rows;
    } finally { await handle.close(); }
  }
  snapshot() { return this.meta ? [this.meta, ...[...this.points.values()].flat(), ...[...this.latest.values()].map(r=>({...r,type:'LATEST'})), ...(this.load?[this.load]:[])] : []; }
}

class MobilityJob {
  constructor({root, onStatus, onLive, onComplete, spawnProcess = spawn}) {
    this.root = root; this.onStatus = onStatus; this.onLive = onLive; this.onComplete = onComplete; this.spawnProcess = spawnProcess;
    this.state = {stage:'IDLE', active:false}; this.journal = new LiveJournal(); this.child = null; this.timer = null; this.pumping = false; this.cancelRequested = false; this.failure = null;
  }
  get active() { return this.state.active; }
  snapshot() { return {state:this.state, events:this.journal.snapshot()}; }
  update(details) { this.state = {...this.state,...details}; this.onStatus(this.state); }
  start(request) {
    if (this.active) throw Error('A mobility run is already active');
    if (!request || typeof request.config !== 'object' || !['nomadic','random-walk'].includes(request.config.strategy)) throw Error('Choose a mobility strategy');
    const config = request.config;
    if (JSON.stringify(config).length > 32768) throw Error('Mobility configuration is too large');
    let python = request.pythonExecutable?.trim() || process.env.MOBILITY_SHADOW_PYTHON || process.env.DISSECT_PYTHON;
    if (!python && process.env.CONDA_PREFIX) python = path.join(process.env.CONDA_PREFIX, process.platform==='win32'?'python.exe':'bin/python');
    python ||= process.platform === 'win32' ? 'python' : 'python3';
    if (typeof python !== 'string' || python.length > 2048 || /[\r\n\0]/.test(python) || python.startsWith('-')) throw Error('Invalid Python interpreter');
    const id = randomUUID();
    const session = path.join(this.root, 'simulator', 'sim_res', 'mobility-ui', id);
    fs.mkdirSync(session, {recursive:true});
    fs.writeFileSync(path.join(session,'config.json'), JSON.stringify(config,null,2)+'\n', {flag:'wx'});
    this.journal = new LiveJournal(); this.cancelRequested = false; this.failure = null;
    this.state = {id,session,stage:'PREPARING',active:true,config,startedAt:Date.now()}; this.onStatus(this.state);
    const log = fs.createWriteStream(path.join(session,'launcher.log'));
    log.on('error', error => this.abort(id, 'Cannot write the launch log: ' + error.message));
    try {
      const script = path.join(this.root,'predictor-ui','scripts','mobility','run_mobility_ui.py');
      this.child = this.spawnProcess(python,['-u',script,'--config',path.join(session,'config.json'),'--session',session,'--project-root',this.root],
        {cwd:this.root,shell:false,windowsHide:true,env:{...process.env,PYTHONUNBUFFERED:'1'}});
      this.child.stdout?.pipe(log,{end:false}); this.child.stderr?.pipe(log,{end:false});
      this.child.once('error',error=>{ log.end(); this.finishError(id,'Could not start Python: '+error.message+'. Start the UI from your dissect-fog environment, or select its Python executable.'); });
      this.child.once('close',(code)=>{ log.end(); this.finish(id,code).catch(e=>this.finishError(id,e.message)); });
      this.timer = setInterval(()=>this.pump().catch(e=>this.abort(id,e.message)),300);
    } catch (e) { log.end(); this.finishError(id,e.message); }
    return this.state;
  }
  async pump() {
    if (this.pumping || !this.state.session) return;
    this.pumping = true;
    const id = this.state.id, session = this.state.session;
    try {
      let progress;
      try { progress=JSON.parse(await fs.promises.readFile(path.join(session,'progress.json'),'utf8')); }
      catch (e) { if (e.code!=='ENOENT' && !(e instanceof SyntaxError)) throw e; }
      if (id!==this.state.id) return;
      if (progress && !this.cancelRequested) {
        // Only process exit + successful final result validation can mark COMPLETE.
        const stage = ['COMPLETE','RESULT_READY'].includes(progress.stage) ? 'AUDITING' : progress.stage;
        const next = {stage,output:progress.output,error:progress.error,batch:progress.batch};
        if (Object.entries(next).some(([k,v])=>v!==this.state[k])) this.update(next);
      }
      const rows = await this.journal.read(path.join(session,'live.jsonl'));
      if (rows.length && id===this.state.id) this.onLive({id,events:rows});
    } finally { this.pumping=false; }
  }
  stopTimer() { if (this.timer) clearInterval(this.timer); this.timer=null; }
  abort(id,error) {
    if (id!==this.state.id || !this.active) return;
    this.failure=error; this.stopTimer();
    if (this.child?.pid) { this.cancel(); this.update({error}); }
    else this.finishError(id,error);
  }
  finishError(id,error) {
    if (id!==this.state.id) return;
    this.stopTimer(); this.update({stage:'FAILED',active:false,error,finishedAt:Date.now()});
  }
  async finish(id,code) {
    if (id!==this.state.id || !this.active) return;
    this.stopTimer();
    // A timer read may still be in flight; wait without blocking the Electron loop.
    while(this.pumping) await new Promise(resolve=>setTimeout(resolve,10));
    try { await this.pump(); } catch(error) { this.failure ||= error.message; }
    this.child=null;
    if (this.failure) { this.finishError(id,this.failure); return; }
    if (this.cancelRequested || code===130) { this.update({stage:'CANCELLED',active:false,finishedAt:Date.now()}); return; }
    if (code!==0 || !this.state.output) throw Error(this.state.error || 'Mobility run failed. See '+path.join(this.state.session,'runner.log'));
    this.update({stage:'LOADING',active:true});
    await this.onComplete(this.state.output, ()=>id===this.state.id && !this.cancelRequested);
    if (id!==this.state.id) return;
    this.update({stage:this.cancelRequested?'CANCELLED':'COMPLETE',active:false,finishedAt:Date.now()});
  }
  cancel() {
    if (!this.active || this.cancelRequested) return false;
    this.cancelRequested=true; this.update({stage:'CANCELLING'});
    const child=this.child;
    if (child?.pid) {
      if (process.platform==='win32') {
        // Windows SIGTERM does not execute Python's handler. Kill this owned tree.
        spawn('taskkill',['/PID',String(child.pid),'/T','/F'],{windowsHide:true,stdio:'ignore'})
          .once('error',error=>{this.update({error:'Could not stop the process tree: '+error.message});});
      } else {
        child.kill('SIGTERM'); // Python's handler explicitly kills its JVM/worker descendants.
        const timer=setTimeout(()=>{if(this.child===child) child.kill('SIGKILL');},5000); timer.unref();
      }
    }
    return true;
  }
}
module.exports = {MobilityJob, LiveJournal};
