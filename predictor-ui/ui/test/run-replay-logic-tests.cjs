'use strict';
// DOM-free component logic tests using the real Angular modules. Template checking
// is performed separately by `npm run build`; this is not a browser test.
const fs=require('fs'),path=require('path'),{pathToFileURL}=require('url');
async function main() {
  const root=path.resolve(__dirname,'..'),temp=fs.mkdtempSync(path.join(root,'.replay-test-'));
  try {
    const core=require('jasmine-core'),jasmine=core.core(core),env=jasmine.getEnv();
    Object.assign(globalThis,core.interface(jasmine,env));env.configure({random:false});
    let count=0,failed=0;
    env.addReporter({specDone:r=>{count++;if(r.status!=='passed'){failed++;console.error(r.fullName,r.failedExpectations);}},
      jasmineDone:r=>{console.log(JSON.stringify({status:r.overallStatus,count,failed}));if(r.overallStatus!=='passed')process.exitCode=1;}});
    const specs=['src/app/components/mobility-readiness/mobility-readiness.component.spec.ts','src/app/components/mobility-map/mobility-map.component.spec.ts','src/app/shared/node-load-timeline.spec.ts','src/app/shared/node-load-migration.spec.ts',
      'src/app/components/node-load-comparison/node-load-comparison.component.spec.ts',
      'src/app/components/node-load-view/node-load-view.component.spec.ts',
      'src/app/components/mobility-view/mobility-view.component.spec.ts',
      'src/app/components/mobility-launcher/mobility-launcher.component.spec.ts','src/app/shared/mobility-display.spec.ts'];
    const out=path.join(temp,'specs.mjs');
    require('esbuild').buildSync({stdin:{contents:specs.map(s=>'import '+JSON.stringify('./'+s)+';').join('\n'),resolveDir:root},
      outfile:out,bundle:true,platform:'node',format:'esm',packages:'external',
      tsconfig:path.join(root,'tsconfig.json')});
    await import('@angular/compiler');
    await import(pathToFileURL(out).href);
    await env.execute();
  } finally {fs.rmSync(temp,{recursive:true,force:true});}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
