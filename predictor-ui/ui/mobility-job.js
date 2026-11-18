'use strict';
const fs = require('node:fs');
const path = require('node:path');
const {spawn} = require('node:child_process');
const {randomUUID} = require('node:crypto');
const {StringDecoder} = require('node:string_decoder');

class LiveJournal {
  constructor() { this.offset = 0; this.pending = ''; this.decoder = new StringDecoder('utf8'); this.sequence = 0; this.meta = null; this.points = new Map(); this.latest = new Map(); this.load = null; this.history = []; this.nodeLoadMeta = null; }
  accept(row) {
    if (!row || row.sequence !== this.sequence + 1) throw Error('Live preview sequence is incomplete');
    if (row.type === 'META') {
      if (this.meta || ![1,2].includes(row.schema_version) || !Number.isInteger(row.device_count) || row.device_count < 1 || row.device_count > 100) throw Error('Invalid preview metadata');
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
    } else if (row.type === 'MOBILITY_STATUS') {
      if (!this.meta || !Number.isInteger(row.device_id) || row.device_id<0 || row.device_id>=this.meta.device_count
        || !Number.isSafeInteger(row.time_ms) || row.time_ms<0 || row.lifecycle?.state!=='TRAINING'
        || row.time_ms<(this.latest.get(row.device_id)?.time_ms??0)) throw Error('Invalid mobility training status');
    } else if (row.type === 'LOAD') { if (!this.meta || !Array.isArray(row.nodes) || !Number.isFinite(row.time_ms)) throw Error('Invalid live node load'); this.load = row; }
    else if (row.type === 'NODE_LOAD') {
      if (!this.meta || row.run_id!==this.meta.run_id || !Number.isSafeInteger(row.time_ms) || row.time_ms<0
        || !row.data || row.data.time_ms!==row.time_ms
        || !['META','SAMPLE','VECTOR','EVALUATION','SELECTION','EVENT'].includes(row.kind)) throw Error('Invalid live node-load record');
      if (row.kind==='META') {
        if (this.nodeLoadMeta || row.data.run_id!==row.run_id || row.data.interval_ms!==60000
          || !Array.isArray(row.data.node_ids) || !row.data.node_ids.length
          || row.data.node_ids.length!==this.meta.nodes.length
          || new Set(row.data.node_ids).size!==row.data.node_ids.length
          || row.data.node_ids.some(id=>!this.meta.nodes.some(n=>n.name===id))) throw Error('Invalid live node-load metadata');
        this.nodeLoadMeta=row.data;
      } else if (!this.nodeLoadMeta?.node_ids.includes(row.data.node_id)) throw Error('Unknown live load node');
      if (row.kind==='VECTOR' && (!Array.isArray(row.data.values)
        || row.data.values.length!==(row.data.model==='Persistence'?1:64)
        || row.data.values.some(v=>!Number.isFinite(v)||v<0))) throw Error('Incomplete live forecast vector');
    }
    else throw Error('Unknown live preview event');
    this.sequence = row.sequence;
    this.history.push(row);
  }
  async read(file) {
    let handle;
    try { handle = await fs.promises.open(file, 'r'); }
    catch (e) { if (e.code === 'ENOENT') return []; throw e; }
    try {
      if ((await handle.stat()).size<this.offset) throw Error('Live preview journal was truncated');
      const buffer = Buffer.allocUnsafe(256 * 1024);
      const {bytesRead} = await handle.read(buffer, 0, buffer.length, this.offset);
      this.offset += bytesRead;
      this.pending += this.decoder.write(buffer.subarray(0, bytesRead));
      const lines = this.pending.split('\n'); this.pending = lines.pop();
      if (this.pending.length > 65536) throw Error('Live preview record exceeds its size limit');
      const rows = [];
      for (const line of lines) { if (line.length>65536) throw Error('Live preview record exceeds its size limit'); if (!line.trim()) continue; const row = JSON.parse(line); this.accept(row); rows.push(row); }
      return rows;
    } finally { await handle.close(); }
  }
  // Retain chronological records so reload can restore historical browsing too.
  snapshot() { return this.history.slice(); }

  verifyNodeLoad(data) {
    if (this.meta?.schema_version!==2) return; // Older launchers did not emit load records.
    if (!data || !this.nodeLoadMeta || data.run_id!==this.meta.run_id) throw Error('Live/final node-load run differs');
    const records=this.history.filter(r=>r.type==='NODE_LOAD');
    const compare=(a,b,label)=>{
      const sorted=rows=>rows.map(r=>JSON.stringify(r)).sort();
      if(JSON.stringify(sorted(a))!==JSON.stringify(sorted(b))) throw Error('Live/final node load differs: '+label);
    };
    compare(this.nodeLoadMeta.node_ids,data.nodes.map(n=>n.node_id),'nodes');
    compare(this.nodeLoadMeta.models,data.models,'models');
    compare(this.nodeLoadMeta.baselines??[],data.baselines??[],'baselines');
    if(data.models.length && (data.profile!==this.nodeLoadMeta.profile
      || Object.entries(this.nodeLoadMeta.policy).some(([k,v])=>data.policy?.[k]!==v))) throw Error('Live/final load policy differs');
    const of=kind=>records.filter(r=>r.kind===kind).map(r=>r.data);
    const identity=p=>[p.node_id,p.model,p.version,p.origin_index,p.target_index];
    compare(of('SAMPLE').map(r=>[r.node_id,r.index,r.time_ms,r.value]),
      data.nodes.flatMap(n=>n.samples.map(s=>[n.node_id,s.index,s.time_ms,s.value])),'actual samples');
    compare(of('VECTOR').flatMap(r=>r.values.map((value,i)=>[r.node_id,r.model,r.version,r.origin_index,r.origin_index+i+1,
      r.time_ms,r.time_ms+(i+1)*data.interval_ms,value,r.inference_vector_ms,r.persistence_bytes??null])),
      data.nodes.flatMap(n=>[...n.baseline,...n.predictions].map(p=>[...identity(p),p.origin_ms,p.target_ms,p.predicted_bytes,p.inference_vector_ms,p.persistence_bytes??null])),'issued vectors');
    compare(of('EVALUATION').map(r=>[...identity(r),r.time_ms,r.actual_bytes]),
      data.nodes.flatMap(n=>[...n.baselineEvaluations,...n.evaluations].map(p=>[...identity(p),p.target_ms,p.actual_bytes])),'matched targets');
    compare(of('SELECTION').map(r=>[...identity(r),r.time_ms]),
      data.nodes.flatMap(n=>n.selections.map(p=>[...identity(p),p.selection_ms])),'recorded service');
    compare(of('EVENT').map(r=>[r.node_id,r.model,r.version,r.time_ms,r.event]),
      data.nodes.flatMap(n=>n.events.map(r=>[r.node_id,r.model,r.version,r.time_ms,r.event])),'lifecycle');
    compare(of('EVENT').filter(r=>r.event==='TRAINED').map(r=>[r.node_id,r.model,r.version,r.trained_at_ms,r.training_windows,r.training_seconds]),
      data.nodes.flatMap(n=>n.versions.map(v=>[n.node_id,v.model,v.version,v.trained_at_ms,v.training_windows,v.training_seconds])),'versions');
  }
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
    try {
      // A fast simulation can leave more than one 256 KiB batch at process exit.
      let offset;
      do { offset=this.journal.offset; await this.pump(); } while(this.journal.offset!==offset);
      if(code===0 && !this.cancelRequested && this.journal.pending.trim()) throw Error('Incomplete final live preview record');
    } catch(error) { this.failure ||= error.message; }
    this.child=null;
    if (this.failure) { this.finishError(id,this.failure); return; }
    if (this.cancelRequested || code===130) { this.update({stage:'CANCELLED',active:false,finishedAt:Date.now()}); return; }
    if (code!==0 || !this.state.output) throw Error(this.state.error || 'Mobility run failed. See '+path.join(this.state.session,'runner.log'));
    this.update({stage:'LOADING',active:true});
    await this.onComplete(this.state.output, ()=>id===this.state.id && !this.cancelRequested,
      data=>this.journal.verifyNodeLoad(data));
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
