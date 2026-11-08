const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const main = fs.readFileSync(path.join(__dirname, '..', 'main.js'), 'utf8');
function boot(mobilityPath, bad = false) {
  const events = {}, messages = [], counters = { reads: 0, sqlite: 0, intervals: 0 };
  class Window {
    constructor() { this.webContents = { once: (n, f) => events[n] = f, send: (n, data) => messages.push([n, data]) }; }
    loadFile() {} on() {} isDestroyed() { return false; }
    static getAllWindows() { return []; }
  }
  const app = { whenReady: () => ({ then: cb => cb() }), on() {} };
  const context = {
    __dirname: path.resolve(__dirname, '..'), console, Buffer,
    process: { env: mobilityPath ? { DISSECT_MOBILITY_RUN_DIR: mobilityPath } : {}, platform: 'win32' },
    setInterval: () => counters.intervals++, clearInterval() {},
    require: name => {
      if (name === 'electron') return { app, BrowserWindow: Window };
      if (name === 'sqlite3') { counters.sqlite++; return { verbose: () => ({}) }; }
      if (name === './mobility-results') return { readMobilityRun: root => { counters.reads++; assert.equal(root, mobilityPath); if (bad) throw Error('Invalid run'); return { label: 'selected-run' }; } };
      return require(name);
    }
  };
  vm.runInNewContext(main, context); events['did-finish-load']();
  return { messages, counters };
}
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
