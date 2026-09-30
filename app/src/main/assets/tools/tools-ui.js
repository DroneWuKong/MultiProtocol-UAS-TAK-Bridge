'use strict';
// One navigation owner for Android, phone and tablet layouts.
const toolCatalog = [
  ['rf-terrain','Terrain link','Ground-to-air path & clearance','RF planning','mountains',true],
  ['mesh-planner','Mesh planner','Nodes, coverage & link health','RF planning','graph',true],
  ['range','Link range','Estimate a radio link budget','RF planning','broadcast',false],
  ['fresnel','Fresnel clearance','Check space around a radio path','RF planning','arrows-out',false],
  ['harmonics','Harmonics','Find potential frequency conflicts','RF planning','wave-sine',false],
  ['dipole','Antenna length','Calculate a dipole cut length','RF planning','ruler',false],
  ['channel-planner','Channel planner','Separate pilot video channels','Video & channels','sliders',false],
  ['closest-channel','Closest channel','Match a frequency to a channel','Video & channels','crosshair',false],
  ['vtx-config','VTX configuration','Prepare Betaflight CLI settings','Video & channels','terminal-window',false],
  ['unlock-vtx','VTX table','Build a transmitter channel table','Video & channels','list',false],
  ['fc-matcher','Flight controller match','Identify a board from CLI output','Flight controllers','cpu',false],
  ['elrs-info','ExpressLRS reference','Packet rates & setup reference','Flight controllers','radio',false],
  ['mafialrs','MafiaLRS reference','Targets & firmware reference','Flight controllers','code',false]
];
window.showTool = function(id, focus = false) {
  const tool = toolCatalog.find(t => t[0] === id);
  if (!tool) return false;
  document.querySelectorAll('.tool-panel').forEach(panel => {
    const active = panel.id === 'tool-' + id;
    panel.classList.toggle('active', active);
    panel.setAttribute('aria-hidden', String(!active));
  });
  document.querySelectorAll('[data-tool]').forEach(button => button.setAttribute('aria-current', String(button.dataset.tool === id)));
  try { localStorage.setItem('tak-tool',id); history.replaceState(null,'','#'+id); } catch (_) {}
  document.title = tool[1] + ' · TAK Bridge';
  const dialog = document.getElementById('tool-library');
  if (dialog.open) dialog.close();
  window.scrollTo({top:0, behavior:'instant'});
  document.dispatchEvent(new CustomEvent('toolchange',{detail:id}));
  if (focus) document.querySelector('#tool-'+id+' .tool-title').focus({preventScroll:true});
  return true;
};
window.watchMapTiles = function(layer, prefix) {
  const status = document.getElementById(prefix+'-map-status');
  const map = document.getElementById(prefix+'-map');
  let loaded = 0, failed = 0;
  const update = () => {
    if (failed && !loaded) {
      status.textContent = 'Map tiles unavailable. Connect to the internet, then retry. Points can still be placed on the grid.';
      status.classList.add('offline');
      map.classList.add('tiles-unavailable');
    } else if (loaded) {
      status.textContent = prefix === 'rf' ? 'Tap once for ground, then again for aircraft. Drag either point to adjust.' : 'Tap the map to place a radio node.';
      status.classList.remove('offline');
      map.classList.remove('tiles-unavailable');
    }
    if ((failed && !loaded) && !status.querySelector('button')) {
      const retry=document.createElement('button');retry.textContent='Retry tiles';
      retry.addEventListener('click',()=>{loaded=0;failed=0;layer.redraw();});status.append(retry);
    }
  };
  layer.on('loading',()=>{loaded=0;failed=0;});
  layer.on('tileload',()=>{loaded++;update();});
  layer.on('tileerror',()=>{failed++;update();});
};
document.addEventListener('DOMContentLoaded',()=>{
  const dialog=document.getElementById('tool-library');
  const search=document.getElementById('tool-search');
  const list=document.getElementById('tool-list');
  for (const group of [...new Set(toolCatalog.map(t=>t[3]))]) {
    const section=document.createElement('section');section.className='tool-category';
    const heading=document.createElement('h3');heading.textContent=group;section.append(heading);
    for (const [id,name,description,category,icon,online] of toolCatalog.filter(t=>t[3]===group)) {
      const button=document.createElement('button');button.className='tool-choice';button.dataset.tool=id;
      button.dataset.search=(name+' '+description+' '+category).toLowerCase();
      // All content comes from the static catalog above.
      button.innerHTML='<i class="ph ph-'+icon+'" aria-hidden="true"></i><span><strong>'+name+'</strong><small>'+description+'</small></span><i class="ph ph-caret-right" aria-hidden="true"></i>';
      button.addEventListener('click',()=>showTool(id,true));section.append(button);
    }
    list.append(section);
  }
  document.getElementById('open-tools').addEventListener('click',()=>{dialog.showModal();search.value='';search.dispatchEvent(new Event('input'));search.focus();});
  document.getElementById('close-tools').addEventListener('click',()=>dialog.close());
  dialog.addEventListener('click',event=>{if(event.target===dialog){const r=dialog.getBoundingClientRect();if(event.clientX<r.left||event.clientX>r.right||event.clientY<r.top||event.clientY>r.bottom)dialog.close();}});
  search.addEventListener('input',()=>{
    let count=0;const query=search.value.trim().toLowerCase();
    list.querySelectorAll('.tool-choice').forEach(button=>{button.hidden=!button.dataset.search.includes(query);if(!button.hidden)count++;});
    list.querySelectorAll('.tool-category').forEach(section=>section.hidden=![...section.querySelectorAll('.tool-choice')].some(b=>!b.hidden));
    document.getElementById('empty-tools').hidden=count>0;
  });
  document.querySelectorAll('.tool-title').forEach(el=>{el.tabIndex=-1;el.setAttribute('role','heading');el.setAttribute('aria-level','1');});
  document.querySelectorAll('input,select,textarea').forEach(input=>{
    if (input.id && !document.querySelector('label[for="'+input.id+'"]') && !input.hasAttribute('aria-label')) {
      const label=input.closest('.input-group')?.querySelector('label');
      input.setAttribute('aria-label',label?.textContent?.trim()||input.getAttribute('placeholder')||input.id.replace(/-/g,' '));
    }
  });
  let saved='';try{saved=localStorage.getItem('tak-tool')||'';}catch(_){}
  if(!showTool(location.hash.slice(1)||saved))showTool('rf-terrain');
});
window.addEventListener('hashchange',()=>showTool(location.hash.slice(1)));
