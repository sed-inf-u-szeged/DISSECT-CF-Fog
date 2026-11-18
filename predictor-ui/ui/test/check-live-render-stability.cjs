'use strict';
// Real Angular templates/change detection with an in-memory renderer. The chart
// double models Chart.js's Zone-bound document/subtree MutationObserver, not
// canvas layout. Run separately from DOM-free replay logic and browser QA.
const fs=require('node:fs'),path=require('node:path'),{spawnSync}=require('node:child_process');

async function main() {
  const root=path.resolve(__dirname,'..'),temp=fs.mkdtempSync(path.join(root,'.render-test-'));
  try {
    const out=path.join(temp,'render.mjs');
    await require('esbuild').build({entryPoints:[path.join(__dirname,'live-render-stability.fixture.ts')],
      outfile:out,bundle:true,platform:'node',format:'esm',packages:'external',tsconfig:path.join(root,'tsconfig.json'),
      plugins:[{name:'angular-resources-and-chart-observer',setup(build){
        build.onLoad({filter:/\.component\.ts$/},args=>{
          const contents=fs.readFileSync(args.path,'utf8')
            .replace(/templateUrl\s*:\s*['"]([^'"]+)['"]/g,(_,url)=>'template:'+JSON.stringify(fs.readFileSync(path.resolve(path.dirname(args.path),url),'utf8')))
            .replace(/styleUrls\s*:\s*\[[^\]]*\]/g,'styles:[]');
          return {contents,loader:'ts'};
        });
        build.onResolve({filter:/^chart\.js\/auto$/},()=>({path:'chart-observer',namespace:'test-double'}));
        build.onResolve({filter:/^chartjs-plugin-annotation$/},()=>({path:'annotation',namespace:'test-double'}));
        build.onLoad({filter:/.*/,namespace:'test-double'},args=>({loader:'js',contents:args.path==='annotation'?'export default {};':`
          export default class Chart {
            static register() {}
            constructor(canvas,config) {
              this.data=config.data;this.options=config.options;
              this.probe=globalThis.__liveRenderProbe;
              this.observer=Zone.current.wrap(()=>{this.probe.observerCalls++;},'Chart.js detach observer');
              this.probe.observers.add(this.observer);this.probe.charts.add(this);
              this.record('create');
            }
            record(action) {this.probe.chartCalls.push({action,inAngular:!!Zone.current.get('isAngularZone')});}
            update() {this.record('update');}
            destroy() {this.record('destroy');this.probe.observers.delete(this.observer);this.probe.charts.delete(this);}
          }
        `}));
      }}]});
    // An event-loop regression cannot be allowed to hang the test runner itself.
    const run=spawnSync(process.execPath,[out,...process.argv.slice(2)],{cwd:root,encoding:'utf8',timeout:15000});
    process.stdout.write(run.stdout||'');process.stderr.write(run.stderr||'');
    if(run.error) throw run.error;
    if(run.status!==0) throw Error('Live render regression failed (exit '+run.status+')');
  } finally {fs.rmSync(temp,{recursive:true,force:true});}
}
main().catch(error=>{console.error(error);process.exitCode=1;});
