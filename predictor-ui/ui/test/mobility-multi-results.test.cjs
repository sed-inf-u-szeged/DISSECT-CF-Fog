'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const {readMobilityRun} = require('../mobility-results');
const {writeFixture} = require('./mobility-multi-fixture.cjs');
function directory(t) { const root=fs.mkdtempSync(path.join(os.tmpdir(),'mobility-multi-test-')); t.after(()=>fs.rmSync(root,{recursive:true,force:true})); return root; }
test('ten devices load independently and share node load arrays', t => {
  const root=directory(t); writeFixture(root); const result=readMobilityRun(root);
  assert.equal(result.devices.length,10);
  for (const {id,data} of result.devices) {
    assert.equal(data.learning.device_id,id);
    assert.ok(data.observations.every(o=>o.device_id===id));
    assert.equal(data.learning.selectedEvaluations.length,165);
    assert.equal(data.learning.versions[0].selected_origins,6);
    assert.equal(data.frames,result.frames);
  }
});
test('a missing or substituted device bundle cannot load', t => {
  const root=directory(t); writeFixture(root,2);
  fs.copyFileSync(path.join(root,'devices/0/retry_predictions.csv'),path.join(root,'devices/1/retry_predictions.csv'));
  assert.throws(()=>readMobilityRun(root),/identity/);
  fs.unlinkSync(path.join(root,'devices/1/retry_predictions.csv'));
  assert.throws(()=>readMobilityRun(root),/missing/);
});
test('manifest path traversal, duplicate identities and unfinished runs are rejected', t => {
  const root=directory(t); const manifest=writeFixture(root,2);
  const save=()=>fs.writeFileSync(path.join(root,'multi-device.json'),JSON.stringify(manifest));
  manifest.devices[1].path='../other'; save(); assert.throws(()=>readMobilityRun(root),/unsafe/);
  manifest.devices[1]={...manifest.devices[0]}; save(); assert.throws(()=>readMobilityRun(root),/duplicate/);
  manifest.status='RUNNING'; save(); assert.throws(()=>readMobilityRun(root),/incomplete/);
});
test('aggregate counts must equal the sum of the device records', t => {
  const root=directory(t); const manifest=writeFixture(root,2);
  manifest.matched++; fs.writeFileSync(path.join(root,'multi-device.json'),JSON.stringify(manifest));
  assert.throws(()=>readMobilityRun(root),/aggregate count/);
});
