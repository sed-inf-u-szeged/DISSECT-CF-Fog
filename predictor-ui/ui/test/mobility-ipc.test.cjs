const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const main = fs.readFileSync(path.join(__dirname, '..', 'main.js'), 'utf8');
function boot(mobilityPath, bad = false, selection = { canceled: true, filePaths: [] }, configure = false) {
  const events = {}, handlers = {}, messages = [], counters = { reads: 0, sqlite: 0, intervals: 0 };
  let window;
  class Window {
    constructor() { window = this; this.webContents = { once: (n, f) => events[n] = f, send: (n, data) => messages.push([n, data]) }; }
    loadFile() {} on() {} isDestroyed() { return false; }
    static getAllWindows() { return []; }
  }
  const app = { whenReady: () => ({ then: cb => cb() }), on() {} };
  const context = {
    __dirname: path.resolve(__dirname, '..'), console, Buffer,
    process: { env: mobilityPath ? { DISSECT_MOBILITY_RUN_DIR: mobilityPath } : configure ? {DISSECT_MOBILITY_CONFIGURE:'1'} : {}, platform: 'win32' },
    setInterval: () => counters.intervals++, clearInterval() {},
    require: name => {
      if (name === 'electron') return { app, BrowserWindow: Window,
        ipcMain: { handle: (name, fn) => handlers[name] = fn }, dialog: { showOpenDialog: async () => selection } };
      if (name === 'sqlite3') { counters.sqlite++; return { verbose: () => ({}) }; }
      if (name === './mobility-results') return { readMobilityRun: root => { counters.reads++; assert.ok(root === mobilityPath || selection.filePaths.includes(root)); if (bad) throw Error('Invalid run'); return { label: 'selected-run' }; } };
      return require(name.startsWith('.') ? path.join(__dirname,'..',name) : name);
    }
  };
  vm.runInNewContext(main, context); events['did-finish-load']();
  return { messages, counters, choose: () => handlers['open-mobility-run']({sender:window.webContents}),
    job: () => handlers['get-mobility-job']({sender:window.webContents}),
    untrustedStart: () => handlers['start-mobility-job']({sender:{}},{}),
    untrusted: () => handlers['open-mobility-run']({sender:{}}) };
}
test('configure entry opens before simulation without SQLite or result discovery', () => {
  const app=boot(undefined,false,undefined,true);
  assert.equal(app.job().configure,true);assert.equal(app.job().state.stage,'IDLE');
  assert.deepEqual(app.counters,{reads:0,sqlite:0,intervals:0});
  assert.throws(()=>app.untrustedStart(),/Unknown UI/);
});
test('explicit Mobility run bypasses database discovery and sends that run once', () => {
  const { messages, counters } = boot('D:\\test path\\mobility-42');
  assert.equal(messages[0][0], 'mobility-updated');
  assert.equal(messages[0][1].label, 'selected-run');
  assert.deepEqual(counters, { reads: 1, sqlite: 0, intervals: 0 });
});
test('invalid Mobility result surfaces an error without loading an old database', () => {
  const { messages, counters } = boot('/tmp/missing', true);
  assert.equal(messages[0][0], 'data-error');
  assert.match(messages[0][1], /Invalid run/);
  assert.equal(counters.sqlite, 0);
});
test('default load prediction mode retains SQLite and its refresh timer', () => {
  const { counters } = boot();
  assert.equal(counters.sqlite, 1); assert.equal(counters.intervals, 1); assert.equal(counters.reads, 0);
});
test('folder picker selects a validated run and cancellation keeps the existing run', async () => {
  const selected=boot('/tmp/old',false,{canceled:false,filePaths:['/tmp/new']});
  assert.equal(await selected.choose(),true);assert.equal(selected.counters.reads,2);
  assert.equal(selected.messages.at(-1)[0],'mobility-updated');
  const canceled=boot('/tmp/old');assert.equal(await canceled.choose(),false);assert.equal(canceled.counters.reads,1);
});
test('folder errors are reported and unknown renderer requests cannot open dialogs', async () => {
  const selected=boot('/tmp/old',true,{canceled:false,filePaths:['/tmp/new']});
  assert.equal(await selected.choose(),false);assert.equal(selected.messages.at(-1)[0],'data-error');
  assert.equal(await selected.untrusted(),false);assert.equal(selected.counters.reads,2);
});
test('closing an active UI waits for its owned run to stop', async () => {
  let window, controller, callbacks, closed=0, cancelled=0;
  const events={};
  class Window {
    constructor(){window=this;this.webContents={once(){},send(){}};}
    loadFile(){} on(name,fn){events[name]=fn;} isDestroyed(){return false;} close(){closed++;}
    static getAllWindows(){return [];}
  }
  class Job {
    constructor(options){callbacks=options;controller=this;this.active=true;}
    cancel(){cancelled++;return true;}
  }
  vm.runInNewContext(main,{
    __dirname:path.resolve(__dirname,'..'),console,Buffer,setInterval,clearInterval,
    process:{env:{DISSECT_MOBILITY_CONFIGURE:'1'},platform:'win32'},
    require:name=>{
      if(name==='electron')return {app:{whenReady:()=>({then:fn=>fn()}),on(){}},BrowserWindow:Window,ipcMain:{handle(){}},dialog:{showMessageBox:async()=>({response:1})}};
      if(name==='./mobility-job')return {MobilityJob:Job};
      return require(name.startsWith('.')?path.join(__dirname,'..',name):name);
    }
  });
  let prevented=0;events.close({preventDefault:()=>prevented++});
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(prevented,1);assert.equal(cancelled,1);assert.equal(closed,0);
  controller.active=false;callbacks.onStatus({stage:'CANCELLED',active:false});
  assert.equal(closed,1);
});
