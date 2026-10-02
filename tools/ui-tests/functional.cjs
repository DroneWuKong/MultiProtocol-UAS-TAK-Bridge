const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const assets=path.resolve(__dirname,'../../app/src/main/assets');
const artifacts=path.join(__dirname,'artifacts');
fs.mkdirSync(artifacts,{recursive:true});
for(const file of ['tools-core.js','tools-ui.js','tools-actions.js','vtx-presets.js'])new vm.Script(fs.readFileSync(path.join(assets,'tools',file),'utf8'),{filename:file});
const origin='https://appassets.androidplatform.net';
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox'],...(process.env.PLAYWRIGHT_EXECUTABLE_PATH?{executablePath:process.env.PLAYWRIGHT_EXECUTABLE_PATH}:{})});
 const checks=[];
 try{
 const context=await browser.newContext({viewport:{width:393,height:852},acceptDownloads:true,permissions:['clipboard-read','clipboard-write']});
 const page=await context.newPage();
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 let elevation='ok',apiCalls=0,geoCalls=0;
 await page.route('**/*',async route=>{
  const u=new URL(route.request().url());
  if(u.origin===origin){
   const file=path.resolve(assets,decodeURIComponent(u.pathname).replace(/^\/assets\//,''));
   if(!file.startsWith(assets+path.sep)||!fs.existsSync(file))return route.fulfill({status:404,body:'Missing asset'});
   return route.fulfill({path:file,contentType:({'.js':'application/javascript','.json':'application/json','.html':'text/html','.css':'text/css','.woff2':'font/woff2'})[path.extname(file)]});
  }
  if(u.hostname==='epqs.nationalmap.gov'||u.hostname==='api.open-elevation.com'){
   apiCalls++;
   if(elevation==='offline')return route.abort();
   if(elevation==='invalid')return route.fulfill({json:u.hostname==='epqs.nationalmap.gov'?{value:'NaN'}:{results:[]}});
   if(u.hostname==='epqs.nationalmap.gov')return route.fulfill({json:{value:1600}});
   const locations=u.searchParams.get('locations').split('|');
   return route.fulfill({json:{results:locations.map(()=>({elevation:1600}))}});
  }
  if(u.hostname==='nominatim.openstreetmap.org'){
   geoCalls++;
   if(elevation==='offline')return route.abort();
   return route.fulfill({json:[{lat:'48.8582',lon:'2.2945',display_name:'Eiffel Tower, Paris'}]});
  }
  return route.abort();
 });
 await page.goto(origin+'/assets/tools/tools_offline.html');
 await page.waitForFunction(()=>window._rfMapRef&&window._mafiaRX);
 async function tool(id){await page.evaluate(id=>showTool(id),id);}
 async function text(id){return page.locator('#'+id).textContent();}
 async function fill(id,value){await page.locator('#'+id).fill(String(value));}
 async function has(id,re){assert.match(await text(id),re,id);}
 async function finite(id){assert.doesNotMatch(await text(id),/NaN|Infinity|undefined/,id);}
 function done(name){checks.push(name);console.log('PASS '+name);}
 // Every inline action resolves to a real function (the old mesh Cache buttons did not).
 const missing=await page.evaluate(()=>[...document.querySelectorAll('[onclick],[onchange],[oninput]')].flatMap(el=>
  ['onclick','onchange','oninput'].flatMap(a=>[...(el.getAttribute(a)||'').matchAll(/\b([A-Za-z_$][\w$]*)\(/g)].map(m=>m[1])))
  .filter(n=>!['if','splice','getElementById'].includes(n)).filter(n=>{try{return typeof eval(n)!=='function';}catch(e){return true;}}));
 assert.deepEqual([...new Set(missing)],[]);done('all inline handlers exist');
 await tool('range');
 for(const [preset,power] of [['elrs900',100],['elrs24',250],['mafia433',1000],['vtx58',600],['vtx13',1000]]){
  await page.locator(`[onclick="setRangePreset('${preset}')"]`).click();
  await has('range-results',new RegExp((10*Math.log10(power)).toFixed(1)+' dBm'));await finite('range-results');
 }
 await page.evaluate(()=>setRangePreset('elrs900'));await fill('range-power',1000);await has('range-results',/164\.[0-9] km/);
 for(const value of ['',0,-1]){await fill('range-power',value);await has('range-results',/valid/);}
 done('range: five presets, known budget, invalid inputs');
 await tool('fresnel');await fill('fres-dist',1000);await fill('fres-freq',1000);await fill('fres-point',50);
 await has('fresnel-results',/8.7 m/);await has('fresnel-results',/5.2 m/);
 await fill('fres-point',101);await has('fresnel-results',/valid/);await fill('fres-point',25);await has('fresnel-results',/7.5 m/);await finite('fresnel-results');
 done('Fresnel: independent midpoint and quarter-path values');
 await tool('dipole');await fill('dip-freq',1000);await fill('dip-vf',1);await page.locator('#dip-unit').selectOption('mm');await has('dipole-results',/75.0 mm/);await has('dipole-results',/150.0 mm/);
 await page.locator('#dip-unit').selectOption('in');await has('dipole-results',/2.95 in/);
 await fill('dip-vf',0);await has('dipole-results',/valid/);await fill('dip-vf',0.97);
 for(const f of [433,868,915,1280,2400,5800]){await page.locator(`[onclick="setDipPreset(${f})"]`).click();await finite('dipole-results');}
 done('antenna: lengths, units, presets, invalid velocity factor');
 await tool('harmonics');await fill('harm-freq',580);await has('harm-results',/H10/);await has('harm-results',/5,800|5800/);
 await fill('harm-freq',525.14);await has('harm-gps-warnings',/GPS L1/);
 await fill('harm-vband-start',6000);await has('harm-results',/end must exceed/);await fill('harm-vband-start',5600);await fill('harm-freq',-1);await has('harm-results',/valid/);
 for(const cat of ['c2','telem','video','mesh','other']){await page.locator(`[data-cat="${cat}"]`).click();if(cat!=='other'){await page.locator('#harm-presets button').first().click();await finite('harm-results');}}
 done('harmonics: frequencies, categories, reversed-band rejection');
 await tool('channel-planner');await page.locator('#pilots-list .pilot-row').nth(1).locator('select').nth(1).selectOption('1');await has('channel-results',/SAME CHANNEL/);
 await page.locator('#pilots-list .pilot-row').nth(1).locator('select').nth(1).selectOption('7');await has('channel-results',/All channels clear/);
 for(let i=0;i<5;i++)await page.locator('#add-pilot-btn').click();assert.equal(await page.locator('.pilot-row').count(),6);
 for(let i=0;i<5;i++)await page.locator('.pilot-row button').last().click();await has('channel-results',/at least 2 pilots/);
 done('channel planner: conflicts, six-pilot cap, removal');
 await tool('closest-channel');await fill('cc-freq',5800);await page.locator('#cc-band').selectOption('all');await has('cc-results',/F4/);await has('cc-results',/Exact/);
 await page.locator('#cc-band').selectOption('R');await has('cc-results',/R5/);await has('cc-results',/5806/);
 await fill('cc-freq','');await has('cc-results',/valid/);done('closest channel: exact, nearest, band filter, empty input');
 await tool('vtx-config');let presets=0;
 const manufacturers=await page.locator('#vtx-mfr option').evaluateAll(o=>o.map(x=>x.value));
 for(const mfr of manufacturers){await page.locator('#vtx-mfr').selectOption(mfr);const models=await page.locator('#vtx-model option').evaluateAll(o=>o.map(x=>x.value));
  for(const model of models){await page.locator('#vtx-model').selectOption(model);const cli=await text('vtx-cli-output');
   assert.doesNotMatch(cli,/^(resource |saveVtxTable|set vtxtable)/m);
   const bands=Number(cli.match(/^vtxtable bands (\d+)$/m)[1]);assert.equal([...cli.matchAll(/^vtxtable band \d+ /gm)].length,bands);
   for(const line of cli.split('\n').filter(l=>/^vtxtable band \d/.test(l))){assert.equal(line.split(/\s+/).length,14);assert.match(line,/ (CUSTOM|FACTORY) /);}
   const count=Number(cli.match(/^vtxtable powerlevels (\d+)$/m)[1]);
   assert.equal(cli.match(/^vtxtable powervalues (.+)$/m)[1].split(' ').length,count);
   assert.equal(cli.match(/^vtxtable powerlabels (.+)$/m)[1].split(' ').length,count);presets++;
  }
 }
 assert.equal(presets,10);await page.locator('#vtx-mfr').selectOption('SmartAudio');await page.locator('#vtx-model').selectOption('VTX profile 08 (SmartAudio 2.1)');await page.locator('#vtx-uart').selectOption('3');await has('vtx-cli-output',/serial 2 2048 115200 57600 0 115200/);await has('vtx-cli-output',/powervalues 14 20 26 36/);
 await page.locator('#vtx-copy-btn').click();assert.equal(await page.evaluate(()=>navigator.clipboard.readText()),await text('vtx-cli-output'));
 done('VTX configuration: ten sourced presets, UART, complete clipboard output');
 await tool('unlock-vtx');
 for(const [protocol,expected] of [['smartaudio','14 23 27 29'],['sa20','0 1 2 3'],['tramp','25 200 500 800']]){
  await page.locator('#vtx-protocol').selectOption(protocol);await page.locator('#vtx-maxpower').selectOption('4');assert.match(await page.locator('#vtx-output').inputValue(),new RegExp('powervalues '+expected));
 }
 for(const band of ['R','F','E','A','B'])await page.locator('#vtx-band-'+band).uncheck();assert.match(await page.locator('#vtx-output').inputValue(),/Select at least/);
 await page.locator('#vtx-band-R').check();const table=await page.locator('#vtx-output').inputValue();assert.match(table,/band 1 RACEBAND R CUSTOM 5658/);assert.doesNotMatch(table,/saveVtxTable/);
 await page.locator('#vtx-table-copy').click();assert.equal(await page.evaluate(()=>navigator.clipboard.readText()),table);
 done('VTX table: protocol encodings, subset bands, copy, empty-band rejection');
 await tool('fc-matcher');await fill('fc-matcher-input','MCU STM32F405 Clock=168MHz\nGYRO=MPU6000\nboard_name MATEKF405');await page.getByRole('button',{name:'Identify FC',exact:true}).click();await has('fc-matcher-result',/MATEKF405/);await has('fc-matcher-result',/F405/);assert.doesNotMatch(await text('fc-matcher-result'),/MCUSTM32/);
 await fill('fc-matcher-input','unrelated text');await page.getByRole('button',{name:'Identify FC',exact:true}).click();await has('fc-matcher-result',/Could not identify/);
 await page.locator('#tool-fc-matcher').getByRole('button',{name:'Clear',exact:true}).click();assert.equal(await page.locator('#fc-matcher-input').inputValue(),'');done('FC matcher: real status/dump syntax, unknown input, clear');
 await tool('mafialrs');await has('mafia-rx-count',/^222$/);await has('mafia-tx-count',/^106$/);await fill('mafia-search','RX profile 001');assert.ok(await page.locator('.mafia-target-row').count()>0);await page.locator('.mafia-target-row').first().click();await has('mafia-sel-detail',/Firmware: Unified_/);await page.locator('#mafia-copy-pid').click();assert.match(await page.evaluate(()=>navigator.clipboard.readText()),/^RX-CATALOG-/);
 await fill('mafia-search','zz-no-match');await has('mafia-target-list',/No targets/);await fill('mafia-search','');await page.locator('[data-type="tx"]').click();assert.equal(await page.locator('.mafia-target-row').count(),106);
 await page.locator('#mafia-mfr').selectOption('2.4 GHz');assert.ok(await page.locator('.mafia-target-row').count()>0);done('MafiaLRS: offline catalog, RX/TX, search, manufacturer, detail, copy');
 await tool('elrs-info');assert.equal(await page.locator('#tool-elrs-info a').count(),4);assert.equal(await page.locator('#tool-elrs-info a').nth(2).getAttribute('href'),'https://www.expresslrs.org/product-finder/');done('ELRS reference: content and repaired resource destinations');
 await tool('rf-terrain');
 // Published reference position: Eiffel Tower, WGS84. Old approximate converter missed by kilometres.
 await fill('rf-coord-input','31UDQ4825111932');await page.locator('#rf-coord-input').press('Enter');let center=await page.evaluate(()=>_rfMapRef.getCenter());assert.ok(Math.abs(center.lat-48.8582)<0.0001&&Math.abs(center.lng-2.2945)<0.0001);
 await fill('rf-coord-input',`33°51'24.0"S 151°12'54.0"E`);await page.locator('#rf-coord-input').press('Enter');center=await page.evaluate(()=>_rfMapRef.getCenter());assert.ok(Math.abs(center.lat+33.8566667)<0.00001);await has('rf-coord-display',/56H/);
 await fill('rf-coord-input','Eiffel Tower');assert.equal(geoCalls,0);await page.locator('#rf-coord-input').press('Enter');await page.waitForFunction(()=>document.getElementById('rf-coord-input').value.includes('Paris'));assert.equal(geoCalls,1);
 done('map search: precise MGRS/DMS and explicit address submission');
 async function points(coords,map='_rfMapRef'){await page.evaluate(({coords,map})=>coords.forEach(([lat,lng])=>window[map].fire('click',{latlng:L.latLng(lat,lng)})),{coords,map});}
 async function rfCalculate(expected=/Calculated/){await page.locator('#rf-calc-btn').click();await page.waitForFunction(re=>new RegExp(re).test(document.getElementById('rf-status').textContent),expected.source);}
 await page.evaluate(()=>{rfClearAllPoints();_rfMapRef.setView([39.5,-104.5],13);});await points([[39.5,-104.5],[39.51,-104.49]]);await rfCalculate();await has('rf-path-details',/3DEP/);await finite('rf-results');assert.ok(apiCalls>0);
 const pipeline=await page.evaluate(()=>{
  const a=_rfAnalyzePath([0,100,0],[0,500,1000],10,10,1000);
  const b=_rfLinkBudget(a,30,-100,2,2,1000,6);
  return {loss:a.diffLoss,blocked:!a.hasLOS,fspl:b.fLoss,margin:b.margin};
 });assert.equal(pipeline.blocked,true);assert.ok(pipeline.loss>35&&pipeline.loss<38);assert.ok(Math.abs(pipeline.fspl-92.44)<1e-9);assert.ok(Math.abs(pipeline.margin-(134-92.44-pipeline.loss-6))<1e-9);
 const reportDownload=page.waitForEvent('download');await page.locator('[onclick="rfExportReport()"]').click();const report=await reportDownload;await report.saveAs(path.join(artifacts,'terrain-report.html'));const reportHtml=fs.readFileSync(path.join(artifacts,'terrain-report.html'),'utf8');assert.match(reportHtml,/3DEP/);assert.doesNotMatch(reportHtml,/onclick=|onmouseover=|<script/);
 await page.locator('#rf-trace-slider').fill('100');await finite('rf-trace-detail');await page.locator('#rf-heatmap-toggle').check();await page.locator('#rf-heatmap-toggle').uncheck();
 for(const layer of ['dark','light','sat','topo']){await page.locator(`[onclick="rfSetLayer('${layer}')"]`).click();assert.equal(await page.locator(`[data-layer="${layer}"]`).getAttribute('aria-pressed'),'true');}
 done('terrain: API fixture, known diffraction/FSPL, report, trace, overlay, four layers');
 await fill('rf-tx-power',0);assert.equal(await text('rf-results'),'');await rfCalculate();await has('rf-budget',/TX \+0 dBm/);
 await page.locator('#rf-hw-advanced').check();await page.locator('#rf-air-radio').selectOption('ELRS_24_RX');await rfCalculate(/different operating frequencies/);await page.locator('#rf-hw-advanced').uncheck();
 elevation='invalid';await page.evaluate(()=>rfClearDEM());await rfCalculate(/Elevation unavailable/);assert.doesNotMatch(await text('rf-results'),/EXCELLENT|GOOD/);assert.equal(await page.locator('#rf-profile-svg path').count(),0);
 done('terrain: zero power, mismatched radios, invalid API data never becomes flat terrain');
 // Load an actual binary HGT through the file input; all network is unavailable.
 elevation='offline';const hgt=Buffer.alloc(1201*1201*2);for(let i=0;i<hgt.length;i+=2)hgt.writeInt16BE(1600,i);
 await page.locator('#tool-rf-terrain input[type=file]').setInputFiles({name:'N39W105.hgt',mimeType:'application/octet-stream',buffer:hgt});await page.waitForFunction(()=>document.getElementById('rf-dem-status').textContent.includes('1 file loaded'));
 const beforeCalls=apiCalls;await rfCalculate();await has('rf-path-details',/Local DEM/);assert.equal(apiCalls,beforeCalls);
 await page.locator('#tool-rf-terrain input[type=file]').setInputFiles({name:'broken.hgt',mimeType:'application/octet-stream',buffer:Buffer.from('broken')});await page.waitForFunction(()=>document.getElementById('rf-dem-status').textContent.includes('failed'));
 done('terrain: real HGT import works offline; malformed file rejected');
 // GeoTIFF uses the actual bundled parser and file picker, including CRS/nodata failures.
 async function tiffFile(projected=false,nodata=false){
  const bytes=await page.evaluate(({projected,nodata})=>Array.from(new Uint8Array(GeoTIFF.writeArrayBuffer(
   [nodata?-9999:1600,1600,1600,1600],{width:2,height:2,ModelPixelScale:[0.5,0.5,0],ModelTiepoint:[0,0,0,-105,40,0],GTModelTypeGeoKey:projected?1:2,GeographicTypeGeoKey:4326,...(projected?{ProjectedCSTypeGeoKey:32613}:{}),GTRasterTypeGeoKey:1,GeogAngularUnitsGeoKey:9102,GDAL_NODATA:'-9999'}))),{projected,nodata});
  return {name:'fixture.tif',mimeType:'image/tiff',buffer:Buffer.from(bytes)};
 }
 await page.evaluate(()=>rfClearDEM());await page.locator('#tool-rf-terrain input[type=file]').setInputFiles(await tiffFile());await page.waitForFunction(()=>document.getElementById('rf-dem-status').textContent.includes('1 file loaded'));await rfCalculate();await has('rf-path-details',/Local DEM/);
 await page.evaluate(()=>rfClearDEM());await page.locator('#tool-rf-terrain input[type=file]').setInputFiles(await tiffFile(true));await page.waitForFunction(()=>document.getElementById('rf-dem-status').textContent.includes('failed'));
 await page.locator('#tool-rf-terrain input[type=file]').setInputFiles({name:'N39W105.hgt',mimeType:'application/octet-stream',buffer:hgt});await page.waitForFunction(()=>document.getElementById('rf-dem-status').textContent.includes('1 file loaded'));
 done('terrain: real GeoTIFF import; projected CRS rejected');

 await page.locator('#rf-advanced-toggle').check();await points([[39.5,-104.5],[39.51,-104.49],[39.52,-104.48]]);await rfCalculate(/waypoints with terrain/);await has('rf-advanced-results',/PROPAGATION FROM PILOT/);await page.locator('#rf-advanced-results tr[onclick]').first().click();await finite('rf-path-details');
 await page.locator('[onclick="rfUndoWaypoint()"]').click();await rfCalculate(/waypoints with terrain/);
 await page.locator('[onclick="rfSetMode(\'repeater\')"]').click();await points([[39.5,-104.5],[39.51,-104.49],[39.52,-104.48]]);await rfCalculate(/relay computed/);await has('rf-advanced-results',/Via Repeater/);await finite('rf-advanced-results');
 await page.locator('[onclick="rfClearAllPoints()"]').click();assert.equal(await text('rf-results'),'');await rfCalculate(/Place TX and RX/);await page.locator('#rf-advanced-toggle').uncheck();
 done('terrain: multi-waypoint profiles, undo, repeater, clear');
 await tool('mesh-planner');await page.waitForFunction(()=>!!window._meshMapRef);await page.evaluate(()=>_meshMapRef.setView([39.5,-104.5],13));
 await page.locator('[onclick="meshCompute()"]').click();await has('mesh-health',/at least 2 active/);
 await points([[39.5,-104.5],[39.51,-104.49],[39.52,-104.48]],'_meshMapRef');await page.locator('[onclick="meshCompute()"]').click();await page.waitForFunction(()=>document.getElementById('mesh-link-count').textContent.includes('3 terrain'));await has('mesh-health',/FULLY CONNECTED/);
 // Capture a populated software scenario for the public manual before clearing it.
 await page.evaluate(()=>{_meshMapRef.fitBounds([[39.5,-104.5],[39.52,-104.48]],{padding:[55,55],animate:false});window.scrollTo(0,document.getElementById('mesh-map').getBoundingClientRect().top+scrollY-90);});
 await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
 await page.screenshot({path:path.join(artifacts,'mesh-plan-example.png')});
 const beforeMatrix=await text('mesh-matrix');await fill('mesh-tx-power',0);assert.equal(await text('mesh-matrix'),'');await page.locator('[onclick="meshCompute()"]').click();await page.waitForFunction(()=>document.getElementById('mesh-link-count').textContent.includes('connected'));assert.notEqual(await text('mesh-matrix'),beforeMatrix);
 await fill('mesh-tx-power',30);await fill('mesh-min-margin',0);await page.locator('[onclick="meshCompute()"]').click();await page.waitForFunction(()=>document.getElementById('mesh-link-count').textContent.includes('3 terrain'));
 const meshDownload=page.waitForEvent('download');await page.locator('[onclick="meshExport()"]').click();await(await meshDownload).saveAs(path.join(artifacts,'mesh-report.html'));
 await page.evaluate(()=>Object.values(_meshMapRef._layers).find(l=>l instanceof L.Polyline&&l.listens('click')).fire('click'));await page.waitForFunction(()=>document.getElementById('mesh-profile-svg').querySelector('path'));await page.locator('#mesh-trace-slider').fill('100');await finite('mesh-profile-detail');await finite('mesh-trace-info');
 await page.locator('#mesh-kill-mode').check();await page.evaluate(()=>Object.values(_meshMapRef._layers).filter(l=>l instanceof L.Marker)[1].fire('click'));await page.locator('[onclick="meshCompute()"]').click();await page.waitForFunction(()=>document.getElementById('mesh-link-count').textContent.includes('1 terrain'));await has('mesh-health',/2 active/);await page.evaluate(()=>Object.values(_meshMapRef._layers).filter(l=>l instanceof L.Marker)[1].fire('click'));await page.evaluate(()=>Object.values(_meshMapRef._layers).filter(l=>l instanceof L.Marker)[0].fire('click'));await page.locator('[onclick="meshCompute()"]').click();await page.waitForFunction(()=>document.getElementById('mesh-health').textContent.includes('GCS offline'));assert.doesNotMatch(await text('mesh-health'),/Hop Count from GCS/);await page.locator('#mesh-kill-mode').uncheck();
 await page.locator('#mesh-heatmap-toggle').check();await page.locator('#mesh-heatmap-toggle').uncheck();for(const layer of ['dark','light','sat','topo'])await page.locator(`[onclick="meshSetLayer('${layer}')"]`).click();
 await page.evaluate(()=>rfClearDEM());await page.locator('[onclick="meshCompute()"]').click();await page.waitForFunction(()=>document.getElementById('mesh-link-count').textContent.includes('terrain unknown'));await has('mesh-health',/TERRAIN INCOMPLETE/);assert.doesNotMatch(await text('mesh-health'),/FULLY CONNECTED/);
 await page.locator('[onclick="meshUndo()"]').click();await page.locator('[onclick="meshClear()"]').click();await has('mesh-node-count',/0 nodes/);
 done('mesh: links, power, zero margin, profile/trace, node failure, export, overlay, layers, offline unknown, undo/clear');
 // Exercise the same action adapters used by Android, plus permission failure.
 await page.evaluate(()=>{window.saved=[];window.copied=[];window.Android={saveReport:(name,html)=>saved.push({name,html}),copyText:text=>{copied.push(text);return true;}};});
 await tool('vtx-config');await page.locator('#vtx-copy-btn').click();assert.equal(await page.evaluate(()=>copied[0]),await text('vtx-cli-output'));
 await page.evaluate(()=>saveToolReport('test.html','<html><body><p>Report</p><button onclick="bad()">Bad</button></body></html>'));assert.equal(await page.evaluate(()=>saved.length),1);assert.doesNotMatch(await page.evaluate(()=>saved[0].html),/onclick|button/);
 await page.evaluate(()=>Android.copyText=()=>false);await page.locator('#vtx-copy-btn').click();await has('vtx-copy-btn',/Copy failed/);
 done('Android action adapters and clipboard failure feedback');
 for(const id of ['rf-terrain','mesh-planner','vtx-config','mafialrs']){await tool(id);await page.screenshot({path:path.join(artifacts,id+'-functional.png')});}
 assert.deepEqual(errors,[],'Uncaught browser errors');
 const result={toolPanels:13,checks:checks.length,checksPassed:checks,scriptErrors:errors};
 fs.writeFileSync(path.join(artifacts,'functional-results.json'),JSON.stringify(result,null,2));
 console.log(JSON.stringify(result));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
