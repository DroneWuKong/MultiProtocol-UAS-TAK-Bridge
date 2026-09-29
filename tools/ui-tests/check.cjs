const {chromium} = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assets=path.resolve(__dirname,'../../app/src/main/assets');
const artifacts=path.join(__dirname,'artifacts');
fs.mkdirSync(artifacts,{recursive:true});
for (const name of ['tools-core.js','tools-ui.js']) new vm.Script(fs.readFileSync(path.join(assets,'tools',name),'utf8'),{filename:name});
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox'],...(process.env.PLAYWRIGHT_EXECUTABLE_PATH?{executablePath:process.env.PLAYWRIGHT_EXECUTABLE_PATH}:{})});
 try {
 const page=await browser.newPage({viewport:{width:393,height:852}});
 const errors=[];page.on('pageerror',error=>errors.push(error.message));
 // Serve exactly the APK assets at the same HTTPS origin used by Android.
 // Every external request is blocked: calculators and map initialization must
 // work without a CDN, USB hardware, map service or radio connection.
 await page.route('**/*',async route=>{
  const url=new URL(route.request().url());
  if(url.hostname!=='appassets.androidplatform.net')return route.abort();
  const file=path.resolve(assets,decodeURIComponent(url.pathname).replace(/^\/assets\//,''));
  if(!file.startsWith(assets+path.sep)||!fs.existsSync(file))return route.fulfill({status:404,body:'Not bundled'});
  return route.fulfill({path:file,contentType:({'.html':'text/html','.js':'application/javascript','.css':'text/css','.woff2':'font/woff2','.json':'application/json'})[path.extname(file)]});
 });
 await page.goto('https://appassets.androidplatform.net/assets/tools/tools_offline.html');
 await page.waitForFunction(()=>!!window._rfMapRef);
 assert.equal(await page.locator('#android-tool-picker,#mobile-tool-nav').count(),0,'Duplicate navigation returned');
 await page.waitForFunction(()=>document.getElementById('rf-map-status').textContent.includes('unavailable'));
 const ids=await page.locator('.tool-panel').evaluateAll(p=>p.map(x=>x.id.slice(5)));
 assert.equal(ids.length,13);
 for(const width of [360,393,800]){
  await page.setViewportSize({width,height:852});
  for(const id of ids){
   await page.locator('#open-tools').click();await page.locator('[data-tool="'+id+'"]').click();
   assert.equal(await page.locator('.tool-panel.active').getAttribute('id'),'tool-'+id);
   const clipped=await page.locator('.tool-panel.active').evaluate(panel=>[...panel.querySelectorAll('button,input,select,textarea')]
    .filter(e=>e.checkVisibility()&&!e.closest('.leaflet-container'))
    .map(e=>({id:e.id||e.textContent.trim().slice(0,35),left:e.getBoundingClientRect().left,right:e.getBoundingClientRect().right}))
    .filter(e=>e.left < -1 || e.right > innerWidth+1));
   assert.deepEqual(clipped,[],`${id} controls clipped at ${width}px`);
  }
 }
 await page.setViewportSize({width:393,height:852});
 await page.locator('#open-tools').click();await page.locator('#tool-search').fill('flight controller');
 assert.equal(await page.locator('.tool-choice:visible').count(),3);
 await page.locator('[data-tool="fc-matcher"]').click();
 await page.locator('#fc-matcher-input').fill('MCU STM32H743\nGyro ICM42688P\ntarget: MATEKH743');
 await page.getByRole('button',{name:'Identify FC',exact:true}).click();
 assert.match(await page.locator('#fc-matcher-result').textContent(),/H743/);
 await page.screenshot({path:path.join(artifacts,'fc-match.png')});
 await page.evaluate(()=>showTool('range'));
 const before=await page.locator('#range-results').textContent();
 await page.locator('#range-power').fill('1000');
 assert.notEqual(await page.locator('#range-results').textContent(),before);
 const rangeText=await page.locator('#range-results').textContent();
 assert.match(rangeText,/30.0 dBm/,'1000 mW must equal 30 dBm');
 assert.match(rangeText,/164\.[0-9] km/,'MHz/km path-loss units regressed');
 await page.screenshot({path:path.join(artifacts,'range.png')});
 await page.evaluate(()=>showTool('rf-terrain'));
 await page.locator('#rf-map').click({position:{x:100,y:100}});
 await page.locator('#rf-map').click({position:{x:240,y:160}});
 assert.doesNotMatch(await page.locator('#rf-tx-coords').textContent(),/click to place/);
 assert.notEqual(await page.locator('#rf-rx-coords').textContent(),'—');
 await page.screenshot({path:path.join(artifacts,'terrain-offline.png')});
 await page.evaluate(()=>showTool('mesh-planner'));
 await page.waitForFunction(()=>!!window._meshMapRef);
 await page.evaluate(()=>showTool('rf-terrain'));
 await page.locator('#open-tools').click();await page.screenshot({path:path.join(artifacts,'tool-library.png')});
 assert.deepEqual(errors,[],'Uncaught JavaScript errors');
 const summary={toolPanels:13,viewportWidths:[360,393,800],navigationChecks:39,scriptErrors:errors.length,checks:['search','FC matcher','range calculator','offline map initialization','map point placement','mesh navigation']};
 fs.writeFileSync(path.join(artifacts,'results.json'),JSON.stringify(summary,null,2));console.log(JSON.stringify(summary));
 } finally { await browser.close(); }
})().catch(e=>{console.error(e);process.exitCode=1;});
