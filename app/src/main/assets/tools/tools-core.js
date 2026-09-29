
// ── Global resize — invalidate all Leaflet maps when window resizes ──
(function(){
    var resizeTimer;
    window.addEventListener('resize',function(){
        clearTimeout(resizeTimer);
        resizeTimer=setTimeout(function(){
            if(window._rfMapRef)window._rfMapRef.invalidateSize();
            if(window._meshMapRef)window._meshMapRef.invalidateSize();
        },150);
    });
})();

// ── FPV Channel data ───────────────────────────────────────────────────────
const CHANNELS = {
    R: { name: 'Raceband', freqs: [5658, 5695, 5732, 5769, 5806, 5843, 5880, 5917] },
    F: { name: 'Fatshark', freqs: [5740, 5760, 5780, 5800, 5820, 5840, 5860, 5880] },
    E: { name: 'Boscam E', freqs: [5705, 5685, 5665, 5645, 5885, 5905, 5925, 5945] },
    A: { name: 'Boscam A', freqs: [5865, 5845, 5825, 5805, 5785, 5765, 5745, 5725] },
    B: { name: 'Boscam B', freqs: [5733, 5752, 5771, 5790, 5809, 5828, 5847, 5866] },
};
const PILOT_COLORS = ['#22d3ee','#a78bfa','#4ade80','#f97316','#f87171','#facc15'];

// ── TOOL 1: Channel Planner ───────────────────────────────────────────────
let pilots = [
    { band: 'R', ch: 1 },
    { band: 'R', ch: 7 },
];

function getBandFreqs(band) { return CHANNELS[band]?.freqs || CHANNELS.R.freqs; }

function renderPilots() {
    const list = document.getElementById('pilots-list');
    list.innerHTML = '';
    pilots.forEach((p, i) => {
        const freq = getBandFreqs(p.band)[p.ch - 1];
        const row = document.createElement('div');
        row.className = 'pilot-row';
        row.innerHTML = `
            <div class="pilot-color" style="background:${PILOT_COLORS[i % PILOT_COLORS.length]};"></div>
            <span class="pilot-label">Pilot ${i + 1}</span>
            <select class="tool-select" style="width:120px;" onchange="pilots[${i}].band=this.value; pilots[${i}].ch=1; renderPilots(); analyzeChannels();">
                ${Object.entries(CHANNELS).map(([k,v]) => `<option value="${k}" ${p.band===k?'selected':''}>${v.name}</option>`).join('')}
            </select>
            <select class="tool-select" style="width:90px;" onchange="pilots[${i}].ch=+this.value; renderPilots(); analyzeChannels();">
                ${getBandFreqs(p.band).map((f,ci) => `<option value="${ci+1}" ${p.ch===ci+1?'selected':''}>${p.band}${ci+1} ${f}</option>`).join('')}
            </select>
            <span class="pilot-freq" style="color:${PILOT_COLORS[i % PILOT_COLORS.length]};">${freq} MHz</span>
            ${pilots.length > 1 ? `<button onclick="pilots.splice(${i},1); renderPilots(); analyzeChannels();" style="background:none; border:none; color:var(--text-faint); cursor:pointer; font-size:16px; padding:0 4px;">×</button>` : ''}
        `;
        list.appendChild(row);
    });
    analyzeChannels();
}

function analyzeChannels() {
    if (pilots.length < 2) {
        document.getElementById('channel-results').innerHTML = '<p style="color:var(--text-muted); font-size:13px;">Add at least 2 pilots to see conflict analysis.</p>';
        return;
    }
    const freqs = pilots.map((p, i) => ({ pilot: i + 1, freq: getBandFreqs(p.band)[p.ch - 1], band: p.band, ch: p.ch }));
    let rows = '';
    let worst = 'ok';
    for (let i = 0; i < freqs.length; i++) {
        for (let j = i + 1; j < freqs.length; j++) {
            const diff = Math.abs(freqs[i].freq - freqs[j].freq);
            let status, dot, label;
            if (diff === 0) { status = 'bad'; dot = 'dot-bad'; label = 'SAME CHANNEL'; worst = 'bad'; }
            else if (diff < 40) { status = 'bad'; dot = 'dot-bad'; label = `${diff} MHz — CONFLICT`; if (worst !== 'bad') worst = 'bad'; }
            else if (diff < 80) { status = 'warn'; dot = 'dot-warn'; label = `${diff} MHz — marginal`; if (worst === 'ok') worst = 'warn'; }
            else { status = 'ok'; dot = 'dot-ok'; label = `${diff} MHz — clean`; }
            rows += `<tr><td>P${freqs[i].pilot} (${freqs[i].band}${freqs[i].ch} ${freqs[i].freq})</td><td>P${freqs[j].pilot} (${freqs[j].band}${freqs[j].ch} ${freqs[j].freq})</td><td><span class="conflict-dot ${dot}"></span>${label}</td></tr>`;
        }
    }
    const summary = worst === 'ok' ? '<span style="color:#4ade80">✓ All channels clear</span>' : worst === 'warn' ? '<span style="color:#eab308">⚠ Marginal separation — review</span>' : '<span style="color:#f87171">✗ Channel conflicts detected</span>';
    document.getElementById('channel-results').innerHTML = `
        <div style="margin-bottom:12px; font-size:14px; font-weight:600;">${summary}</div>
        <table class="channel-table"><thead><tr><th>Pilot A</th><th>Pilot B</th><th>Separation</th></tr></thead><tbody>${rows}</tbody></table>`;
}

document.getElementById('add-pilot-btn').addEventListener('click', () => {
    if (pilots.length >= 6) return;
    pilots.push({ band: 'R', ch: pilots.length + 1 > 8 ? 1 : pilots.length + 1 });
    renderPilots();
});
renderPilots();

// ── TOOL 2: Harmonics ─────────────────────────────────────────────────────
// ── Harmonics: Category presets ──
const HARM_PRESETS = {
    c2: [
        {label:'433 MHz (MafiaLRS)',f:433}, {label:'490 MHz (MafiaLRS)',f:490}, {label:'560 MHz (MafiaLRS)',f:560},
        {label:'735 MHz (MafiaLRS)',f:735}, {label:'868 MHz (ELRS EU)',f:868}, {label:'915 MHz (ELRS US)',f:915},
        {label:'2.4 GHz (ELRS)',f:2400}, {label:'Crossfire 868',f:868}, {label:'Crossfire 915',f:915},
    ],
    telem: [
        {label:'RFD900x (915)',f:915}, {label:'RFD868x',f:868}, {label:'SiK 433',f:433},
        {label:'MicroHard P900',f:900}, {label:'Doodle Labs 2.4G',f:2400}, {label:'Doodle Labs 1.6G',f:1625},
        {label:'Silvus 1.6G',f:1625}, {label:'Persistent 2.4G',f:2400},
    ],
    video: [
        {label:'5.8 GHz Analog/Digital',f:5800}, {label:'2.4 GHz (DJI O4)',f:2400},
        {label:'1.3 GHz Long Range',f:1300}, {label:'HDZero 5.8G',f:5800},
    ],
    mesh: [
        {label:'Doodle Labs 2.4G',f:2400}, {label:'Silvus 1625',f:1625},
        {label:'Persistent 2.4G',f:2400}, {label:'Rajant 2.4G',f:2400}, {label:'Rajant 5.8G',f:5800},
    ],
    other: [
        {label:'Custom...',f:0},
    ],
};
let harmCat='c2';
function setHarmCat(cat){
    harmCat=cat;
    document.querySelectorAll('.harm-cat-btn').forEach(b=>b.classList.toggle('active',b.dataset.cat===cat));
    const el=document.getElementById('harm-presets');
    el.innerHTML='';
    (HARM_PRESETS[cat]||[]).forEach(p=>{
        if(p.f===0)return;
        const btn=document.createElement('button');
        btn.textContent=p.label;
        btn.style.cssText='padding:4px 10px; border-radius:var(--radius-sm); border:1px solid var(--border-color); background:none; color:var(--text-muted); font-size:11px; cursor:pointer; font-family:var(--font-family);';
        btn.onclick=()=>{document.getElementById('harm-freq').value=p.f; calcHarmonics();};
        el.appendChild(btn);
    });
}

// GPS/GNSS bands
const GNSS_BANDS=[
    {name:'GPS L5',freq:1176.45,bw:24,color:'#06b6d4'},
    {name:'GPS L2',freq:1227.60,bw:24,color:'#3b82f6'},
    {name:'GPS L1',freq:1575.42,bw:24,color:'#ef4444'},
    {name:'Galileo E5a',freq:1176.45,bw:20,color:'#06b6d4'},
    {name:'Galileo E1',freq:1575.42,bw:24,color:'#ef4444'},
    {name:'GLONASS L1',freq:1602.0,bw:10,color:'#f59e0b'},
    {name:'BeiDou B1',freq:1561.098,bw:20,color:'#8b5cf6'},
];

function calcHarmonics() {
    const f = +document.getElementById('harm-freq').value;
    const txP = +document.getElementById('harm-txpower').value;
    const vStart = +document.getElementById('harm-vband-start').value;
    const vEnd = +document.getElementById('harm-vband-end').value;
    if (!f) return;

    // ── GPS/GNSS warnings ──
    let gpsHtml='';
    const maxN=Math.ceil(6000/f)+1;
    let gpsRisks=[];
    for(let n=2;n<=maxN&&n<=20;n++){
        const hf=n*f;
        GNSS_BANDS.forEach(band=>{
            const sep=Math.abs(hf-band.freq);
            if(sep<200){
                // Estimate harmonic power (rough: -10dBc per harmonic order for PA)
                const suppressionDb=n<=2?25:n<=3?30:n<=4?35:40;
                const harmPower=txP-suppressionDb;
                let risk='low';
                if(sep<30){risk=harmPower>-10?'critical':'high';}
                else if(sep<80){risk=harmPower>0?'high':'moderate';}
                else if(sep<150){risk=harmPower>10?'moderate':'low';}
                if(risk!=='low'||sep<100) gpsRisks.push({n,hf,band:band.name,sep:sep.toFixed(1),risk,harmPower:harmPower.toFixed(0),color:band.color});
            }
        });
    }
    // Deduplicate by band+harmonic
    const seen=new Set();
    gpsRisks=gpsRisks.filter(r=>{const k=r.n+'-'+r.band;if(seen.has(k))return false;seen.add(k);return true;});

    if(gpsRisks.length>0){
        gpsHtml='<div style="margin-bottom:12px;">';
        gpsHtml+='<div style="font-size:11px; font-weight:600; color:var(--text-main); margin-bottom:8px; text-transform:uppercase; letter-spacing:0.08em;">GPS / GNSS Interference Check</div>';
        gpsRisks.forEach(r=>{
            const riskColor=r.risk==='critical'?'#ef4444':r.risk==='high'?'#f87171':r.risk==='moderate'?'#eab308':'#4ade80';
            const riskBg=r.risk==='critical'?'rgba(239,68,68,0.1)':r.risk==='high'?'rgba(248,113,113,0.08)':r.risk==='moderate'?'rgba(234,179,8,0.08)':'rgba(74,222,128,0.05)';
            gpsHtml+=`<div style="display:flex; align-items:center; gap:8px; padding:6px 10px; margin-bottom:4px; border-radius:var(--radius-sm); background:${riskBg}; border-left:3px solid ${riskColor}; font-size:12px;">
                <span style="color:${r.color}; font-weight:600; min-width:60px;">${r.band}</span>
                <span style="color:var(--text-muted);">H${r.n} = ${r.hf.toLocaleString()} MHz</span>
                <span style="color:var(--text-muted);">Δ ${r.sep} MHz</span>
                <span style="color:var(--text-muted);">~${r.harmPower} dBm</span>
                <span style="margin-left:auto; color:${riskColor}; font-weight:700; text-transform:uppercase; font-size:10px;">${r.risk}</span>
            </div>`;
        });
        const hasCrit=gpsRisks.some(r=>r.risk==='critical'||r.risk==='high');
        if(hasCrit) gpsHtml+='<div style="padding:8px 10px; margin-top:4px; font-size:11px; color:#f87171; background:rgba(248,113,113,0.08); border-radius:var(--radius-sm);">⚠ Add output LPF above fundamental frequency. At '+txP+' dBm TX, GPS receiver desensing is likely.</div>';
        gpsHtml+='</div>';
    } else {
        gpsHtml='<div style="padding:8px 10px; margin-bottom:12px; font-size:12px; color:#4ade80; background:rgba(74,222,128,0.08); border-radius:var(--radius-sm);">✓ No GPS/GNSS harmonic risks detected at '+f+' MHz</div>';
    }
    document.getElementById('harm-gps-warnings').innerHTML=gpsHtml;

    // ── Video band conflicts (original logic) ──
    let html = '<div style="margin-bottom:8px; font-size:11px; font-weight:600; color:var(--text-main); text-transform:uppercase; letter-spacing:0.08em;">Video Band Conflicts ('+vStart+'–'+vEnd+' MHz)</div>';
    let hasConflict = false;
    const window = 20;
    for (let n = 2; n <= maxN; n++) {
        const hf = n * f;
        const inBand = hf >= vStart && hf <= vEnd;
        const nearBand = hf >= vStart - window && hf <= vEnd + window;
        if (!nearBand && hf > vEnd + 200) break;
        if (!nearBand) continue;
        const danger = inBand;
        const near = !inBand && nearBand;
        if (danger) hasConflict = true;
        const barWidth = Math.min(100, Math.max(5, 100 - Math.abs(hf - (vStart + vEnd) / 2) / 5));
        const barColor = danger ? '#f87171' : near ? '#eab308' : '#4ade80';
        const tag = danger ? '<span class="harmonic-tag tag-danger">IN BAND</span>' : near ? '<span class="harmonic-tag tag-warn">NEAR BAND</span>' : '<span class="harmonic-tag tag-clear">clear</span>';
        html += `<div class="harmonic-row">
            <span class="harmonic-n">H${n}</span>
            <span class="harmonic-freq">${hf.toLocaleString()} MHz</span>
            <div class="harmonic-bar-wrap"><div class="harmonic-bar" style="width:${barWidth}%; background:${barColor};"></div></div>
            ${tag}
        </div>`;
    }
    if (!html.includes('harmonic-row')) html += '<p style="color:var(--text-muted); font-size:13px;">No harmonics in video band at this frequency.</p>';
    const summary = hasConflict
        ? '<div style="margin-bottom:12px; padding:8px 12px; border-radius:var(--radius-sm); background:rgba(248,113,113,0.1); border:1px solid rgba(248,113,113,0.3); color:#f87171; font-size:12px; font-weight:600;"><i class="ph ph-warning"></i> Harmonics in video band — avoid affected channels</div>'
        : '<div style="margin-bottom:12px; padding:8px 12px; border-radius:var(--radius-sm); background:rgba(74,222,128,0.1); border:1px solid rgba(74,222,128,0.3); color:#4ade80; font-size:12px; font-weight:600;"><i class="ph ph-check-circle"></i> No video band conflicts</div>';
    document.getElementById('harm-results').innerHTML = summary + html;
}
function setHarmPreset(f) { document.getElementById('harm-freq').value = f; calcHarmonics(); }
['harm-freq','harm-txpower','harm-vband-start','harm-vband-end'].forEach(id => document.getElementById(id).addEventListener('input', calcHarmonics));
document.addEventListener('DOMContentLoaded',function(){setHarmCat('c2');});
calcHarmonics();

// ── TOOL 3: Range estimator ───────────────────────────────────────────────
function calcRange() {
    const pw_mw = +document.getElementById('range-power').value;
    const txGain = +document.getElementById('range-tx-gain').value;
    const rxGain = +document.getElementById('range-rx-gain').value;
    const freq_mhz = +document.getElementById('range-freq').value;
    const sens_dbm = +document.getElementById('range-sensitivity').value;
    const margin = +document.getElementById('range-margin').value;
    if (![pw_mw, freq_mhz, txGain, rxGain, sens_dbm, margin].every(Number.isFinite) || pw_mw <= 0 || freq_mhz <= 0) {
        document.getElementById('range-results').textContent = 'Enter positive power and frequency to calculate a range.';
        return;
    }
    // Input is milliwatts: 1 mW = 0 dBm; 1000 mW = 30 dBm.
    const pw_dbm = 10 * Math.log10(pw_mw);
    const eirp = pw_dbm + txGain;
    const linkBudget = eirp + rxGain - sens_dbm - margin;
    const fspl_needed = linkBudget;
    // FSPL(dB) = 32.44 + 20 log10(MHz) + 20 log10(km).
    const range_km = Math.pow(10, (fspl_needed - 20 * Math.log10(freq_mhz) - 32.44) / 20);
    const range_m = Math.round(range_km * 1000);
    const range_display = range_km >= 1 ? range_km.toFixed(1) + ' km' : range_m + ' m';
    document.getElementById('range-results').innerHTML = `
        <div class="result-grid">
            <div class="result-item"><span class="result-value ${range_km > 10 ? 'ok' : range_km > 2 ? '' : 'warn'}">${range_display}</span><span class="result-label">Free-space limit</span></div>
            <div class="result-item"><span class="result-value sm">${pw_dbm.toFixed(1)} dBm</span><span class="result-label">TX power</span></div>
            <div class="result-item"><span class="result-value sm">${eirp.toFixed(1)} dBm</span><span class="result-label">EIRP</span></div>
            <div class="result-item"><span class="result-value sm">${linkBudget.toFixed(1)} dB</span><span class="result-label">Link budget</span></div>
        </div>
        <div class="result-note">Power-and-sensitivity limit in free space. Terrain, the horizon, interference and cable losses can shorten usable range. ${margin} dB safety margin applied.</div>`;
}
const rangePresets = {
    elrs900:  { power:100,  txGain:2,  rxGain:2,  freq:915,  sens:-112, margin:10 },
    elrs24:   { power:250,  txGain:2,  rxGain:2,  freq:2400, sens:-105, margin:10 },
    mafia433: { power:1000, txGain:2,  rxGain:2,  freq:433,  sens:-112, margin:10 },
    vtx58:    { power:600,  txGain:2,  rxGain:2,  freq:5800, sens:-85,  margin:6  },
    vtx13:    { power:1000, txGain:3,  rxGain:3,  freq:1280, sens:-88,  margin:6  },
};
function setRangePreset(k) {
    const p = rangePresets[k];
    document.getElementById('range-power').value = p.power;
    document.getElementById('range-tx-gain').value = p.txGain;
    document.getElementById('range-rx-gain').value = p.rxGain;
    document.getElementById('range-freq').value = p.freq;
    document.getElementById('range-sensitivity').value = p.sens;
    document.getElementById('range-margin').value = p.margin;
    calcRange();
}
['range-power','range-tx-gain','range-rx-gain','range-freq','range-sensitivity','range-margin'].forEach(id => document.getElementById(id).addEventListener('input', calcRange));
calcRange();

// ── TOOL 4: Fresnel zone ──────────────────────────────────────────────────
function calcFresnel() {
    const d = +document.getElementById('fres-dist').value;
    const f_mhz = +document.getElementById('fres-freq').value;
    const pct = +document.getElementById('fres-point').value / 100;
    if (!d || !f_mhz) return;
    const c = 3e8;
    const lambda = c / (f_mhz * 1e6);
    const d1 = d * pct, d2 = d * (1 - pct);
    const r1 = Math.sqrt(lambda * d1 * d2 / d);
    const r60 = r1 * 0.6;
    const r1_display = r1 >= 1 ? r1.toFixed(1) + ' m' : (r1 * 100).toFixed(0) + ' cm';
    const r60_display = r60 >= 1 ? r60.toFixed(1) + ' m' : (r60 * 100).toFixed(0) + ' cm';
    document.getElementById('fresnel-results').innerHTML = `
        <div class="result-grid">
            <div class="result-item"><span class="result-value">${r1_display}</span><span class="result-label">Zone 1 radius at ${Math.round(pct*100)}%</span></div>
            <div class="result-item"><span class="result-value ok">${r60_display}</span><span class="result-label">60% clearance needed</span></div>
            <div class="result-item"><span class="result-value sm">${(lambda*100).toFixed(1)} cm</span><span class="result-label">Wavelength</span></div>
        </div>`;
    drawFresnel(d, r1, pct);
}
function drawFresnel(d, r1, pct) {
    const svg = document.getElementById('fresnel-svg');
    const W = 700, H = 120, pad = 40;
    const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
    const col = isDark ? '#22d3ee' : '#0e7490';
    const textCol = isDark ? '#737373' : '#64748b';
    const scale = (W - pad * 2) / d;
    const rScale = Math.min(50, r1 * scale * 2);
    const cx = W / 2, cy = H / 2;
    const xPt = pad + d * pct * scale;
    svg.innerHTML = `
        <line x1="${pad}" y1="${cy}" x2="${W-pad}" y2="${cy}" stroke="${col}" stroke-width="1.5"/>
        <ellipse cx="${cx}" cy="${cy}" rx="${(W-pad*2)/2}" ry="${rScale}" stroke="${col}" stroke-width="1" fill="none" stroke-dasharray="4,3" opacity="0.5"/>
        <ellipse cx="${cx}" cy="${cy}" rx="${(W-pad*2)/2}" ry="${rScale*0.6}" stroke="${col}" stroke-width="1.5" fill="${col}" fill-opacity="0.06"/>
        <circle cx="${pad}" cy="${cy}" r="5" fill="${col}"/>
        <circle cx="${W-pad}" cy="${cy}" r="5" fill="${col}"/>
        <line x1="${xPt}" y1="${cy - rScale}" x2="${xPt}" y2="${cy + rScale}" stroke="#f87171" stroke-width="1.5" stroke-dasharray="3,2"/>
        <text x="${pad}" y="${cy+24}" font-size="11" fill="${textCol}" font-family="monospace">TX</text>
        <text x="${W-pad-4}" y="${cy+24}" font-size="11" fill="${textCol}" font-family="monospace">RX</text>
        <text x="${xPt-12}" y="${cy-rScale-6}" font-size="10" fill="#f87171" font-family="monospace">${Math.round(pct*100)}%</text>`;
}
['fres-dist','fres-freq','fres-point'].forEach(id => document.getElementById(id).addEventListener('input', calcFresnel));
calcFresnel();

// ── TOOL 5: Dipole length ─────────────────────────────────────────────────
function calcDipole() {
    const f = +document.getElementById('dip-freq').value;
    const vf = +document.getElementById('dip-vf').value;
    const unit = document.getElementById('dip-unit').value;
    if (!f) return;
    const c = 3e8;
    const lambda = (c / (f * 1e6)) * vf;
    const convert = unit === 'mm' ? 1000 : unit === 'cm' ? 100 : 39.3701;
    const suffix = unit;
    const q = lambda / 4 * convert;
    const h = lambda / 2 * convert;
    const fiveEighths = lambda * 5 / 8 * convert;
    const fmt = v => v >= 10 ? v.toFixed(1) : v.toFixed(2);
    document.getElementById('dipole-results').innerHTML = `
        <div class="result-grid">
            <div class="result-item"><span class="result-value">${fmt(q)} ${suffix}</span><span class="result-label">Quarter-wave (λ/4)</span></div>
            <div class="result-item"><span class="result-value">${fmt(h)} ${suffix}</span><span class="result-label">Half-wave (λ/2)</span></div>
            <div class="result-item"><span class="result-value sm">${fmt(fiveEighths)} ${suffix}</span><span class="result-label">5/8 wave</span></div>
            <div class="result-item"><span class="result-value sm">${(lambda * convert).toFixed(1)} ${suffix}</span><span class="result-label">Full wavelength</span></div>
        </div>
        <div class="result-note">At ${f} MHz with velocity factor ${vf}. Quarter-wave = standard whip / monopole element. Half-wave = dipole total length (two × λ/4 elements). Cut slightly long and trim for best SWR.</div>`;
}
function setDipPreset(f) { document.getElementById('dip-freq').value = f; calcDipole(); }
['dip-freq','dip-vf','dip-unit'].forEach(id => document.getElementById(id).addEventListener('input', calcDipole));
calcDipole();

// ── TOOL 6: Closest channel ───────────────────────────────────────────────
function calcClosestChannel() {
    const target = +document.getElementById('cc-freq').value;
    const band = document.getElementById('cc-band').value;
    if (!target) return;
    const bands = band === 'all' ? Object.entries(CHANNELS) : [[band, CHANNELS[band]]];
    let best = null, bestDiff = Infinity;
    let allRows = '';
    bands.forEach(([k, b]) => {
        b.freqs.forEach((f, i) => {
            const diff = Math.abs(f - target);
            if (diff < bestDiff) { bestDiff = diff; best = { band: k, ch: i + 1, freq: f }; }
            const status = diff === 0 ? '<span style="color:#4ade80">exact</span>' : diff <= 10 ? `<span style="color:#4ade80">+${diff} MHz</span>` : diff <= 30 ? `<span style="color:#eab308">+${diff} MHz</span>` : `<span style="color:var(--text-muted)">${diff} MHz</span>`;
            allRows += `<tr><td>${k}${i+1}</td><td>${b.name}</td><td style="font-family:var(--font-family)">${f}</td><td>${status}</td></tr>`;
        });
    });
    document.getElementById('cc-results').innerHTML = best ? `
        <div class="result-grid">
            <div class="result-item"><span class="result-value">${best.band}${best.ch}</span><span class="result-label">Closest channel</span></div>
            <div class="result-item"><span class="result-value">${best.freq} MHz</span><span class="result-label">Channel frequency</span></div>
            <div class="result-item"><span class="result-value sm ${bestDiff === 0 ? 'ok' : bestDiff <= 20 ? '' : 'warn'}">${bestDiff === 0 ? 'Exact' : '±' + bestDiff + ' MHz'}</span><span class="result-label">Offset</span></div>
        </div>` : '';
    document.getElementById('cc-table').innerHTML = `<table class="channel-table"><thead><tr><th>Channel</th><th>Band</th><th>Freq (MHz)</th><th>Distance</th></tr></thead><tbody>${allRows}</tbody></table>`;
}
['cc-freq','cc-band'].forEach(id => document.getElementById(id).addEventListener('input', calcClosestChannel));
calcClosestChannel();

// ── TOOL 7: VTX Config Generator ─────────────────────────────────────────
const VTX_BANDS_FULL = [
    { name:'BOSCAM_A', letter:'A', flag:'FACTORY', freqs:[5865,5845,5825,5805,5785,5765,5745,5725] },
    { name:'BOSCAM_B', letter:'B', flag:'FACTORY', freqs:[5733,5752,5771,5790,5809,5828,5847,5866] },
    { name:'BOSCAM_E', letter:'E', flag:'FACTORY', freqs:[5705,5685,5665,5645,5885,5905,5925,5945] },
    { name:'FATSHARK', letter:'F', flag:'FACTORY', freqs:[5740,5760,5780,5800,5820,5840,5860,5880] },
    { name:'RACEBAND', letter:'R', flag:'FACTORY', freqs:[5658,5695,5732,5769,5806,5843,5880,5917] },
];

// VTX database from vtx.in.ua catalog (subset with known power configs)
const VTX_DB = [
    { mfr:'Rush', name:'Rush Tank Solo 1.6W', proto:2048, pw:[1,3,4], port:0, bands:5, channels:8 },
    { mfr:'Rush', name:'Rush Max Solo 2.5W', proto:2048, pw:[1,3,4], port:0, bands:5, channels:8 },
    { mfr:'Rush', name:'Rush Max Solo 2.5W (+ X-band)', proto:2048, pw:[1,3,4], port:0, bands:6, channels:8 },
    { mfr:'Rush', name:'Rush 3.3G 2W VTX', proto:8192, pw:[1,3,4], port:0, bands:2, channels:8 },
    { mfr:'Rush', name:'Rush 3.3G 4W VTX', proto:8192, pw:[1,3,4], port:0, bands:2, channels:8 },
    { mfr:'Rush', name:'Rush 1.2/1.3GHz 4W', proto:8192, pw:[1,2,4], port:0, bands:1, channels:9 },
    { mfr:'Foxeer', name:'Foxeer Reaper Extreme 1.8W 72Ch', proto:8192, pw:[1,3,5], port:0, bands:5, channels:8 },
    { mfr:'Foxeer', name:'Foxeer Reaper Extreme 2.5W v2 72Ch', proto:8192, pw:[1,3,5], port:0, bands:5, channels:8 },
    { mfr:'Foxeer', name:'Foxeer Reaper Extreme 3W 72Ch', proto:8192, pw:[1,3,5], port:0, bands:5, channels:8 },
    { mfr:'Foxeer', name:'Foxeer Reaper 5G 4W 80Ch (X-band)', proto:8192, pw:[1,3,5], port:0, bands:6, channels:8 },
    { mfr:'AKK', name:'AKK FX3-ultimate 3W', proto:8192, pw:[1,2,3,4,5], port:0, bands:5, channels:8 },
    { mfr:'AKK', name:'AKK FX5 4W 96Ch', proto:8192, pw:[1,2,3,4,5], port:0, bands:5, channels:8 },
    { mfr:'GEPRC', name:'GEPRC TX800 VTX 800mW', proto:8192, pw:[1,2,3,4,5], port:0, bands:5, channels:8 },
    { mfr:'GEPRC', name:'GEPRC TX1000 VTX 1W', proto:8192, pw:[1,2,3,4,5], port:0, bands:5, channels:8 },
    { mfr:'TBS', name:'TBS Unify Pro32 HV', proto:2048, pw:[1,2,3,4,5], port:0, bands:5, channels:8 },
    { mfr:'TBS', name:'TBS Unify Pro HV', proto:2048, pw:[1,2,3,4,5], port:0, bands:5, channels:8 },
    { mfr:'SpeedyBee', name:'SpeedyBee TX800 5.8G', proto:8192, pw:[1,2,3,4], port:0, bands:5, channels:8 },
    { mfr:'ImmersionRC', name:'ImmersionRC Tramp HV', proto:8192, pw:[1,2,3,4,5], port:0, bands:5, channels:8 },
    { mfr:'Stingbee', name:'StingBee Stellar VTX 2.5W 64Ch', proto:8192, pw:[1,2,4], port:0, bands:4, channels:8 },
    { mfr:'DEC1', name:'DEC1 Spot VTX 2.5W', proto:8192, pw:[2,3,5], port:0, bands:5, channels:8 },
    { mfr:'FT', name:'FT VTX 5.8GHz 4W 96CH', proto:8192, pw:[1,2,3,4,5], port:0, bands:5, channels:8 },
    { mfr:'FT', name:'FT VTX 3.3GHz 4W 16CH', proto:8192, pw:[1,2,4], port:0, bands:2, channels:8 },
    { mfr:'FT', name:'FT VTX 1.2GHz 4W 9CH', proto:8192, pw:[1,2,4], port:0, bands:1, channels:9 },
    { mfr:'KaraFPV', name:'KaraFPV Teleport67 4W 6.1-7.2G', proto:8192, pw:[1,3,5], port:0, bands:2, channels:8 },
    { mfr:'Pilotix', name:'Pilotix VTX 2.5W 64Ch', proto:8192, pw:[1,2,4], port:0, bands:4, channels:8 },
    { mfr:'Scream Industries', name:'Scream Industries SI-MOD v1.0', proto:8192, pw:[1,3,5], port:0, bands:5, channels:8 },
    { mfr:'Axisflying', name:'Axisflying Thor VTX 2.5W', proto:8192, pw:[1,2,3,4,5], port:0, bands:5, channels:8 },
    { mfr:'SKYZONE', name:'SKYZONE VTX 2.5W 56Ch', proto:8192, pw:[1,2,4], port:0, bands:4, channels:8 },
    { mfr:'HGLRC', name:'HGLRC Zeus VTX 3W', proto:8192, pw:[1,2,3,4,5], port:0, bands:5, channels:8 },
    { mfr:'iFlight', name:'iFlight BLITZ WHOOP VTX 400mW', proto:8192, pw:[1,2,3,4], port:0, bands:5, channels:8 },
];

function populateVtxMfr() {
    const sel = document.getElementById('vtx-mfr');
    const mfrs = [...new Set(VTX_DB.map(v => v.mfr))].sort();
    sel.innerHTML = mfrs.map(m => `<option value="${m}">${m}</option>`).join('');
    updateVtxModels();
}
function updateVtxModels() {
    const mfr = document.getElementById('vtx-mfr').value;
    const sel = document.getElementById('vtx-model');
    sel.innerHTML = VTX_DB.filter(v => v.mfr === mfr).map(v => `<option value="${v.name}">${v.name}</option>`).join('');
    updateVtxConfig();
}
function updateVtxConfig() {
    const name = document.getElementById('vtx-model').value;
    const uart = document.getElementById('vtx-uart').value;
    const vtx = VTX_DB.find(v => v.name === name);
    if (!vtx) return;
    const protoName = vtx.proto === 8192 ? 'IRC Tramp' : 'SmartAudio';
    const numBands = Math.min(vtx.bands, 5);
    const bands = VTX_BANDS_FULL.slice(0, numBands);
    const pwCount = vtx.pw.length;
    const pwLabels = vtx.pw.map(p => p === 1?'25':p===2?'100':p===3?'200':p===4?'400':'600');
    let cli = `# VTX: ${vtx.name} (${protoName})\n`;
    cli += `# Generated by Forge Tools\n\n`;
    cli += `set vtx_softserial_alt = OFF\n`;
    cli += `feature -SOFTSERIAL\n`;
    cli += `resource UART${uart}_TX 1 NONE\n`;
    cli += `resource UART${uart}_RX 1 NONE\n\n`;
    cli += `set vtxtable_bands = ${numBands}\n`;
    cli += `set vtxtable_channels = 8\n`;
    bands.forEach((b, i) => {
        cli += `vtxtable band ${i+1} ${b.name} ${b.letter} ${b.flag} ${b.freqs.join(' ')}\n`;
    });
    cli += `vtxtable powerlevels ${pwCount}\n`;
    cli += `vtxtable powervalues ${vtx.pw.join(' ')}\n`;
    cli += `vtxtable powerlabels ${pwLabels.join(' ')}\n\n`;
    cli += `set vtx_band = 5\nset vtx_channel = 1\nset vtx_power = 1\n`;
    cli += `set vtx_low_power_disarm = ON\n\nsave`;
    document.getElementById('vtx-cli-output').textContent = cli;
}
function copyVtxConfig() {
    const text = document.getElementById('vtx-cli-output').textContent;
    navigator.clipboard.writeText(text).then(() => {
        const btn = document.getElementById('vtx-copy-btn');
        btn.innerHTML = '<i class="ph ph-check"></i> Copied!';
        btn.style.color = '#4ade80';
        setTimeout(() => { btn.innerHTML = '<i class="ph ph-copy"></i> Copy'; btn.style.color = ''; }, 2000);
    });
}
['vtx-uart','vtx-aux'].forEach(id => document.getElementById(id).addEventListener('change', updateVtxConfig));
populateVtxMfr();

// ── FC Target Matcher ─────────────────────────────────────────────────────
const FC_DB = [
    { target:'BETAFLIGHTF4', mcu:'STM32F405', gyros:['MPU6000'], manufacturers:['Betaflight','Various'] },
    { target:'OMNIBUSF4SD', mcu:'STM32F405', gyros:['MPU6000'], manufacturers:['Airbot','Omnibus'] },
    { target:'OMNIBUSF7', mcu:'STM32F745', gyros:['MPU6000'], manufacturers:['Airbot'] },
    { target:'SPRACINGF3', mcu:'STM32F303', gyros:['MPU6500'], manufacturers:['SP Racing'] },
    { target:'SPRACINGF7DUAL', mcu:'STM32F745', gyros:['MPU6000','ICM20689'], manufacturers:['SP Racing'] },
    { target:'MATEKF405', mcu:'STM32F405', gyros:['ICM20602','MPU6000'], manufacturers:['Matek'] },
    { target:'MATEKF411', mcu:'STM32F411', gyros:['ICM20602'], manufacturers:['Matek'] },
    { target:'MATEKF722', mcu:'STM32F722', gyros:['MPU6000','ICM20602'], manufacturers:['Matek'] },
    { target:'MATEKF745', mcu:'STM32F745', gyros:['MPU6000'], manufacturers:['Matek'] },
    { target:'MATEKH743', mcu:'STM32H743', gyros:['ICM42688P','MPU6000'], manufacturers:['Matek'] },
    { target:'IFLIGHT_BLITZ_F7_AIO', mcu:'STM32F745', gyros:['BMI270'], manufacturers:['iFlight'] },
    { target:'IFLIGHT_BLITZ_H7', mcu:'STM32H743', gyros:['ICM42688P'], manufacturers:['iFlight'] },
    { target:'IFLIGHT_BLITZ_ATF435', mcu:'AT32F435', gyros:['ICM42688P'], manufacturers:['iFlight'] },
    { target:'GEPRC_F405', mcu:'STM32F405', gyros:['MPU6000','ICM42688P'], manufacturers:['GEPRC'] },
    { target:'GEPRC_F722', mcu:'STM32F722', gyros:['MPU6000','ICM42688P'], manufacturers:['GEPRC'] },
    { target:'GEPRC_TAKER_H743', mcu:'STM32H743', gyros:['MPU6000','ICM42688'], manufacturers:['GEPRC'] },
    { target:'SPEEDYBEEF405', mcu:'STM32F405', gyros:['ICM42688P','MPU6000'], manufacturers:['SpeedyBee'] },
    { target:'SPEEDYBEEF7', mcu:'STM32F722', gyros:['MPU6000'], manufacturers:['SpeedyBee'] },
    { target:'FLYWOOF745', mcu:'STM32F745', gyros:['ICM42688P'], manufacturers:['Flywoo'] },
    { target:'FLYWOOF722', mcu:'STM32F722', gyros:['ICM42688P','MPU6000'], manufacturers:['Flywoo'] },
    { target:'FOXEERF405V3', mcu:'STM32F405', gyros:['ICM42688P'], manufacturers:['Foxeer'] },
    { target:'NEUTRONRCF435', mcu:'AT32F435', gyros:['ICM42688P'], manufacturers:['NeutronRC'] },
    { target:'SKYSTARSF405', mcu:'STM32F405', gyros:['MPU6000'], manufacturers:['Skystars'] },
    { target:'TMOTORF7', mcu:'STM32F722', gyros:['ICM20689','MPU6000'], manufacturers:['T-Motor'] },
    { target:'TMOTORF405', mcu:'STM32F405', gyros:['MPU6000'], manufacturers:['T-Motor'] },
    { target:'KAKUTEF7', mcu:'STM32F745', gyros:['ICM20689'], manufacturers:['Holybro'] },
    { target:'KAKUTEH7', mcu:'STM32H743', gyros:['ICM42688P','MPU6000'], manufacturers:['Holybro'] },
    { target:'LUXH743HD', mcu:'STM32H743', gyros:['ICM42688P'], manufacturers:['Lumenier'] },
    { target:'MICOAIRF405', mcu:'STM32F405', gyros:['BMI270','ICM42688P'], manufacturers:['MicoAir'] },
    { target:'MICOAIRH743', mcu:'STM32H743', gyros:['BMI270','BMI088'], manufacturers:['MicoAir'] },
    { target:'TBSLUCIDH7', mcu:'STM32H743', gyros:['ICM42688P','MPU6000'], manufacturers:['TBS'] },
    { target:'TBSLUCIDPRO', mcu:'STM32H743', gyros:['ICM42688P'], manufacturers:['TBS'] },
    { target:'HGLRCF428', mcu:'STM32F405', gyros:['MPU6000','ICM42688P'], manufacturers:['HGLRC'] },
    { target:'HGLRCH743', mcu:'STM32H743', gyros:['ICM42688P'], manufacturers:['HGLRC'] },
    { target:'IFLIGHT_BLITZ_F722', mcu:'STM32F722', gyros:['BMI270'], manufacturers:['iFlight'] },
];

function fcMatcherPlaceholder() {
    return `<div style="height:100%; display:flex; align-items:center; justify-content:center; flex-direction:column; gap:8px; color:var(--text-faint); text-align:center; padding:32px;">
        <i class="ph ph-magnifying-glass" style="font-size:36px; opacity:0.3;"></i>
        <div style="font-size:13px;">Paste CLI output and click Identify FC</div>
    </div>`;
}

function runFcMatcher() {
    const raw = document.getElementById('fc-matcher-input').value.trim();
    const out = document.getElementById('fc-matcher-result');
    if (!raw) { out.innerHTML = '<div style="color:var(--text-faint); font-size:13px; padding:16px;">No input detected.</div>'; return; }

    // Parse key fields from status output
    const mcuMatch = raw.match(/MCU\s+([A-Z0-9]+\s*[A-Z0-9]+)/i) || raw.match(/STM32\w+/i) || raw.match(/AT32\w+/i) || raw.match(/GD32\w+/i);
    const gyroMatch = raw.match(/Gyro\s*[:#]?\s*([A-Za-z0-9]+)/i) || raw.match(/(MPU6000|MPU6500|ICM20602|ICM20689|ICM42688|ICM42688P|BMI270|BMI088|LSM6DSO)/i);
    const targetMatch = raw.match(/target:\s*([A-Z0-9_]+)/i);
    const boardMatch = raw.match(/board:\s*([A-Z0-9_]+)/i);
    const cpuMatch = raw.match(/cpu:\s*([A-Za-z0-9]+)/i);

    const detectedMcu = (mcuMatch?.[0] || cpuMatch?.[1] || '').toUpperCase().replace(/\s+/g,'');
    const detectedGyro = (gyroMatch?.[1] || '').toUpperCase();
    const detectedTarget = targetMatch?.[1] || boardMatch?.[1] || '';

    // Score matches
    let matches = FC_DB.map(fc => {
        let score = 0;
        const mcuNorm = detectedMcu.replace('STM32','').replace('AT32','');
        if (detectedTarget && fc.target.toUpperCase().includes(detectedTarget.toUpperCase())) score += 10;
        if (detectedMcu && fc.mcu.replace('STM32','').replace('AT32','') === mcuNorm) score += 5;
        if (detectedGyro && fc.gyros.some(g => g.toUpperCase() === detectedGyro)) score += 4;
        if (detectedMcu && fc.mcu.toUpperCase() === detectedMcu) score += 3;
        return { ...fc, score };
    }).filter(fc => fc.score > 0).sort((a,b) => b.score - a.score).slice(0, 5);

    let html = '';
    if (detectedTarget) {
        html += `<div style="background:rgba(34,211,238,0.06); border:1px solid rgba(34,211,238,0.2); border-radius:var(--radius-sm); padding:12px 14px; margin-bottom:14px; font-size:12px;">
            <div style="color:var(--text-faint); font-size:10px; text-transform:uppercase; letter-spacing:0.06em; margin-bottom:4px;">Target found in output</div>
            <div style="font-family:'JetBrains Mono',monospace; color:var(--accent-red); font-size:16px;">${detectedTarget}</div>
        </div>`;
    }

    if (detectedMcu || detectedGyro) {
        html += `<div style="display:flex; gap:10px; margin-bottom:14px;">`;
        if (detectedMcu) html += `<div style="flex:1; background:var(--bg-dark); border:1px solid var(--border-color); border-radius:var(--radius-sm); padding:10px 12px;"><div style="font-size:10px; text-transform:uppercase; color:var(--text-faint); margin-bottom:3px;">MCU detected</div><div style="font-size:13px; font-family:'JetBrains Mono',monospace;">${detectedMcu}</div></div>`;
        if (detectedGyro) html += `<div style="flex:1; background:var(--bg-dark); border:1px solid var(--border-color); border-radius:var(--radius-sm); padding:10px 12px;"><div style="font-size:10px; text-transform:uppercase; color:var(--text-faint); margin-bottom:3px;">Gyro detected</div><div style="font-size:13px; font-family:'JetBrains Mono',monospace;">${detectedGyro}</div></div>`;
        html += `</div>`;
    }

    if (matches.length > 0) {
        html += `<div style="font-size:10px; text-transform:uppercase; letter-spacing:0.06em; color:var(--text-faint); margin-bottom:8px;">Possible targets (by confidence)</div>`;
        matches.forEach((m, i) => {
            const conf = i === 0 ? '#4ade80' : i === 1 ? '#eab308' : 'var(--text-muted)';
            html += `<div style="display:flex; align-items:center; gap:10px; padding:9px 12px; background:var(--bg-dark); border:1px solid var(--border-color); border-radius:var(--radius-sm); margin-bottom:6px;">
                <div style="width:6px; height:6px; border-radius:50%; background:${conf}; flex-shrink:0;"></div>
                <div style="flex:1;">
                    <div style="font-family:'JetBrains Mono',monospace; font-size:13px; color:var(--text-main);">${m.target}</div>
                    <div style="font-size:11px; color:var(--text-muted); margin-top:2px;">${m.mcu} · ${m.gyros.join(' / ')} · ${m.manufacturers.join(', ')}</div>
                </div>
                <div style="font-size:10px; color:var(--text-faint);">score ${m.score}</div>
            </div>`;
        });
    } else if (!detectedTarget) {
        html += `<div style="font-size:13px; color:var(--text-muted); padding:16px 0;">Could not identify target from this output. Try pasting the full <code style="background:var(--bg-dark); padding:1px 5px; border-radius:3px;">status</code> output including the board/target lines.</div>`;
    }

    out.innerHTML = html || fcMatcherPlaceholder();
}
document.getElementById('fc-matcher-result').innerHTML = fcMatcherPlaceholder();

// ── VTX Unlock Table ──────────────────────────────────────────────────────
const VTX_UNLOCK_BANDS = {
    R: { name:'RACEBAND', freqs:[5658,5695,5732,5769,5806,5843,5880,5917] },
    F: { name:'FATSHARK', freqs:[5740,5760,5780,5800,5820,5840,5860,5880] },
    E: { name:'BOSCAM_E', freqs:[5705,5685,5665,5645,5885,5905,5925,5945] },
    A: { name:'BOSCAM_A', freqs:[5865,5845,5825,5805,5785,5765,5745,5725] },
    B: { name:'BOSCAM_B', freqs:[5733,5752,5771,5790,5809,5828,5847,5866] },
};
const VTX_POWER_LEVELS = [
    { mw: 25,  label: '25' },
    { mw: 200, label: '200' },
    { mw: 500, label: '500' },
    { mw: 800, label: '800' },
];

function renderVtxTable() {
    const proto = document.getElementById('vtx-protocol').value;
    const maxPwr = +document.getElementById('vtx-maxpower').value;
    const activeBands = Object.keys(VTX_UNLOCK_BANDS).filter(k => document.getElementById('vtx-band-' + k)?.checked);

    const powers = VTX_POWER_LEVELS.slice(0, maxPwr);
    const protoNum = proto === 'smartaudio' ? 3 : 4; // SA 2.1 = 3, Tramp = 4

    let lines = [];
    lines.push('# Unlocked VTX table — generated by Forge RF Tools');
    lines.push(`# Protocol: ${proto === 'smartaudio' ? 'SmartAudio 2.1' : 'IRC Tramp'}`);
    lines.push('vtxtable bands ' + activeBands.length);
    lines.push('vtxtable channels 8');
    lines.push('vtxtable powerlevels ' + powers.length);
    lines.push('vtxtable powervalues ' + powers.map(p => p.mw).join(' '));
    lines.push('vtxtable powerlabels ' + powers.map(p => p.label).join(' '));
    lines.push('');

    activeBands.forEach((k, i) => {
        const b = VTX_UNLOCK_BANDS[k];
        lines.push(`vtxtable band ${i+1} ${b.name} ${k} ${b.freqs.join(' ')}`);
    });

    lines.push('');
    lines.push('saveVtxTable');
    lines.push('save');

    const output = lines.join('\n');
    document.getElementById('vtx-output').value = output;
    document.getElementById('vtx-line-count').textContent = lines.length + ' lines';
}

function copyVtxTable() {
    const text = document.getElementById('vtx-output').value;
    navigator.clipboard.writeText(text).then(() => {
        const btn = event.target;
        const orig = btn.textContent;
        btn.textContent = '✓ Copied!';
        btn.style.background = '#15803d';
        setTimeout(() => { btn.textContent = orig; btn.style.background = ''; }, 2000);
    });
}
renderVtxTable();

// ═══ MafiaLRS Self-Hosted Generator ═══
(function(){
    let mafiaDB=null, mafiaType='rx', mafiaSearch='', mafiaMfr='';

    fetch('forge_database.json').then(r=>{if(!r.ok)throw new Error('Catalog unavailable');return r.json();}).then(db=>{
        mafiaDB=db;
        const rx=[], tx=[];
        for(const p of (db.components.receivers||[])){
            const s=String(p.source||'').toLowerCase(), pid=String(p.pid||'').toLowerCase();
            if(s.includes('mafia')||pid.includes('mafia')) rx.push(p);
        }
        for(const p of (db.components.control_link_tx||[])){
            const s=String(p.source||'').toLowerCase(), pid=String(p.pid||'').toLowerCase();
            if(s.includes('mafia')||pid.includes('mafia')) tx.push(p);
        }
        window._mafiaRX=rx; window._mafiaTX=tx;
        document.getElementById('mafia-rx-count').textContent=rx.length;
        document.getElementById('mafia-tx-count').textContent=tx.length;

        // Populate manufacturer dropdown
        const allMfrs=new Set();
        rx.forEach(p=>allMfrs.add(p.manufacturer||'Unknown'));
        tx.forEach(p=>allMfrs.add(p.manufacturer||'Unknown'));
        const sel=document.getElementById('mafia-mfr');
        Array.from(allMfrs).sort().forEach(m=>{
            const o=document.createElement('option');o.value=m;o.textContent=m;sel.appendChild(o);
        });

        renderMafiaTargets();
    }).catch(function(){
        document.getElementById('mafia-target-list').textContent='The target catalog is not included in this build. Firmware reference information is available below.';
        ['mafia-search','mafia-mfr'].forEach(function(id){document.getElementById(id).disabled=true;});
        document.getElementById('mafia-rx-count').textContent='—';
        document.getElementById('mafia-tx-count').textContent='—';
    });

    function renderMafiaTargets(){
        const targets=mafiaType==='rx'?window._mafiaRX:window._mafiaTX;
        if(!targets)return;
        let filtered=targets;
        if(mafiaSearch){
            const q=mafiaSearch.toLowerCase();
            filtered=filtered.filter(p=>(p.name||'').toLowerCase().includes(q)||(p.pid||'').toLowerCase().includes(q)||(p.manufacturer||'').toLowerCase().includes(q));
        }
        if(mafiaMfr) filtered=filtered.filter(p=>(p.manufacturer||'')=== mafiaMfr);

        const list=document.getElementById('mafia-target-list');
        if(!filtered.length){list.innerHTML='<div style="padding:20px;text-align:center;color:var(--text-muted);font-size:13px;">No targets found</div>';return}

        list.innerHTML=filtered.map(p=>{
            const esc=s=>{const d=document.createElement('div');d.textContent=s;return d.innerHTML};
            return `<div class="mafia-target-row" data-pid="${esc(p.pid)}" data-name="${esc(p.name)}" data-mfr="${esc(p.manufacturer||'')}" style="padding:8px 12px;border-bottom:1px solid var(--border-color);cursor:pointer;display:flex;justify-content:space-between;align-items:center;font-size:13px;transition:background .1s;" onmouseover="this.style.background='rgba(34,211,238,.04)'" onmouseout="this.style.background=''">
                <div><span style="color:var(--text-main);font-weight:500;">${esc(p.name)}</span><span style="color:var(--text-faint);font-size:11px;margin-left:8px;">${esc(p.manufacturer||'')}</span></div>
                <span style="font-size:10px;color:var(--text-faint);font-family:var(--font-family);">${esc(p.pid)}</span>
            </div>`;
        }).join('');

        list.querySelectorAll('.mafia-target-row').forEach(row=>{
            row.addEventListener('click',()=>{
                const sel=document.getElementById('mafia-selected');
                sel.style.display='block';
                document.getElementById('mafia-sel-name').textContent=row.dataset.name;
                document.getElementById('mafia-sel-detail').textContent='PID: '+row.dataset.pid+' — Manufacturer: '+row.dataset.mfr;
                sel.dataset.pid=row.dataset.pid;
            });
        });
    }

    // Type toggle
    document.querySelectorAll('.mafia-type-btn').forEach(btn=>{
        btn.addEventListener('click',()=>{
            document.querySelectorAll('.mafia-type-btn').forEach(b=>{b.style.background='var(--bg-panel)';b.style.color='var(--text-muted)';b.classList.remove('active')});
            btn.style.background='rgba(34,211,238,.08)';btn.style.color='var(--accent-red)';btn.classList.add('active');
            mafiaType=btn.dataset.type;
            document.getElementById('mafia-selected').style.display='none';
            renderMafiaTargets();
        });
    });

    // Search
    document.getElementById('mafia-search').addEventListener('input',e=>{mafiaSearch=e.target.value;renderMafiaTargets()});

    // Manufacturer filter
    document.getElementById('mafia-mfr').addEventListener('change',e=>{mafiaMfr=e.target.value;renderMafiaTargets()});

    // Copy PID
    document.getElementById('mafia-copy-pid').addEventListener('click',function(){
        const pid=document.getElementById('mafia-selected').dataset.pid;
        if(pid){navigator.clipboard.writeText(pid).then(()=>{this.innerHTML='<i class="ph ph-check"></i> Copied!';setTimeout(()=>{this.innerHTML='<i class="ph ph-copy"></i> Copy target ID'},2000)})}
    });
})();

// ═══════════════════════════════════════════════════════════════════════════
// RF TERRAIN PROPAGATION ENGINE
// ═══════════════════════════════════════════════════════════════════════════
(function(){
    const RF_PROTOCOLS = {
        GHST:       { name:"GHST (ImmersionRC)",   freq:2400, txPower:27, rxSens:-108, mod:"LoRa-like" },
        ELRS_2G4:   { name:"ExpressLRS 2.4G",      freq:2400, txPower:27, rxSens:-123, mod:"LoRa" },
        ELRS_900:   { name:"ExpressLRS 900M",       freq:915,  txPower:27, rxSens:-123, mod:"LoRa" },
        CRSF:       { name:"Crossfire (TBS)",       freq:915,  txPower:30, rxSens:-130, mod:"LoRa" },
        DJI_O3:     { name:"DJI O3/O4",            freq:5800, txPower:25, rxSens:-93,  mod:"OFDM" },
        HDZERO:     { name:"HDZero",                freq:5800, txPower:25, rxSens:-90,  mod:"OFDM" },
        WALKSNAIL:  { name:"Walksnail Avatar",      freq:5800, txPower:25, rxSens:-92,  mod:"OFDM" },
        ANALOG_FPV: { name:"Analog FPV",            freq:5800, txPower:25, rxSens:-85,  mod:"FM" },
        DOODLE_MESH:{ name:"Doodle Labs Mesh",      freq:2400, txPower:30, rxSens:-96,  mod:"OFDM" },
        SILVUS:     { name:"Silvus StreamCaster",    freq:1625, txPower:33, rxSens:-100, mod:"MIMO-OFDM" },
        RFD900X:    { name:"RFD900x",               freq:915,  txPower:30, rxSens:-121, mod:"FHSS" },
        PERSISTENT: { name:"Persistent MPU5",       freq:2400, txPower:33, rxSens:-98,  mod:"MIMO-OFDM" },
    };

    function haversine(lat1,lon1,lat2,lon2){
        const R=6371000,dLat=(lat2-lat1)*Math.PI/180,dLon=(lon2-lon1)*Math.PI/180;
        const a=Math.sin(dLat/2)**2+Math.cos(lat1*Math.PI/180)*Math.cos(lat2*Math.PI/180)*Math.sin(dLon/2)**2;
        return R*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
    }
    function fspl(dKm,fMHz){ return dKm<=0||fMHz<=0?0:20*Math.log10(dKm)+20*Math.log10(fMHz)+32.44; }
    function fresnelR(d1,d2,fMHz,n){
        const lam=300/fMHz,D=d1+d2;
        return D<=0?0:Math.sqrt(n*lam*d1*d2/D);
    }
    function knifeEdgeLoss(v){
        if(v<=-0.78)return 0;
        if(v<0)return 6.02+9.11*v+1.27*v*v;
        return 6.02+9.0*v+1.65*v*v;
    }
    function fresnelV(h,d1,d2,fMHz){
        const lam=300/fMHz,D=d1+d2;
        return D<=0?0:h*Math.sqrt(2*D/(lam*d1*d2));
    }
    function interpolatePoints(lat1,lon1,lat2,lon2,n){
        const pts=[];
        for(let i=0;i<=n;i++){const t=i/n;pts.push({lat:lat1+t*(lat2-lat1),lon:lon1+t*(lon2-lon1)});}
        return pts;
    }

    // ── Local DEM (SRTM HGT) Storage ──
    var loadedDEMs=[]; // [{name,lat,lon,resolution,data,rows,cols}]
    var elevCache=new Map();

    // Parse SRTM HGT file (raw binary, 1201x1201 or 3601x3601 Int16BE)
    function parseHGT(arrayBuffer,filename){
        var bytes=arrayBuffer.byteLength;
        var res=1; // arc-seconds
        var size;
        if(bytes===2884802){size=1201;res=3;} // SRTM3 (3 arc-second, ~90m)
        else if(bytes===25934402){size=3601;res=1;} // SRTM1 (1 arc-second, ~30m)
        else{return null;} // Unknown format
        // Extract lat/lon from filename: N39W105.hgt
        var m=filename.match(/([NS])(\d+)([EW])(\d+)/i);
        if(!m)return null;
        var lat=parseInt(m[2])*(m[1].toUpperCase()==='S'?-1:1);
        var lon=parseInt(m[4])*(m[3].toUpperCase()==='W'?-1:1);
        var data=new Int16Array(bytes/2);
        var view=new DataView(arrayBuffer);
        for(var i=0;i<data.length;i++){data[i]=view.getInt16(i*2,false);} // Big-endian
        return{name:filename,lat:lat,lon:lon,resolution:res,data:data,rows:size,cols:size};
    }

    // Parse GeoTIFF DEM file using geotiff.js
    async function parseGeoTIFF(arrayBuffer,filename){
        try{
            var tiff=await GeoTIFF.fromArrayBuffer(arrayBuffer);
            var image=await tiff.getImage();
            var bbox=image.getBoundingBox(); // [minX,minY,maxX,maxY] = [west,south,east,north]
            var w=image.getWidth(),h=image.getHeight();
            var rasters=await image.readRasters();
            var data=rasters[0]; // First band = elevation
            // Convert to Float32 for uniform handling
            var f32=new Float32Array(data.length);
            for(var i=0;i<data.length;i++){f32[i]=data[i];}
            return{
                name:filename,type:'geotiff',
                west:bbox[0],south:bbox[1],east:bbox[2],north:bbox[3],
                data:f32,rows:h,cols:w,
                // For sampleDEM compatibility
                lat:Math.floor(bbox[1]),lon:Math.floor(bbox[0]),
                pixelW:(bbox[2]-bbox[0])/w,pixelH:(bbox[3]-bbox[1])/h,
                resolution:null // variable
            };
        }catch(e){console.error('GeoTIFF parse error:',e);return null;}
    }

    // Sample elevation from a GeoTIFF DEM
    function sampleGeoTIFF(dem,lat,lon){
        if(lat<dem.south||lat>dem.north||lon<dem.west||lon>dem.east)return null;
        var col=Math.round((lon-dem.west)/dem.pixelW);
        var row=Math.round((dem.north-lat)/dem.pixelH);
        col=Math.max(0,Math.min(dem.cols-1,col));
        row=Math.max(0,Math.min(dem.rows-1,row));
        var val=dem.data[row*dem.cols+col];
        if(val<-500||val>9000)return null; // nodata
        return val;
    }

    // Sample elevation from loaded DEMs (HGT or GeoTIFF)
    function sampleDEM(lat,lon){
        for(var i=0;i<loadedDEMs.length;i++){
            var dem=loadedDEMs[i];
            if(dem.type==='geotiff'){
                var v=sampleGeoTIFF(dem,lat,lon);
                if(v!==null)return v;
            } else {
                // Original HGT sampling
                if(lat>=dem.lat&&lat<dem.lat+1&&lon>=dem.lon&&lon<dem.lon+1){
                    var row=Math.round((dem.lat+1-lat)*(dem.rows-1));
                    var col=Math.round((lon-dem.lon)*(dem.cols-1));
                    row=Math.max(0,Math.min(dem.rows-1,row));
                    col=Math.max(0,Math.min(dem.cols-1,col));
                    var val=dem.data[row*dem.cols+col];
                    if(val===-32768)return null;
                    return val;
                }
            }
        }
        return null;
    }

    // Try local DEM first, then APIs
    function fetchLocalDEM(pts){
        var results=pts.map(function(p){return sampleDEM(p.lat,p.lon);});
        if(results.every(function(r){return r!==null;}))return results;
        return null; // Not all points covered
    }

    async function fetchSRTM(pts){
        var locs=pts.map(function(p){return p.lat+','+p.lon;}).join('|');
        try{
            var r=await fetch('https://api.open-elevation.com/api/v1/lookup?locations='+locs);
            if(!r.ok)throw new Error(r.status);
            var d=await r.json();
            return d.results.map(function(x){return x.elevation;});
        }catch(e){return null;}
    }
    async function fetch3DEP(pts){
        try{
            var results=await Promise.all(pts.map(async function(p){
                var r=await fetch('https://epqs.nationalmap.gov/v1/json?x='+p.lon+'&y='+p.lat+'&wkid=4326&units=Meters&includeDate=false');
                if(!r.ok)return null;
                var d=await r.json();
                return d.value!==undefined?parseFloat(d.value):null;
            }));
            return results.some(function(r){return r===null||r===-1000000;})?null:results;
        }catch(e){return null;}
    }
    async function getElevations(pts){
        var key=pts.map(function(p){return p.lat.toFixed(5)+','+p.lon.toFixed(5);}).join('|');
        if(elevCache.has(key))return elevCache.get(key);
        var result=null,source='none';
        // Priority 1: Local DEM (instant, no API calls)
        result=fetchLocalDEM(pts);
        if(result){source='Local DEM ('+loadedDEMs.length+' tiles)';}
        // Priority 2: 3DEP API (US only, high-res)
        if(!result){
            var isUS=pts.every(function(p){return p.lat>=24&&p.lat<=50&&p.lon>=-125&&p.lon<=-66;});
            if(isUS&&pts.length<=30){result=await fetch3DEP(pts);if(result)source='3DEP';}
        }
        // Priority 3: Open-Elevation SRTM API
        if(!result){result=await fetchSRTM(pts);if(result)source='SRTM API';}
        // Fallback: flat
        if(!result){result=pts.map(function(){return 0;});source='Flat (no data)';}
        var out={elevations:result,source:source};
        elevCache.set(key,out);
        return out;
    }

    // ── DEM file upload handler ──
    window.rfLoadDEM=function(files){
        if(!files||!files.length)return;
        var loaded=0,failed=0,total=files.length;
        function updateStatus(){
            if(loaded+failed<total)return;
            elevCache.clear();
            var msg='<span style="color:#4ade80;">'+loaded+' file'+(loaded>1?'s':'')+' loaded</span>'+(failed?' <span style="color:#f87171;">'+failed+' failed</span>':'')+' — '+loadedDEMs.map(function(d){
                if(d.type==='geotiff')return d.name.replace(/\.[^.]+$/,'');
                var ns=d.lat>=0?'N':'S',ew=d.lon>=0?'E':'W';
                return ns+Math.abs(d.lat)+ew+Math.abs(d.lon);
            }).join(', ');
            ['rf-dem-status','mesh-dem-status'].forEach(function(id){var el=document.getElementById(id);if(el)el.innerHTML=msg;});
        }
        Array.from(files).forEach(function(file){
            var reader=new FileReader();
            reader.onload=async function(e){
                var ext=file.name.toLowerCase().split('.').pop();
                if(ext==='tif'||ext==='tiff'){
                    var dem=await parseGeoTIFF(e.target.result,file.name);
                    if(dem){loadedDEMs.push(dem);loaded++;}else{failed++;}
                    updateStatus();
                } else {
                    var dem=parseHGT(e.target.result,file.name);
                    if(dem){
                        var exists=loadedDEMs.some(function(d){return d.lat===dem.lat&&d.lon===dem.lon&&d.type!=='geotiff';});
                        if(!exists)loadedDEMs.push(dem);
                        loaded++;
                    } else {failed++;}
                    updateStatus();
                }
            };
            reader.readAsArrayBuffer(file);
        });
    };

    window.rfClearDEM=function(){
        loadedDEMs=[];
        elevCache.clear();
        ['rf-dem-status','mesh-dem-status'].forEach(function(id){var el=document.getElementById(id);if(el)el.textContent='Cleared — using API';});
    };

    function analyzePath(elevs,dists,txH,rxH,fMHz){
        const totalD=dists[dists.length-1];
        const txE=elevs[0]+txH,rxE=elevs[elevs.length-1]+rxH;
        const losH=i=>txE+(rxE-txE)*(dists[i]/totalD);
        let worstV=-999,worstIdx=-1;
        const points=elevs.map((e,i)=>{
            const d1=Math.max(dists[i],0.1),d2=Math.max(totalD-dists[i],0.1);
            const los=losH(i),cl=los-e;
            const f1=fresnelR(d1,d2,fMHz,1),f60=f1*0.6;
            const v=fresnelV(-cl,d1,d2,fMHz);
            if(v>worstV&&i>0&&i<elevs.length-1){worstV=v;worstIdx=i;}
            return{dist:dists[i],elev:e,los,clearance:cl,f1,f60,obstructed:cl<0,fresnelObs:cl<f60};
        });
        const diffLoss=worstV>-0.78?knifeEdgeLoss(worstV):0;
        const losObs=points.filter(p=>p.obstructed).length;
        const fObs=points.filter(p=>p.fresnelObs).length;
        const minCl=Math.min(...points.map(p=>p.clearance));
        return{points,totalD,totalDkm:totalD/1000,diffLoss,losObs,fObs,minCl,worstV,worstIdx,hasLOS:losObs===0,hasF60:fObs===0};
    }
    function linkBudget(analysis,txP,rxS,txG,rxG,fMHz,fade){
        const fLoss=fspl(analysis.totalDkm,fMHz);
        const total=fLoss+analysis.diffLoss;
        const rxPow=txP+txG+rxG-total;
        const margin=rxPow-rxS-fade;
        const q=margin>20?'excellent':margin>10?'good':margin>3?'marginal':margin>0?'weak':'fail';
        return{fLoss,diffLoss:analysis.diffLoss,total,rxPow,margin,ok:margin>0,quality:q};
    }

    // Expose terrain pipeline for other tools (mesh planner)
    window._rfInterpolatePoints=interpolatePoints;
    window._rfGetElevations=getElevations;
    window._rfAnalyzePath=analyzePath;
    window._rfLinkBudget=linkBudget;
    window._rfDrawProfile=drawProfile;

    // ── Shared: compute full terrain link between two points ──
    async function computeFullLink(fromLat,fromLon,fromH,toLat,toLon,toH,proto,txP,txG,rxG,fade,samples,label){
        var pts=interpolatePoints(fromLat,fromLon,toLat,toLon,samples||25);
        var elev;
        try{elev=await getElevations(pts);}catch(e){elev={elevations:pts.map(function(){return 0;}),source:'Flat'};}
        var dists=pts.map(function(p){return haversine(fromLat,fromLon,p.lat,p.lon);});
        var analysis=analyzePath(elev.elevations,dists,fromH,toH,proto.freq);
        var budget=linkBudget(analysis,txP,proto.rxSens,txG,rxG,proto.freq,fade);
        return{analysis:analysis,budget:budget,elev:elev,pts:pts,dists:dists,label:label||''};
    }

    // ── Build route trace for any set of points from an origin ──
    function rfBuildTraceFromOrigin(originLat,originLon,waypoints,proto,txP,txG,rxG,fade){
        rfClearTrace();
        if(!rfMap||waypoints.length<1)return;
        var L=window.L;
        var STEPS=100;
        var allPts=[];
        // Interpolate along the full route
        var routePts=[{lat:originLat,lon:originLon}].concat(waypoints);
        for(var i=0;i<routePts.length-1;i++){
            var a=routePts[i],b=routePts[i+1];
            var segSteps=Math.max(5,Math.floor(STEPS/(routePts.length-1)));
            for(var s=0;s<=segSteps;s++){
                var t=s/segSteps;
                var lat=a.lat+(b.lat-a.lat)*t;
                var lon=a.lon+(b.lon-a.lon)*t;
                var d=haversine(originLat,originLon,lat,lon)/1000;
                var fsplDb=d>0?(20*Math.log10(d)+20*Math.log10(proto.freq)+32.44):0;
                var rxPower=txP+txG+rxG-fsplDb;
                var margin=rxPower-proto.rxSens-fade;
                allPts.push({lat:lat,lon:lon,d:d,margin:margin,rxPower:rxPower});
            }
        }
        // Draw colored segments
        for(var i=0;i<allPts.length-1;i++){
            var m=allPts[i].margin;
            var color=m>15?'#4ade80':m>6?'#eab308':m>0?'#f59e0b':'#ef4444';
            var seg=L.polyline([[allPts[i].lat,allPts[i].lon],[allPts[i+1].lat,allPts[i+1].lon]],
                {color:color,weight:4,opacity:0.8}).addTo(rfMap);
            rfTraceSegments.push({polyline:seg});
        }
        rfTraceSegments._allPts=allPts;
        rfTraceSegments._origin={lat:originLat,lon:originLon};
        document.getElementById('rf-route-trace').style.display='block';
        document.getElementById('rf-trace-slider').value=0;
        rfTraceUpdate();
        // Analyze route for issue spots
        rfAnalyzeIssues(allPts);
    }

    // ── Issue spot detection + clickable list ──
    let rfIssueMarkers=[];
    function rfAnalyzeIssues(allPts){
        // Clear old issue markers
        rfIssueMarkers.forEach(function(m){if(rfMap)rfMap.removeLayer(m);});
        rfIssueMarkers=[];
        var el=document.getElementById('rf-issue-spots');
        if(!el||!allPts||allPts.length<2){if(el)el.style.display='none';return;}
        // Find issue zones: contiguous segments where margin < threshold
        var issues=[];
        var inIssue=false;
        var issueStart=null;
        var worstPt=null;
        for(var i=0;i<allPts.length;i++){
            var p=allPts[i];
            if(p.margin<10){
                if(!inIssue){inIssue=true;issueStart=i;worstPt=p;}
                if(p.margin<worstPt.margin)worstPt=p;
            } else {
                if(inIssue){
                    issues.push({startIdx:issueStart,endIdx:i-1,worst:worstPt,
                        startPt:allPts[issueStart],endPt:allPts[i-1]});
                    inIssue=false;worstPt=null;
                }
            }
        }
        if(inIssue){issues.push({startIdx:issueStart,endIdx:allPts.length-1,worst:worstPt,
            startPt:allPts[issueStart],endPt:allPts[allPts.length-1]});}
        if(issues.length===0){
            el.innerHTML='<div style="padding:6px 10px;font-size:10px;color:#4ade80;background:rgba(74,222,128,0.06);border:1px solid rgba(74,222,128,0.2);border-radius:var(--radius-sm);">\u2713 No issue spots — signal stays above 10 dB margin along entire route</div>';
            el.style.display='block';
            return;
        }
        // Classify issues
        var html='<div style="padding:8px 10px;background:var(--bg-panel);border:1px solid var(--border-color);border-radius:var(--radius-sm);">';
        html+='<div style="font-size:9px;font-weight:600;color:var(--text-main);text-transform:uppercase;letter-spacing:0.08em;margin-bottom:6px;">\u26A0 '+issues.length+' Issue Spot'+(issues.length>1?'s':'')+' Detected</div>';
        var L=window.L;
        issues.forEach(function(issue,idx){
            var severity=issue.worst.margin<0?'LOST':issue.worst.margin<3?'CRITICAL':'WEAK';
            var sColor=issue.worst.margin<0?'#ef4444':issue.worst.margin<3?'#f87171':'#eab308';
            var spanKm=issue.endPt.d-issue.startPt.d;
            var pctOfRoute=Math.round((issue.endIdx-issue.startIdx)/allPts.length*100);
            // Add warning marker on map at worst point
            if(L&&rfMap){
                var icon=L.divIcon({className:'',html:'<div style="width:12px;height:12px;border-radius:50%;background:'+sColor+';border:2px solid #0b0f14;box-shadow:0 0 6px '+sColor+'88;"></div>',iconSize:[12,12],iconAnchor:[6,6]});
                var marker=L.marker([issue.worst.lat,issue.worst.lon],{icon:icon,interactive:false}).addTo(rfMap);
                rfIssueMarkers.push(marker);
            }
            html+='<div onclick="rfJumpToIssue('+idx+')" style="display:flex;align-items:center;gap:8px;padding:5px 8px;margin-bottom:3px;border-radius:var(--radius-sm);cursor:pointer;border-left:3px solid '+sColor+';background:'+sColor+'08;transition:background 0.15s;" onmouseover="this.style.background=\''+sColor+'15\'" onmouseout="this.style.background=\''+sColor+'08\'">';
            html+='<span style="font-size:10px;font-weight:700;color:'+sColor+';min-width:55px;">'+severity+'</span>';
            html+='<span style="font-size:10px;color:var(--text-muted);flex:1;">'+issue.worst.margin.toFixed(1)+' dB @ '+issue.worst.d.toFixed(2)+' km</span>';
            html+='<span style="font-size:9px;color:var(--text-faint);">'+spanKm.toFixed(2)+' km ('+pctOfRoute+'%)</span>';
            html+='<span style="font-size:10px;color:var(--text-faint);">\u203A</span>';
            html+='</div>';
        });
        html+='<div style="font-size:9px;color:var(--text-faint);margin-top:6px;">Click an issue to jump to it on the map. Drag waypoints to reroute around problems.</div>';
        html+='</div>';
        el.innerHTML=html;
        el.style.display='block';
        // Store issues for jump function
        rfTraceSegments._issues=issues;
    }

    window.rfJumpToIssue=function(idx){
        var issues=rfTraceSegments._issues;
        if(!issues||!issues[idx])return;
        var issue=issues[idx];
        var pts=rfTraceSegments._allPts;
        // Pan map to the worst point
        if(rfMap)rfMap.setView([issue.worst.lat,issue.worst.lon],15);
        // Move slider to issue position
        var pct=Math.round(issue.startIdx/pts.length*100);
        document.getElementById('rf-trace-slider').value=pct;
        rfTraceUpdate();
    };
    let txPos=null,rxPos=null;
    let rfTimer=null;
    let rfClickState=0; // 0=place TX, 1=place RX, 2=both placed
    let txMarker=null,rxMarker=null,pathLine=null;

    // Advanced hardware radio specs (used when checkbox is checked)
    var RF_ADV_RADIOS={
        DL_2450:{freq:2400,txPow:30,rxSens:-96,name:'Doodle Labs RM-2450'},
        DL_1675:{freq:1625,txPow:30,rxSens:-96,name:'Doodle Labs RM-1675'},
        DL_2458:{freq:2400,txPow:30,rxSens:-93,name:'Doodle Labs DM-2458'},
        SC4200:{freq:1625,txPow:33,rxSens:-100,name:'Silvus SC4200'},
        SC4400:{freq:1625,txPow:33,rxSens:-100,name:'Silvus SC4400'},
        MPU5:{freq:2400,txPow:33,rxSens:-98,name:'Persistent MPU5'},
        TW950:{freq:1350,txPow:30,rxSens:-100,name:'TW-950 Shadow'},
        TW135:{freq:1350,txPow:43,rxSens:-100,name:'TW-135 Shadow HPR'},
        TW750:{freq:1350,txPow:30,rxSens:-100,name:'TW-750 Shadow'},
        TW650:{freq:1350,txPow:27,rxSens:-100,name:'TW-650 Module'},
        TW870:{freq:1350,txPow:24,rxSens:-100,name:'TW-870 Ghost'},
        TW880:{freq:1350,txPow:24,rxSens:-100,name:'TW-880 Ghost Module'},
        RFD900X:{freq:915,txPow:30,rxSens:-121,name:'RFD900x'},
        RAJANT_ES1:{freq:2400,txPow:27,rxSens:-95,name:'Rajant ES1'},
        RAJANT_PG:{freq:2400,txPow:27,rxSens:-93,name:'Rajant Peregrine'},
        ELRS_900:{freq:915,txPow:27,rxSens:-123,name:'ELRS 900M TX'},ELRS_900_RX:{freq:915,txPow:20,rxSens:-123,name:'ELRS 900M RX'},
        ELRS_24:{freq:2400,txPow:13,rxSens:-118,name:'ELRS 2.4G TX'},ELRS_24_RX:{freq:2400,txPow:13,rxSens:-118,name:'ELRS 2.4G RX'},
        CRSF_TX:{freq:915,txPow:30,rxSens:-130,name:'Crossfire TX'},CRSF_RX:{freq:915,txPow:20,rxSens:-130,name:'Crossfire RX'},
        GHST_TX:{freq:2400,txPow:27,rxSens:-108,name:'GHST TX'},GHST_RX:{freq:2400,txPow:24,rxSens:-108,name:'GHST RX'},
    };

    var rfHwAdvanced=false;
    window.rfToggleHwAdvanced=function(){
        rfHwAdvanced=document.getElementById('rf-hw-advanced').checked;
        document.getElementById('rf-hw-simple').style.display=rfHwAdvanced?'none':'block';
        document.getElementById('rf-hw-advanced-panel').style.display=rfHwAdvanced?'block':'none';
        if(rfHwAdvanced)rfUpdateAdvHwInfo();
    };

    window.rfGcsRadioChange=function(){rfUpdateAdvHwInfo();};
    window.rfAirRadioChange=function(){rfUpdateAdvHwInfo();};

    function rfUpdateAdvHwInfo(){
        var gcs=RF_ADV_RADIOS[document.getElementById('rf-gcs-radio').value];
        var air=RF_ADV_RADIOS[document.getElementById('rf-air-radio').value];
        if(!gcs||!air)return;
        var info='GCS: '+gcs.name+' ('+gcs.freq+' MHz, '+gcs.txPow+' dBm, '+gcs.rxSens+' dBm) · Air: '+air.name+' ('+air.freq+' MHz, '+air.txPow+' dBm)';
        if(Math.abs(gcs.freq-air.freq)>500)info+=' <span style="color:#f87171;font-weight:700;">⚠ FREQUENCY MISMATCH</span>';
        document.getElementById('rf-adv-hw-info').innerHTML=info;
    }

    function getVal(id){
        if(rfHwAdvanced){
            // Map simple IDs to advanced IDs
            var advMap={'rf-tx-power':null,'rf-tx-height':'rf-adv-tx-height','rf-rx-height':'rf-adv-rx-height',
                'rf-tx-gain':'rf-adv-tx-gain','rf-rx-gain':'rf-adv-rx-gain',
                'rf-fade-margin':'rf-adv-fade','rf-samples':'rf-adv-samples'};
            if(id==='rf-tx-power'){
                // In advanced mode, TX power comes from the GCS radio spec
                var gcs=RF_ADV_RADIOS[document.getElementById('rf-gcs-radio').value];
                return gcs?gcs.txPow:27;
            }
            var mapped=advMap[id];
            if(mapped){var el=document.getElementById(mapped);if(el)return parseFloat(el.value)||0;}
        }
        return parseFloat(document.getElementById(id).value)||0;
    }
    function getProto(){
        if(rfHwAdvanced){
            var gcs=RF_ADV_RADIOS[document.getElementById('rf-gcs-radio').value]||RF_ADV_RADIOS.ELRS_900;
            var air=RF_ADV_RADIOS[document.getElementById('rf-air-radio').value]||RF_ADV_RADIOS.ELRS_900_RX;
            return{freq:Math.min(gcs.freq,air.freq),rxSens:Math.max(gcs.rxSens,air.rxSens),mod:gcs.name+' + '+air.name};
        }
        return RF_PROTOCOLS[document.getElementById('rf-protocol').value]||RF_PROTOCOLS.ELRS_900;
    }

    // ── Address autocomplete with debounce ──
    let acTimer=null;
    function rfSetupAutocomplete(){
        const input=document.getElementById('rf-coord-input');
        const dropdown=document.getElementById('rf-autocomplete');
        if(!input||!dropdown)return;

        input.addEventListener('input',function(){
            clearTimeout(acTimer);
            const val=input.value.trim();
            if(val.length<3){dropdown.style.display='none';return;}

            // Check if it's coordinates first
            const parsed=parseCoordInput(val);
            if(parsed){
                dropdown.style.display='none';
                rfShowCoordFormats(parsed.lat,parsed.lon);
                return;
            }

            // Debounce address search
            acTimer=setTimeout(function(){
                fetch('https://nominatim.openstreetmap.org/search?q='+encodeURIComponent(val)+'&format=json&limit=5&addressdetails=1',
                    {headers:{'User-Agent':'Forge-RF-Tool/1.0'}})
                .then(r=>r.json()).then(data=>{
                    if(!data.length){dropdown.style.display='none';return;}
                    dropdown.innerHTML='';
                    data.forEach(function(item){
                        const opt=document.createElement('div');
                        opt.style.cssText='padding:8px 12px; cursor:pointer; font-size:12px; color:var(--text-muted); border-bottom:1px solid var(--border-color); font-family:var(--font-family);';
                        opt.textContent=item.display_name.substring(0,80);
                        opt.onmouseover=function(){opt.style.background='var(--bg-panel-hover)';opt.style.color='var(--text-main)';};
                        opt.onmouseout=function(){opt.style.background='none';opt.style.color='var(--text-muted)';};
                        opt.onclick=function(){
                            const lat=parseFloat(item.lat),lon=parseFloat(item.lon);
                            input.value=item.display_name.substring(0,60);
                            dropdown.style.display='none';
                            rfMoveMapTo(lat,lon);
                            rfShowCoordFormats(lat,lon);
                        };
                        dropdown.appendChild(opt);
                    });
                    dropdown.style.display='block';
                }).catch(function(){dropdown.style.display='none';});
            },400);
        });

        input.addEventListener('keydown',function(e){
            if(e.key==='Enter'){
                dropdown.style.display='none';
                rfSearchCoord();
            }
        });

        // Close dropdown on outside click
        document.addEventListener('click',function(e){
            if(!input.contains(e.target)&&!dropdown.contains(e.target))dropdown.style.display='none';
        });
    }

    function rfSearchCoord(){
        const input=document.getElementById('rf-coord-input').value.trim();
        if(!input)return;
        const parsed=parseCoordInput(input);
        if(parsed){
            rfMoveMapTo(parsed.lat,parsed.lon);
            rfShowCoordFormats(parsed.lat,parsed.lon);
        } else {
            fetch('https://nominatim.openstreetmap.org/search?q='+encodeURIComponent(input)+'&format=json&limit=1',
                {headers:{'User-Agent':'Forge-RF-Tool/1.0'}})
            .then(r=>r.json()).then(data=>{
                if(data.length>0){
                    const lat=parseFloat(data[0].lat),lon=parseFloat(data[0].lon);
                    rfMoveMapTo(lat,lon);
                    rfShowCoordFormats(lat,lon);
                    document.getElementById('rf-coord-input').value=data[0].display_name.substring(0,60);
                }
            }).catch(function(){});
        }
    }

    function rfMoveMapTo(lat,lon){
        if(rfMap) rfMap.setView([lat,lon],13);
        // Don't auto-place markers — user clicks to place
    }

    function rfShowCoordFormats(lat,lon){
        const el=document.getElementById('rf-coord-display');
        if(!el)return;
        const dd=lat.toFixed(6)+', '+lon.toFixed(6);
        const dms=toDMS(lat,'NS')+' '+toDMS(lon,'EW');
        const mgrs=toMGRS(lat,lon);
        el.innerHTML='DD: '+dd+' · DMS: '+dms+' · MGRS: '+mgrs;
    }

    // ── Click-to-place markers ──
    function rfMapClick(e){
        const lat=e.latlng.lat,lon=e.latlng.lng;

        // Route to mode-specific handler
        if(rfAdvanced&&rfAdvMode==='waypoint'){
            rfWaypointClick(lat,lon);
            return;
        }
        if(rfAdvanced&&rfAdvMode==='repeater'){
            rfRepeaterClick(lat,lon);
            return;
        }

        // Simple mode: TX then RX
        const L=window.L;
        function makeIcon(c){return L.divIcon({className:'',html:'<div style="width:16px;height:16px;border-radius:50%;background:'+c+';border:3px solid #0b0f14;box-shadow:0 0 8px '+c+'88;"></div>',iconSize:[16,16],iconAnchor:[8,8]});}

        if(rfClickState===0||rfClickState===2){
            // Place TX (or restart)
            if(rfClickState===2){
                // Clear previous markers
                if(txMarker){rfMap.removeLayer(txMarker);txMarker=null;}
                if(rxMarker){rfMap.removeLayer(rxMarker);rxMarker=null;}
                if(pathLine){rfMap.removeLayer(pathLine);pathLine=null;}
                rxPos=null;
            }
            txPos={lat:lat,lon:lon};
            txMarker=L.marker([lat,lon],{draggable:true,icon:makeIcon('#22d3ee')}).addTo(rfMap);
            txMarker.on('dragend',function(){txPos={lat:txMarker.getLatLng().lat,lon:txMarker.getLatLng().lng};rfUpdateCoordDisplay();});
            rfClickState=1;
            document.getElementById('rf-tx-coords').textContent=lat.toFixed(5)+', '+lon.toFixed(5);
            document.getElementById('rf-rx-coords').textContent='click map to place RX...';
            document.getElementById('rf-status').textContent='';
        } else if(rfClickState===1){
            // Place RX
            rxPos={lat:lat,lon:lon};
            rxMarker=L.marker([lat,lon],{draggable:true,icon:makeIcon('#2a9d8f')}).addTo(rfMap);
            rxMarker.on('dragend',function(){rxPos={lat:rxMarker.getLatLng().lat,lon:rxMarker.getLatLng().lng};rfUpdateCoordDisplay();});
            pathLine=L.polyline([[txPos.lat,txPos.lon],[lat,lon]],{color:'#22d3ee',weight:2,dashArray:'8,8',opacity:0.7}).addTo(rfMap);
            rfClickState=2;
            rfUpdateCoordDisplay();
        }
    }

    function rfUpdateCoordDisplay(){
        if(txPos) document.getElementById('rf-tx-coords').textContent=txPos.lat.toFixed(5)+', '+txPos.lon.toFixed(5);
        if(rxPos) document.getElementById('rf-rx-coords').textContent=rxPos.lat.toFixed(5)+', '+rxPos.lon.toFixed(5);
        if(txPos&&rxPos&&pathLine) pathLine.setLatLngs([[txPos.lat,txPos.lon],[rxPos.lat,rxPos.lon]]);
    }

    function parseCoordInput(input){
        // Try DD: "39.7392, -104.9903"
        let m=input.match(/^(-?\d+\.?\d*)[,\s]+(-?\d+\.?\d*)$/);
        if(m){const lat=parseFloat(m[1]),lon=parseFloat(m[2]);if(Math.abs(lat)<=90&&Math.abs(lon)<=180)return{lat,lon};}
        // Try DMS: "39°44'21"N 104°59'25"W"
        const dmsRe=/(\d+)[°](\d+)[']([0-9.]+)["]?\s*([NSEW])/gi;
        const parts=[...input.matchAll(dmsRe)];
        if(parts.length===2){
            function dm(p){let d=parseFloat(p[1])+parseFloat(p[2])/60+parseFloat(p[3])/3600;if(p[4]==='S'||p[4]==='W'||p[4]==='s'||p[4]==='w')d=-d;return d;}
            return{lat:dm(parts[0]),lon:dm(parts[1])};
        }
        // Try MGRS: "13SDE8401012345" (basic regex — 10+ chars starting with digit)
        if(/^\d{1,2}[A-Z]{1,3}\d{4,10}$/i.test(input.replace(/\s/g,''))){
            const ll=mgrsToLatLon(input.replace(/\s/g,'').toUpperCase());
            if(ll)return ll;
        }
        return null;
    }

    function toDMS(dec,dir){
        const a=Math.abs(dec),d=Math.floor(a),mf=(a-d)*60,m=Math.floor(mf),s=((mf-m)*60).toFixed(1);
        const c=dir==='NS'?(dec>=0?'N':'S'):(dec>=0?'E':'W');
        return d+'°'+String(m).padStart(2,'0')+"'"+String(s).padStart(4,'0')+'"'+c;
    }

    // Simplified MGRS→LatLon (covers most cases without external lib)
    function mgrsToLatLon(mgrs){
        // This is a basic decoder. For full accuracy use a proper lib.
        // Format: ZZB EE NN (zone, band, sq letters, easting, northing)
        try{
            const zoneMatch=mgrs.match(/^(\d{1,2})([C-X])([A-Z]{2})(\d+)$/);
            if(!zoneMatch)return null;
            const zone=parseInt(zoneMatch[1]),band=zoneMatch[2],sq=zoneMatch[3],digits=zoneMatch[4];
            if(digits.length%2!==0)return null;
            const half=digits.length/2;
            const eStr=digits.substring(0,half),nStr=digits.substring(half);
            const scale=Math.pow(10,5-half);
            const e=parseInt(eStr)*scale+scale/2;
            const n=parseInt(nStr)*scale+scale/2;
            // UTM to lat/lon (simplified)
            const col=sq.charCodeAt(0)-'A'.charCodeAt(0);
            const row=sq.charCodeAt(1)-'A'.charCodeAt(0);
            const easting=((col%8)*100000)+e;
            const northing=(row*100000)+n;
            // Approximate conversion
            const lon0=(zone-1)*6-180+3;
            const lat0=bandToLat(band);
            const lonApprox=lon0+(easting-500000)/(111320*Math.cos(lat0*Math.PI/180));
            const latApprox=lat0+northing/110540;
            if(Math.abs(latApprox)>84||Math.abs(lonApprox)>180)return null;
            return{lat:latApprox,lon:lonApprox};
        }catch(e){return null;}
    }
    function bandToLat(b){const bands='CDEFGHJKLMNPQRSTUVWX';const i=bands.indexOf(b);return i>=0?-80+i*8:-80;}
    function toMGRS(lat,lon){
        // Simplified lat/lon→MGRS (approximate, 100m precision)
        const zone=Math.floor((lon+180)/6)+1;
        const bands='CDEFGHJKLMNPQRSTUVWX';
        const bandIdx=Math.min(19,Math.max(0,Math.floor((lat+80)/8)));
        const band=bands[bandIdx];
        const lon0=(zone-1)*6-180+3;
        const k0=0.9996,a=6378137;
        const easting=500000+k0*a*(lon-lon0)*Math.PI/180*Math.cos(lat*Math.PI/180);
        const northing=k0*a*(lat*Math.PI/180);
        const e100k=Math.floor(easting/100000);
        const n100k=Math.floor((northing%2000000)/100000);
        const setNum=(zone-1)%6;
        const colLetters='ABCDEFGHJKLMNPQRSTUVWXYZ';
        const rowLetters='ABCDEFGHJKLMNPQRSTUV';
        const colLetter=colLetters[(setNum*8+e100k-1)%24];
        const rowLetter=rowLetters[(setNum%2===0?0:5+n100k)%20];
        const eRem=Math.floor(easting%100000/100);
        const nRem=Math.floor((northing<0?northing+10000000:northing)%100000/100);
        return zone+band+colLetter+rowLetter+String(eRem).padStart(3,'0')+String(nRem).padStart(3,'0');
    }

    let mapInited=false;
    // ── Map tile layers ──
    const RF_LAYERS={
        dark:  'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',
        light: 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png',
        sat:   'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
        topo:  'https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/{z}/{y}/{x}',
    };
    let rfTileLayer=null;

    function initRFMap(){
        if(mapInited)return;
        const L=window.L;
        if(!L||!document.getElementById('rf-map'))return;
        mapInited=true;
        rfMap=L.map('rf-map',{center:[39.0,-98.0],zoom:4,zoomControl:true});
        window._rfMapRef=rfMap;
        rfTileLayer=L.tileLayer(RF_LAYERS.topo,{attribution:'&copy; USGS',maxZoom:19});
        watchMapTiles(rfTileLayer, 'rf');
        rfTileLayer.addTo(rfMap);
        rfMap.on('click',rfMapClick);
        rfSetupAutocomplete();
        // Route trace slider
        var slider=document.getElementById('rf-trace-slider');
        if(slider)slider.addEventListener('input',rfTraceUpdate);
        setTimeout(function(){rfMap.invalidateSize();},200);
    }

    window.rfSetLayer=function(key){
        if(!rfMap||!rfTileLayer)return;
        rfTileLayer.setUrl(RF_LAYERS[key]||RF_LAYERS.dark);
        document.querySelectorAll('.rf-layer-btn').forEach(b=>{
            const active=b.dataset.layer===key;
            b.style.background=active?'rgba(34,211,238,0.12)':'none';
            b.style.color=active?'var(--accent-red)':'var(--text-muted)';
            b.setAttribute('aria-pressed', String(active));
        });
    };

    // ── Universal point management ──
    window.rfClearAllPoints=function(){
        // Clear simple mode
        if(txMarker){rfMap.removeLayer(txMarker);txMarker=null;}
        if(rxMarker){rfMap.removeLayer(rxMarker);rxMarker=null;}
        if(pathLine){rfMap.removeLayer(pathLine);pathLine=null;}
        txPos=null;rxPos=null;rfClickState=0;
        document.getElementById('rf-tx-coords').textContent='click to place';
        document.getElementById('rf-rx-coords').textContent='—';
        document.getElementById('rf-status').textContent='';
        // Clear waypoints
        if(typeof rfClearWaypoints==='function')rfClearWaypoints();
        // Clear repeater
        if(typeof rfClearRepeater==='function')rfClearRepeater();
        // Clear route trace
        rfClearTrace();
        rfClearHeatmap();
        // Clear results
        document.getElementById('rf-results').innerHTML='';
        document.getElementById('rf-advanced-results').style.display='none';
        var svg=document.getElementById('rf-profile-svg');
        if(svg)svg.innerHTML='';
    };

    window.rfUndoLastPoint=function(){
        if(rfAdvanced&&rfAdvMode==='waypoint'&&rfWaypoints.length>0){
            rfUndoWaypoint();
            return;
        }
        if(rfAdvanced&&rfAdvMode==='repeater'&&rfRepeaterPts.length>0){
            var last=rfRepeaterPts.pop();
            var lastM=rfRepeaterMarkers.pop();
            if(lastM&&rfMap)rfMap.removeLayer(lastM);
            rfDrawRepeaterLines();
            rfUpdateRepeaterStatus();
            return;
        }
        // Simple mode: undo RX first, then TX
        if(rxMarker){rfMap.removeLayer(rxMarker);rxMarker=null;rxPos=null;rfClickState=1;
            if(pathLine){rfMap.removeLayer(pathLine);pathLine=null;}
            document.getElementById('rf-rx-coords').textContent='click to place RX...';
        } else if(txMarker){rfMap.removeLayer(txMarker);txMarker=null;txPos=null;rfClickState=0;
            document.getElementById('rf-tx-coords').textContent='click to place';
        }
    };

    // ── Route trace (fly the path) ──
    let rfTraceMarker=null;
    let rfTraceSightLine=null;
    let rfTraceSegments=[];  // [{latlngs:[[lat,lon],...], margins:[...]}]

    function rfClearTrace(){
        if(rfTraceMarker&&rfMap){rfMap.removeLayer(rfTraceMarker);rfTraceMarker=null;}
        if(rfTraceSightLine&&rfMap){rfMap.removeLayer(rfTraceSightLine);rfTraceSightLine=null;}
        rfTraceSegments.forEach(s=>{if(s.polyline&&rfMap)rfMap.removeLayer(s.polyline);});
        rfTraceSegments=[];
        if(typeof rfIssueMarkers!=='undefined')rfIssueMarkers.forEach(function(m){if(rfMap)rfMap.removeLayer(m);});
        rfIssueMarkers=[];
        document.getElementById('rf-route-trace').style.display='none';
        var issueEl=document.getElementById('rf-issue-spots');
        if(issueEl)issueEl.style.display='none';
    }

    function rfBuildTrace(){
        if(!rfAdvanced||rfAdvMode!=='waypoint'||rfWaypoints.length<2)return;
        rfClearTrace();
        const L=window.L;
        const proto=getProto();
        const txP=getVal('rf-tx-power'),txG=getVal('rf-tx-gain'),rxG=getVal('rf-rx-gain');
        const fade=getVal('rf-fade-margin');
        const pilot=rfWaypoints[0];
        const STEPS=100;
        let allPts=[];
        // Build full route path interpolated
        for(let i=0;i<rfWaypoints.length-1;i++){
            const a=rfWaypoints[i],b=rfWaypoints[i+1];
            for(let s=0;s<=STEPS/(rfWaypoints.length-1);s++){
                const t=s/(STEPS/(rfWaypoints.length-1));
                const lat=a.lat+(b.lat-a.lat)*t;
                const lon=a.lon+(b.lon-a.lon)*t;
                const d=haversine(pilot.lat,pilot.lon,lat,lon)/1000;
                const fsplDb=d>0?(20*Math.log10(d)+20*Math.log10(proto.freq)+32.44):0;
                const rxPower=txP+txG+rxG-fsplDb;
                const margin=rxPower-proto.rxSens-fade;
                allPts.push({lat,lon,d,margin,rxPower});
            }
        }
        // Draw colored polyline segments
        for(let i=0;i<allPts.length-1;i++){
            const m=allPts[i].margin;
            const color=m>15?'#4ade80':m>6?'#eab308':m>0?'#f59e0b':'#ef4444';
            const seg=L.polyline([[allPts[i].lat,allPts[i].lon],[allPts[i+1].lat,allPts[i+1].lon]],
                {color:color,weight:4,opacity:0.8}).addTo(rfMap);
            rfTraceSegments.push({polyline:seg});
        }
        // Store points for slider
        rfTraceSegments._allPts=allPts;
        rfTraceSegments._origin={lat:pilot.lat,lon:pilot.lon};
        // Show slider
        document.getElementById('rf-route-trace').style.display='block';
        document.getElementById('rf-trace-slider').value=0;
        rfTraceUpdate();
    }

    function rfTraceUpdate(){
        const pts=rfTraceSegments._allPts;
        if(!pts||!pts.length)return;
        const origin=rfTraceSegments._origin;
        const pct=parseInt(document.getElementById('rf-trace-slider').value);
        const idx=Math.min(Math.floor(pct/100*pts.length),pts.length-1);
        const pt=pts[idx];
        const L=window.L;
        if(!rfTraceMarker){
            rfTraceMarker=L.circleMarker([pt.lat,pt.lon],{radius:6,color:'#fff',fillColor:'#22d3ee',fillOpacity:1,weight:2}).addTo(rfMap);
        } else {
            rfTraceMarker.setLatLng([pt.lat,pt.lon]);
        }
        if(origin){
            if(!rfTraceSightLine){
                rfTraceSightLine=L.polyline([[origin.lat,origin.lon],[pt.lat,pt.lon]],{color:'#ffffff',weight:1,dashArray:'4,6',opacity:0.5}).addTo(rfMap);
            } else {
                rfTraceSightLine.setLatLngs([[origin.lat,origin.lon],[pt.lat,pt.lon]]);
            }
        }
        const mColor=pt.margin>15?'#4ade80':pt.margin>6?'#eab308':pt.margin>0?'#f59e0b':'#ef4444';
        const status=pt.margin>15?'STRONG':pt.margin>6?'OK':pt.margin>0?'WEAK':'LOST';
        document.getElementById('rf-trace-info').innerHTML='<span style="color:'+mColor+'">'+status+'</span> '+pt.margin.toFixed(1)+' dB';
        document.getElementById('rf-trace-detail').textContent=
            pt.d.toFixed(2)+' km from origin · '+pt.rxPower.toFixed(1)+' dBm · '+pt.lat.toFixed(5)+', '+pt.lon.toFixed(5);
    }

    function updateMapMarkers(){
        if(!rfMap)return;
        if(txMarker&&txPos)txMarker.setLatLng([txPos.lat,txPos.lon]);
        if(rxMarker&&rxPos)rxMarker.setLatLng([rxPos.lat,rxPos.lon]);
        if(pathLine&&txPos&&rxPos)pathLine.setLatLngs([[txPos.lat,txPos.lon],[rxPos.lat,rxPos.lon]]);
    }

    function drawProfile(analysis,fMHz){
        var svg=document.getElementById('rf-profile-svg');
        if(!svg||!analysis)return;
        var pts=analysis.points,totalD=analysis.totalD;
        var W=700,H=200,PX=50,PY=16,PB=24;
        var plotW=W-2*PX,plotH=H-PY-PB;
        var elevs=pts.map(function(p){return p.elev;}),losVals=pts.map(function(p){return p.los;});
        var maxF=Math.max.apply(null,pts.map(function(p){return p.f1;}));
        var allH=elevs.concat(losVals);
        var minH=Math.min.apply(null,allH)-maxF*0.3,maxH=Math.max.apply(null,allH)+maxF*0.5;
        var rangeH=maxH-minH||1;
        function toX(d){return PX+(plotW*d)/totalD;}
        function toY(h){return PY+plotH*(1-(h-minH)/rangeH);}

        var html='<defs><linearGradient id="tg" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#1a3a2a" stop-opacity="0.9"/><stop offset="100%" stop-color="#0a1510" stop-opacity="0.5"/></linearGradient></defs>';

        var step=Math.pow(10,Math.floor(Math.log10(rangeH/4)));
        for(var v=Math.ceil(minH/step)*step;v<=maxH;v+=step){
            html+='<line x1="'+PX+'" y1="'+toY(v)+'" x2="'+(W-PX)+'" y2="'+toY(v)+'" stroke="#162030" stroke-width="0.3"/>';
            html+='<text x="'+(PX-4)+'" y="'+(toY(v)+3)+'" fill="#4a5f75" font-size="7" text-anchor="end" font-family="monospace">'+Math.round(v)+'m</text>';
        }
        var xStep=totalD>5000?1000:totalD>1000?500:100;
        for(var xv=0;xv<=totalD;xv+=xStep){
            html+='<line x1="'+toX(xv)+'" y1="'+PY+'" x2="'+toX(xv)+'" y2="'+(H-PB)+'" stroke="#162030" stroke-width="0.3"/>';
            var lbl=xv>=1000?(xv/1000).toFixed(1)+'km':xv+'m';
            html+='<text x="'+toX(xv)+'" y="'+(H-PB+12)+'" fill="#4a5f75" font-size="7" text-anchor="middle" font-family="monospace">'+lbl+'</text>';
        }

        var tPath=pts.map(function(p,i){return(i===0?'M':'L')+toX(p.dist).toFixed(1)+','+toY(p.elev).toFixed(1);}).join(' ');
        tPath+=' L'+toX(totalD).toFixed(1)+','+(H-PB)+' L'+PX+','+(H-PB)+' Z';
        html+='<path d="'+tPath+'" fill="url(#tg)"/>';

        for(var i=0;i<pts.length-1;i++){
            var c=pts[i].obstructed?'#f87171':pts[i].fresnelObs?'#eab308':'#4ade80';
            html+='<line x1="'+toX(pts[i].dist)+'" y1="'+toY(pts[i].elev)+'" x2="'+toX(pts[i+1].dist)+'" y2="'+toY(pts[i+1].elev)+'" stroke="'+c+'" stroke-width="1.5"/>';
        }

        var fTop=pts.map(function(p,i){return(i===0?'M':'L')+toX(p.dist).toFixed(1)+','+toY(p.los+p.f1).toFixed(1);}).join(' ');
        var fBot=pts.slice().reverse().map(function(p){return'L'+toX(p.dist).toFixed(1)+','+toY(p.los-p.f1).toFixed(1);}).join(' ');
        html+='<path d="'+fTop+' '+fBot+' Z" fill="rgba(34,211,238,0.04)" stroke="rgba(34,211,238,0.2)" stroke-width="0.5"/>';

        var f60Top=pts.map(function(p,i){return(i===0?'M':'L')+toX(p.dist).toFixed(1)+','+toY(p.los+p.f60).toFixed(1);}).join(' ');
        var f60Bot=pts.slice().reverse().map(function(p){return'L'+toX(p.dist).toFixed(1)+','+toY(p.los-p.f60).toFixed(1);}).join(' ');
        html+='<path d="'+f60Top+' '+f60Bot+' Z" fill="rgba(34,211,238,0.07)" stroke="rgba(34,211,238,0.35)" stroke-width="0.5" stroke-dasharray="3,2"/>';

        html+='<line x1="'+toX(0)+'" y1="'+toY(pts[0].los)+'" x2="'+toX(totalD)+'" y2="'+toY(pts[pts.length-1].los)+'" stroke="#22d3ee" stroke-width="1.5"/>';

        if(analysis.worstIdx>0&&analysis.worstV>-0.78){
            var wp=pts[analysis.worstIdx];
            html+='<line x1="'+toX(wp.dist)+'" y1="'+toY(wp.elev)+'" x2="'+toX(wp.dist)+'" y2="'+toY(wp.los)+'" stroke="#f87171" stroke-width="1" stroke-dasharray="2,2"/>';
            html+='<circle cx="'+toX(wp.dist)+'" cy="'+toY(wp.elev)+'" r="3" fill="#f87171"/>';
        }

        html+='<circle cx="'+toX(0)+'" cy="'+toY(pts[0].los)+'" r="5" fill="#22d3ee" stroke="#0b0f14" stroke-width="1.5"/>';
        html+='<text x="'+toX(0)+'" y="'+(toY(pts[0].los)-9)+'" fill="#22d3ee" font-size="9" text-anchor="middle" font-family="monospace" font-weight="700">TX</text>';
        html+='<circle cx="'+toX(totalD)+'" cy="'+toY(pts[pts.length-1].los)+'" r="5" fill="#2a9d8f" stroke="#0b0f14" stroke-width="1.5"/>';
        html+='<text x="'+toX(totalD)+'" y="'+(toY(pts[pts.length-1].los)-9)+'" fill="#2a9d8f" font-size="9" text-anchor="middle" font-family="monospace" font-weight="700">RX</text>';

        html+='<g transform="translate('+(PX+8)+','+(PY+4)+')">';
        html+='<rect x="0" y="0" width="8" height="3" fill="#4ade80"/><text x="12" y="3" fill="#4a5f75" font-size="6" font-family="monospace">Clear</text>';
        html+='<rect x="50" y="0" width="8" height="3" fill="#eab308"/><text x="62" y="3" fill="#4a5f75" font-size="6" font-family="monospace">Fresnel</text>';
        html+='<rect x="110" y="0" width="8" height="3" fill="#f87171"/><text x="122" y="3" fill="#4a5f75" font-size="6" font-family="monospace">Blocked</text>';
        html+='</g>';
        svg.innerHTML=html;
    }

    function renderResults(analysis,budget,proto,elevSrc){
        try{
        var distKm=analysis.totalDkm;
        var qMap={
            excellent:{c:'#4ade80',l:'EXCELLENT',i:'\u25C6\u25C6\u25C6\u25C6'},
            good:{c:'#4ade80',l:'GOOD',i:'\u25C6\u25C6\u25C6\u25C7'},
            marginal:{c:'#eab308',l:'MARGINAL',i:'\u25C6\u25C6\u25C7\u25C7'},
            weak:{c:'#f87171',l:'WEAK',i:'\u25C6\u25C7\u25C7\u25C7'},
            fail:{c:'#f87171',l:'NO LINK',i:'\u25C7\u25C7\u25C7\u25C7'},
        };
        var q=qMap[budget.quality]||qMap.fail;
        var badge=document.getElementById('rf-quality-badge');
        if(badge){
            badge.innerHTML='<span style="color:'+q.c+';letter-spacing:2px;">'+q.i+'</span> <span style="color:'+q.c+';">'+q.l+'</span>';
            badge.style.background=q.c+'12';
            badge.style.border='1px solid '+q.c+'40';
        }

        if(txPos)document.getElementById('rf-tx-coords').textContent=txPos.lat.toFixed(5)+', '+txPos.lon.toFixed(5);
        if(rxPos)document.getElementById('rf-rx-coords').textContent=rxPos.lat.toFixed(5)+', '+rxPos.lon.toFixed(5);
        var srcColor=elevSrc==='3DEP'?'#4ade80':elevSrc==='SRTM'?'#22d3ee':'#eab308';
        document.getElementById('rf-status').innerHTML='Distance: <span style="color:var(--accent-red)">'+distKm.toFixed(2)+' km</span> \u00B7 Elev: <span style="color:'+srcColor+'">'+elevSrc+'</span>';

        document.getElementById('rf-freq-label').textContent=proto.freq+' MHz';
        document.getElementById('rf-sens-label').textContent=proto.rxSens+' dBm';
        document.getElementById('rf-mod-label').textContent=proto.mod;

        var mColor=budget.margin>10?'ok':budget.margin>0?'warn':'warn';
        var mcColor=analysis.hasF60?'ok':analysis.hasLOS?'warn':'warn';
        var mc=analysis.minCl;
        document.getElementById('rf-results').innerHTML=
            '<div class="result-grid">'+
            '<div class="result-item"><div class="result-value">'+distKm.toFixed(2)+'</div><div class="result-label">Distance (km)</div></div>'+
            '<div class="result-item"><div class="result-value">'+budget.fLoss.toFixed(1)+'</div><div class="result-label">FSPL (dB)</div></div>'+
            '<div class="result-item"><div class="result-value '+(analysis.diffLoss>6?'warn':analysis.diffLoss>0?'warn':'ok')+'">'+analysis.diffLoss.toFixed(1)+'</div><div class="result-label">Diffraction (dB)</div></div>'+
            '<div class="result-item"><div class="result-value '+(budget.ok?'ok':'warn')+'">'+budget.rxPow.toFixed(1)+'</div><div class="result-label">RX Power (dBm)</div></div>'+
            '<div class="result-item"><div class="result-value '+mColor+'">'+budget.margin.toFixed(1)+'</div><div class="result-label">Link Margin (dB)</div></div>'+
            '<div class="result-item"><div class="result-value '+mcColor+'">'+mc.toFixed(1)+'</div><div class="result-label">Min Clearance (m)</div></div>'+
            '</div>';

        var fade=getVal('rf-fade-margin'),txP=getVal('rf-tx-power'),txG=getVal('rf-tx-gain'),rxG=getVal('rf-rx-gain');
        var rows=[
            ['TX Power','+'+txP,'dBm','#4ade80'],
            ['TX Antenna Gain','+'+txG,'dBi','#4ade80'],
            ['RX Antenna Gain','+'+rxG,'dBi','#4ade80'],
            ['Free Space Path Loss','-'+budget.fLoss.toFixed(1),'dB','#f87171'],
            ['Knife-Edge Diffraction','-'+analysis.diffLoss.toFixed(1),'dB',analysis.diffLoss>0?'#f87171':'#4a5f75'],
            ['Fade Margin','-'+fade,'dB','#eab308'],
            null,
            ['Received Power',budget.rxPow.toFixed(1),'dBm','#22d3ee'],
            ['RX Sensitivity',''+proto.rxSens,'dBm','#4a5f75'],
            ['Link Margin',budget.margin.toFixed(1),'dB',budget.margin>0?'#4ade80':'#f87171'],
        ];
        var bHtml='<div style="font-size:11px;text-transform:uppercase;letter-spacing:0.08em;color:var(--text-faint);margin-bottom:10px;">Link Budget Breakdown</div>';
        rows.forEach(function(r){
            if(!r){bHtml+='<div style="border-top:1px solid var(--border-color);margin:6px 0;"></div>';return;}
            bHtml+='<div style="display:flex;justify-content:space-between;padding:3px 0;border-bottom:1px solid rgba(255,255,255,0.02);"><span style="color:var(--text-muted);">'+r[0]+'</span><span style="color:'+r[3]+';font-weight:600;">'+r[1]+' '+r[2]+'</span></div>';
        });
        document.getElementById('rf-budget').innerHTML=bHtml;

        var samples=getVal('rf-samples');
        var dHtml='<div style="font-size:11px;text-transform:uppercase;letter-spacing:0.08em;color:var(--text-faint);margin-bottom:10px;">Path Details</div>';
        dHtml+='<div style="display:grid;grid-template-columns:1fr 1fr;gap:4px;font-size:11px;">';
        var detRows=[
            ['LOS Clear',analysis.hasLOS?'Yes':'No',analysis.hasLOS?'#4ade80':'#f87171'],
            ['60% Fresnel Clear',analysis.hasF60?'Yes':'No',analysis.hasF60?'#4ade80':'#f87171'],
            ['LOS Obstructions',analysis.losObs+' / '+(samples+1),'var(--text-main)'],
            ['Fresnel Obstructions',analysis.fObs+' / '+(samples+1),'var(--text-main)'],
            ['Min Clearance',mc.toFixed(1)+' m','var(--text-main)'],
            ['Worst \u03BD',analysis.worstV.toFixed(3),'var(--text-main)'],
            ['Elevation Source',elevSrc,'var(--text-main)'],
            ['Path Samples',(samples+1)+' points','var(--text-main)'],
        ];
        detRows.forEach(function(r){dHtml+='<span style="color:var(--text-muted);">'+r[0]+'</span><span style="color:'+r[2]+';">'+r[1]+'</span>';});
        dHtml+='</div>';
        document.getElementById('rf-path-details').innerHTML=dHtml;
        }catch(renderErr){
            console.error('renderResults error:',renderErr);
            document.getElementById('rf-results').innerHTML='<div style="color:#f87171;font-size:11px;padding:8px;">Render error: '+renderErr.message+'</div>';
        }
    }

    var computing=false;
    async function computeRFTerrain(){
        computing=false; // Reset in case stuck from previous run
        if(!txPos||!rxPos){document.getElementById('rf-status').textContent='Place TX and RX first';return;}
        computing=true;
        document.getElementById('rf-status').innerHTML='<span style="color:#eab308;">\u25CF computing...</span>';
        document.getElementById('rf-results').innerHTML='<div style="color:#eab308;font-size:10px;padding:4px;">Computing...</div>';
        try{
            var proto=getProto(),txH=getVal('rf-tx-height'),rxH=getVal('rf-rx-height');
            var txP=getVal('rf-tx-power'),txG=getVal('rf-tx-gain'),rxG=getVal('rf-rx-gain');
            var fade=getVal('rf-fade-margin'),samples=getVal('rf-samples');
            var pts=interpolatePoints(txPos.lat,txPos.lon,rxPos.lat,rxPos.lon,samples);
            var elev;
            try{
                elev=await getElevations(pts);
            }catch(elevErr){
                elev={elevations:pts.map(function(){return 0;}),source:'Flat (API unavailable)'};
            }
            var dists=pts.map(function(p){return haversine(txPos.lat,txPos.lon,p.lat,p.lon);});
            var analysis=analyzePath(elev.elevations,dists,txH,rxH,proto.freq);
            var budget=linkBudget(analysis,txP,proto.rxSens,txG,rxG,proto.freq,fade);
            updateMapMarkers();
            drawProfile(analysis,proto.freq);
            var mc=budget.margin>10?'#4ade80':budget.margin>0?'#eab308':'#f87171';
            document.getElementById('rf-results').innerHTML='<div style="color:#ffffff;font-size:16px;padding:12px;font-weight:700;line-height:1.6;">'+budget.quality.toUpperCase()+' — '+budget.margin.toFixed(1)+' dB margin<br><span style="font-size:13px;font-weight:400;color:#c9d1d9;">'+analysis.totalDkm.toFixed(2)+' km | FSPL '+budget.fLoss.toFixed(1)+' dB | RX '+budget.rxPow.toFixed(1)+' dBm</span></div>';
            document.getElementById('rf-budget').innerHTML='<div style="color:#c9d1d9;font-size:13px;padding:12px;line-height:1.8;">TX +'+txP+' dBm | Gain +'+(txG+rxG)+' dBi<br>FSPL -'+budget.fLoss.toFixed(1)+' dB | Diffr -'+analysis.diffLoss.toFixed(1)+' dB | Fade -'+fade+' dB<br><span style="color:'+mc+';font-weight:700;">Margin '+budget.margin.toFixed(1)+' dB | RX '+budget.rxPow.toFixed(1)+' dBm | Sens '+proto.rxSens+' dBm</span></div>';
            document.getElementById('rf-path-details').innerHTML='<div style="color:#c9d1d9;font-size:13px;padding:12px;">LOS '+(analysis.hasLOS?'<span style=color:#4ade80>Clear</span>':'<span style=color:#f87171>Blocked</span>')+' | Fresnel '+(analysis.hasF60?'<span style=color:#4ade80>Clear</span>':'<span style=color:#f87171>Obstructed</span>')+' | Clearance '+analysis.minCl.toFixed(1)+'m | '+elev.source+'</div>';
            // Scroll results into view
            document.getElementById('rf-results').scrollIntoView({behavior:'smooth',block:'nearest'});
            try{
                rfBuildTraceFromOrigin(txPos.lat,txPos.lon,[{lat:rxPos.lat,lon:rxPos.lon}],proto,txP,txG,rxG,fade);
            }catch(traceErr){console.error('Trace error:',traceErr);}
            if(rfHeatEnabled)rfBuildHeatmap();
        }catch(e){
            document.getElementById('rf-status').innerHTML='<span style="color:#f87171;">\u26A0 '+e.message+'</span>';
            document.getElementById('rf-results').innerHTML='<div style="color:#f87171;font-size:10px;padding:8px;">Error: '+e.message+'<br>Stack: '+String(e.stack).substring(0,200)+'</div>';
            console.error('RF compute error:',e);
        }
        computing=false;
    }

    function scheduleCompute(){
        // Manual only — triggered by Calculate button
        clearTimeout(rfTimer);
        rfTimer=setTimeout(computeRFTerrain,100);
    }

    // rfCompute is the public function called by the Calculate button
    window.rfCompute=function(){
        if(rfAdvanced&&rfAdvMode==='waypoint'&&rfWaypoints.length>=2){
            // Hide basic results, show advanced
            document.getElementById('rf-results').innerHTML='';
            document.getElementById('rf-budget').innerHTML='';
            document.getElementById('rf-path-details').innerHTML='';
            var svg=document.getElementById('rf-profile-svg');if(svg)svg.innerHTML='';
            var qb=document.getElementById('rf-quality-badge');if(qb)qb.innerHTML='';
            rfComputeMultiLeg();
            return;
        }
        if(rfAdvanced&&rfAdvMode==='repeater'&&rfRepeaterPts.length===3){
            document.getElementById('rf-results').innerHTML='';
            document.getElementById('rf-budget').innerHTML='';
            document.getElementById('rf-path-details').innerHTML='';
            var svg=document.getElementById('rf-profile-svg');if(svg)svg.innerHTML='';
            var qb=document.getElementById('rf-quality-badge');if(qb)qb.innerHTML='';
            rfComputeRepeater();
            return;
        }
        if(!txPos||!rxPos){
            document.getElementById('rf-status').textContent='Place TX and RX markers first';
            return;
        }
        // Simple mode — hide advanced results
        document.getElementById('rf-advanced-results').style.display='none';
        rfClearTrace();
        scheduleCompute();
    };

    // ── Advanced mode state ──
    let rfAdvanced=false;
    let rfAdvMode='waypoint'; // 'waypoint' or 'repeater'
    let rfWaypoints=[]; // [{lat,lon,marker,label}]
    let rfWaypointLines=[];
    let rfRepeaterPts=[]; // [pilot, repeater, target]
    let rfRepeaterMarkers=[];

    window.rfToggleAdvanced=function(){
        rfAdvanced=document.getElementById('rf-advanced-toggle').checked;
        document.getElementById('rf-advanced-panel').style.display=rfAdvanced?'block':'none';
        if(!rfAdvanced){
            // Revert to simple mode
            rfClearWaypoints();
            rfClearRepeater();
            rfClickState=0;
        } else {
            rfSetMode(rfAdvMode);
        }
    };

    window.rfSetMode=function(mode){
        rfAdvMode=mode;
        document.querySelectorAll('.rf-mode-btn').forEach(b=>{
            const active=b.dataset.mode===mode;
            b.style.background=active?'rgba(34,211,238,0.08)':'none';
            b.style.color=active?'var(--accent-red)':'var(--text-muted)';
        });
        document.getElementById('rf-waypoint-panel').style.display=mode==='waypoint'?'block':'none';
        document.getElementById('rf-repeater-panel').style.display=mode==='repeater'?'block':'none';
        rfClearWaypoints();
        rfClearRepeater();
    };

    // ── Multi-Leg Waypoints ──
    function rfWaypointClick(lat,lon){
        const L=window.L;
        const n=rfWaypoints.length;
        const colors=['#22d3ee','#f59e0b','#a78bfa','#f87171','#4ade80','#fb923c','#38bdf8','#e879f9'];
        const label=n===0?'TX':('WP'+n);
        const color=colors[n%colors.length];
        const icon=L.divIcon({className:'',html:'<div style="width:14px;height:14px;border-radius:50%;background:'+color+';border:2px solid #0b0f14;box-shadow:0 0 6px '+color+'88;"></div>',iconSize:[14,14],iconAnchor:[7,7]});
        const marker=L.marker([lat,lon],{draggable:true,icon:icon}).addTo(rfMap);
        marker.bindTooltip(label,{permanent:true,direction:'top',offset:[0,-10],className:'rf-wp-tooltip'});
        marker.on('dragend',function(){
            const p=marker.getLatLng();
            rfWaypoints[n].lat=p.lat;
            rfWaypoints[n].lon=p.lng;
            rfUpdateWaypointList();
            rfDrawWaypointLines();
        });
        rfWaypoints.push({lat:lat,lon:lon,marker:marker,label:label});
        // Draw connecting lines
        rfDrawWaypointLines();
        rfUpdateWaypointList();
    }

    function rfDrawWaypointLines(){
        const L=window.L;
        rfWaypointLines.forEach(l=>rfMap.removeLayer(l));
        rfWaypointLines=[];
        for(let i=0;i<rfWaypoints.length-1;i++){
            const a=rfWaypoints[i],b=rfWaypoints[i+1];
            const line=L.polyline([[a.lat,a.lon],[b.lat,b.lon]],{color:'#22d3ee',weight:2,dashArray:'6,4',opacity:0.7}).addTo(rfMap);
            rfWaypointLines.push(line);
        }
    }

    function rfUpdateWaypointList(){
        const el=document.getElementById('rf-waypoint-list');
        if(!el)return;
        if(rfWaypoints.length===0){el.innerHTML='<div style="font-size:11px; color:var(--text-faint);">No waypoints placed. Click map to add.</div>';return;}
        let html='';
        rfWaypoints.forEach((wp,i)=>{
            const label=i===0?'TX':(i===rfWaypoints.length-1&&rfWaypoints.length>1?'RX (WP'+i+')':'WP'+i);
            html+='<div style="display:flex; align-items:center; gap:8px; padding:3px 0; font-size:11px;">';
            html+='<span style="color:var(--accent-red); font-weight:600; min-width:35px;">'+label+'</span>';
            html+='<span style="color:var(--text-muted); font-family:var(--font-mono);">'+wp.lat.toFixed(5)+', '+wp.lon.toFixed(5)+'</span>';
            if(i>0){
                const prev=rfWaypoints[i-1];
                const d=haversine(prev.lat,prev.lon,wp.lat,wp.lon);
                html+='<span style="color:var(--text-faint); font-size:10px;">'+formatDist(d)+'</span>';
            }
            html+='</div>';
        });
        el.innerHTML=html;
    }

    window.rfClearWaypoints=function(){
        rfWaypoints.forEach(wp=>rfMap&&rfMap.removeLayer(wp.marker));
        rfWaypointLines.forEach(l=>rfMap&&rfMap.removeLayer(l));
        rfWaypoints=[];
        rfWaypointLines=[];
        rfUpdateWaypointList();
        document.getElementById('rf-advanced-results').style.display='none';
        document.getElementById('rf-advanced-results').innerHTML='';
    };

    window.rfUndoWaypoint=function(){
        if(rfWaypoints.length===0)return;
        const last=rfWaypoints.pop();
        if(rfMap)rfMap.removeLayer(last.marker);
        rfDrawWaypointLines();
        rfUpdateWaypointList();
    };

    function formatDist(m){return m>=1000?(m/1000).toFixed(2)+' km':Math.round(m)+' m';}

    // ── Repeater Relay ──
    function rfRepeaterClick(lat,lon){
        const L=window.L;
        const n=rfRepeaterPts.length;
        const labels=['Pilot (GCS)','Repeater','Target'];
        const colors=['#22d3ee','#f59e0b','#2a9d8f'];
        if(n>=3){rfClearRepeater();}
        const icon=L.divIcon({className:'',html:'<div style="width:16px;height:16px;border-radius:50%;background:'+colors[n]+';border:3px solid #0b0f14;box-shadow:0 0 8px '+colors[n]+'88;"></div>',iconSize:[16,16],iconAnchor:[8,8]});
        const marker=L.marker([lat,lon],{draggable:true,icon:icon}).addTo(rfMap);
        marker.bindTooltip(labels[n],{permanent:true,direction:'top',offset:[0,-10],className:'rf-wp-tooltip'});
        marker.on('dragend',function(){
            rfRepeaterPts[n]={lat:marker.getLatLng().lat,lon:marker.getLatLng().lng};
            rfUpdateRepeaterStatus();
        });
        rfRepeaterPts.push({lat:lat,lon:lon});
        rfRepeaterMarkers.push(marker);
        rfUpdateRepeaterStatus();

        // Draw lines when we have 2+ points
        rfDrawRepeaterLines();
    }

    function rfDrawRepeaterLines(){
        const L=window.L;
        // Remove old lines
        rfWaypointLines.forEach(l=>rfMap.removeLayer(l));
        rfWaypointLines=[];
        if(rfRepeaterPts.length>=2){
            const line1=L.polyline([[rfRepeaterPts[0].lat,rfRepeaterPts[0].lon],[rfRepeaterPts[1].lat,rfRepeaterPts[1].lon]],{color:'#22d3ee',weight:2,dashArray:'6,4',opacity:0.7}).addTo(rfMap);
            rfWaypointLines.push(line1);
        }
        if(rfRepeaterPts.length>=3){
            const line2=L.polyline([[rfRepeaterPts[1].lat,rfRepeaterPts[1].lon],[rfRepeaterPts[2].lat,rfRepeaterPts[2].lon]],{color:'#f59e0b',weight:2,dashArray:'6,4',opacity:0.7}).addTo(rfMap);
            rfWaypointLines.push(line2);
        }
    }

    function rfUpdateRepeaterStatus(){
        const el=document.getElementById('rf-repeater-status');
        if(!el)return;
        const labels=['Pilot','Repeater','Target'];
        let html='';
        rfRepeaterPts.forEach((p,i)=>{
            html+='<span style="color:var(--accent-red); font-weight:600;">'+labels[i]+':</span> '+p.lat.toFixed(5)+', '+p.lon.toFixed(5)+'&nbsp;&nbsp;';
        });
        if(rfRepeaterPts.length<3) html+='<span style="color:var(--text-faint);">Click map to place '+(labels[rfRepeaterPts.length]||'')+'</span>';
        el.innerHTML=html;
    }

    function rfClearRepeater(){
        rfRepeaterMarkers.forEach(m=>rfMap&&rfMap.removeLayer(m));
        rfWaypointLines.forEach(l=>rfMap&&rfMap.removeLayer(l));
        rfRepeaterPts=[];
        rfRepeaterMarkers=[];
        rfWaypointLines=[];
        rfUpdateRepeaterStatus();
        document.getElementById('rf-advanced-results').style.display='none';
    }

    // ── Multi-leg compute ──
    async function rfComputeMultiLeg(){
        if(rfWaypoints.length<2)return;
        const proto=getProto();
        const txP=getVal('rf-tx-power'),txG=getVal('rf-tx-gain'),rxG=getVal('rf-rx-gain');
        const txH=getVal('rf-tx-height'),rxH=getVal('rf-rx-height');
        const fade=getVal('rf-fade-margin'),samples=getVal('rf-samples');
        document.getElementById('rf-status').innerHTML='<span style="color:#eab308;">\u25CF computing terrain...</span>';
        const pilot=rfWaypoints[0];
        let links=[],worstMargin=999,farthestIdx=0,farthestDist=0;
        for(let i=1;i<rfWaypoints.length;i++){
            const wp=rfWaypoints[i];
            const result=await computeFullLink(pilot.lat,pilot.lon,txH,wp.lat,wp.lon,rxH,proto,txP,txG,rxG,fade,samples);
            const m=result.budget.margin,d=result.analysis.totalDkm;
            if(m<worstMargin)worstMargin=m;
            if(d>farthestDist){farthestDist=d;farthestIdx=i-1;}
            links.push({to:wp.label||('WP'+i),distKm:d,margin:m,quality:result.budget.quality,
                rxPower:result.budget.rxPow,fsplDb:result.budget.fLoss,diffLoss:result.analysis.diffLoss,
                hasLOS:result.analysis.hasLOS,result:result});
        }
        drawProfile(links[farthestIdx].result.analysis,proto.freq);
        let totalRoute=0;
        for(let i=0;i<rfWaypoints.length-1;i++)totalRoute+=haversine(rfWaypoints[i].lat,rfWaypoints[i].lon,rfWaypoints[i+1].lat,rfWaypoints[i+1].lon);
        const oq=worstMargin>20?'excellent':worstMargin>10?'good':worstMargin>3?'marginal':worstMargin>0?'weak':'fail';
        const qc=oq==='excellent'||oq==='good'?'#4ade80':oq==='marginal'?'#eab308':'#f87171';
        let html='<div style="margin-bottom:6px;font-size:12px;font-weight:700;color:var(--text-main);">PROPAGATION FROM PILOT</div>';
        html+='<div style="font-size:10px;color:var(--text-muted);margin-bottom:6px;">Route: '+(totalRoute/1000).toFixed(2)+' km \u00B7 Farthest: '+farthestDist.toFixed(2)+' km \u00B7 Profile: Pilot\u2192'+links[farthestIdx].to+'</div>';
        html+='<div style="margin-bottom:8px;padding:6px 10px;border-radius:var(--radius-sm);background:'+qc+'15;border:1px solid '+qc+'40;color:'+qc+';font-size:11px;font-weight:600;">Weakest: '+oq.toUpperCase()+' \u2014 '+worstMargin.toFixed(1)+' dB</div>';
        html+='<table style="width:100%;font-size:10px;border-collapse:collapse;">';
        html+='<tr style="color:var(--text-faint);text-transform:uppercase;font-size:9px;"><th style="text-align:left;padding:3px;">Pilot\u2192</th><th>Dist</th><th>FSPL</th><th>Diffr</th><th>RX</th><th>Margin</th><th>LOS</th></tr>';
        links.forEach(function(l,idx){
            var lc=l.quality==='excellent'||l.quality==='good'?'#4ade80':l.quality==='marginal'?'#eab308':'#f87171';
            html+='<tr onclick="rfShowWpProfile('+idx+')" style="border-top:1px solid var(--border-color);cursor:pointer;" onmouseover="this.style.background=\'rgba(34,211,238,0.05)\'" onmouseout="this.style.background=\'none\'"><td style="padding:3px;color:var(--text-muted);">'+l.to+' \u203A</td>';
            html+='<td style="text-align:center;color:var(--text-muted);">'+l.distKm.toFixed(2)+'</td>';
            html+='<td style="text-align:center;color:var(--text-muted);">'+l.fsplDb.toFixed(1)+'</td>';
            html+='<td style="text-align:center;color:'+(l.diffLoss>3?'#f87171':'var(--text-muted)')+';">'+l.diffLoss.toFixed(1)+'</td>';
            html+='<td style="text-align:center;color:var(--accent-red);">'+l.rxPower.toFixed(1)+'</td>';
            html+='<td style="text-align:center;color:'+lc+';font-weight:600;">'+l.margin.toFixed(1)+'</td>';
            html+='<td style="text-align:center;color:'+(l.hasLOS?'#4ade80':'#f87171')+';">'+(l.hasLOS?'\u2713':'\u2717')+'</td></tr>';
        });
        html+='</table>';
        // Store links for click handler
        window._rfWpLinks=links;
        var maxD=Math.max.apply(null,links.map(function(l){return l.distKm;}));
        html+='<div style="margin-top:8px;">';
        links.sort(function(a,b){return a.distKm-b.distKm;}).forEach(function(l){
            var pct=maxD>0?(l.distKm/maxD*100):100;
            var lc=l.quality==='excellent'||l.quality==='good'?'#4ade80':l.quality==='marginal'?'#eab308':'#f87171';
            html+='<div style="display:flex;align-items:center;gap:6px;margin-bottom:3px;"><span style="min-width:35px;font-size:9px;color:var(--text-muted);">'+l.to+'</span><div style="flex:1;height:6px;background:var(--bg-dark);border-radius:3px;overflow:hidden;"><div style="width:'+pct+'%;height:100%;background:'+lc+';border-radius:3px;"></div></div><span style="min-width:50px;font-size:9px;color:'+lc+';font-weight:600;text-align:right;">'+l.margin.toFixed(1)+' dB</span></div>';
        });
        html+='</div>';
        document.getElementById('rf-advanced-results').innerHTML=html;
        document.getElementById('rf-advanced-results').style.display='block';
        document.getElementById('rf-status').innerHTML='<span style="color:#4ade80;">\u25CF '+links.length+' waypoints with terrain</span>';
        var wpCoords=rfWaypoints.slice(1).map(function(w){return{lat:w.lat,lon:w.lon};});
        rfBuildTraceFromOrigin(pilot.lat,pilot.lon,wpCoords,proto,txP,txG,rxG,fade);
        if(rfHeatEnabled)rfBuildHeatmap();
    }


    // ── Repeater relay compute — repeater is RF origin ──
    async function rfComputeRepeater(){
        if(rfRepeaterPts.length<3)return;
        const c2Proto=getProto(),c2Power=getVal('rf-tx-power'),pilotH=getVal('rf-tx-height');
        const repeaterH=parseFloat(document.getElementById('rf-repeater-height').value)||80;
        const rPower=parseFloat(document.getElementById('rf-repeater-power').value)||27;
        const fwdKey=document.getElementById('rf-repeater-fwd-proto').value;
        const fwdProto=fwdKey==='same'?c2Proto:(RF_PROTOCOLS[fwdKey]||c2Proto);
        const targetH=parseFloat(document.getElementById('rf-target-height').value)||50;
        const fade=getVal('rf-fade-margin'),txG=getVal('rf-tx-gain'),rxG=getVal('rf-rx-gain');
        const samples=getVal('rf-samples');
        document.getElementById('rf-status').innerHTML='<span style="color:#eab308;">\u25cf computing relay terrain...</span>';
        var opLink=await computeFullLink(rfRepeaterPts[1].lat,rfRepeaterPts[1].lon,repeaterH,rfRepeaterPts[2].lat,rfRepeaterPts[2].lon,targetH,fwdProto,rPower,txG,rxG,fade,samples);
        var c2Link=await computeFullLink(rfRepeaterPts[0].lat,rfRepeaterPts[0].lon,pilotH,rfRepeaterPts[1].lat,rfRepeaterPts[1].lon,repeaterH,c2Proto,c2Power,txG,rxG,fade,samples);
        var directLink=await computeFullLink(rfRepeaterPts[0].lat,rfRepeaterPts[0].lon,pilotH,rfRepeaterPts[2].lat,rfRepeaterPts[2].lon,targetH,c2Proto,c2Power,txG,rxG,fade,samples);
        drawProfile(opLink.analysis,fwdProto.freq);
        var m1=c2Link.budget.margin,m2=opLink.budget.margin,mD=directLink.budget.margin;
        var worst=Math.min(m1,m2);
        var opC=m2>10?'#4ade80':m2>0?'#eab308':'#f87171';
        var c2C=m1>10?'#4ade80':m1>0?'#eab308':'#f87171';
        var relayC=worst>10?'#4ade80':worst>0?'#eab308':'#f87171';
        var directC=mD>10?'#4ade80':mD>0?'#eab308':'#f87171';
        var html='<div style="margin-bottom:8px;font-size:12px;font-weight:700;color:var(--text-main);">REPEATER RELAY \u2014 RF Origin: <span style="color:#f59e0b;">Repeater @ '+repeaterH+'m</span></div>';
        html+='<div style="margin-bottom:8px;padding:10px;border-radius:var(--radius-sm);background:'+opC+'10;border:1px solid '+opC+'40;">';
        html+='<div style="font-size:9px;color:var(--text-muted);text-transform:uppercase;">Operational Link (Repeater \u2192 Target)</div>';
        html+='<div style="display:flex;justify-content:space-between;align-items:center;"><span style="font-size:10px;color:var(--text-muted);">'+fwdProto.freq+' MHz \u00b7 '+rPower+' dBm \u00b7 '+opLink.analysis.totalDkm.toFixed(2)+' km \u00b7 '+(opLink.analysis.hasLOS?'LOS clear':'LOS blocked')+'</span><span style="font-size:18px;font-weight:700;color:'+opC+';">'+m2.toFixed(1)+' dB</span></div></div>';
        html+='<div style="margin-bottom:8px;padding:8px 10px;border-radius:var(--radius-sm);background:var(--bg-dark);border:1px solid var(--border-color);">';
        html+='<div style="font-size:9px;color:var(--text-muted);text-transform:uppercase;">C2 Uplink (Pilot \u2192 Repeater)</div>';
        html+='<div style="display:flex;justify-content:space-between;align-items:center;"><span style="font-size:10px;color:var(--text-muted);">'+c2Proto.freq+' MHz \u00b7 '+c2Power+' dBm \u00b7 '+c2Link.analysis.totalDkm.toFixed(2)+' km</span><span style="font-size:14px;font-weight:700;color:'+c2C+';">'+m1.toFixed(1)+' dB</span></div></div>';
        html+='<div style="display:flex;gap:6px;margin-bottom:8px;">';
        html+='<div style="flex:1;padding:6px;border-radius:var(--radius-sm);background:'+relayC+'10;border:1px solid '+relayC+'30;text-align:center;"><div style="font-size:8px;color:var(--text-faint);text-transform:uppercase;">Via Repeater</div><div style="font-size:14px;font-weight:700;color:'+relayC+';">'+worst.toFixed(1)+' dB</div></div>';
        html+='<div style="flex:1;padding:6px;border-radius:var(--radius-sm);background:'+directC+'10;border:1px solid '+directC+'30;text-align:center;"><div style="font-size:8px;color:var(--text-faint);text-transform:uppercase;">Direct</div><div style="font-size:14px;font-weight:700;color:'+directC+';">'+mD.toFixed(1)+' dB</div></div></div>';
        var adv=worst-mD;
        if(adv>0)html+='<div style="padding:5px 8px;font-size:10px;color:#4ade80;background:rgba(74,222,128,0.06);border-radius:var(--radius-sm);">Repeater gains '+adv.toFixed(1)+' dB</div>';
        else if(adv<-3)html+='<div style="padding:5px 8px;font-size:10px;color:#eab308;background:rgba(234,179,8,0.06);border-radius:var(--radius-sm);">Direct stronger by '+Math.abs(adv).toFixed(1)+' dB</div>';
        document.getElementById('rf-advanced-results').innerHTML=html;
        document.getElementById('rf-advanced-results').style.display='block';
        document.getElementById('rf-status').innerHTML='<span style="color:#4ade80;">\u25cf relay computed with terrain</span>';
        rfBuildTraceFromOrigin(rfRepeaterPts[1].lat,rfRepeaterPts[1].lon,[{lat:rfRepeaterPts[2].lat,lon:rfRepeaterPts[2].lon}],fwdProto,rPower,txG,rxG,fade);
        if(rfHeatEnabled)rfBuildHeatmap();
    }
    // No auto-compute on input changes — user clicks Calculate
    // ['rf-protocol','rf-tx-power',...].forEach — removed

    document.addEventListener('toolchange', function(event) {
        if (event.detail === 'rf-terrain') {
            initRFMap();
            if (rfMap) setTimeout(function(){ rfMap.invalidateSize(); }, 50);
        }
    });
    // ── Export Report ──
    // ── RF Coverage Heatmap for terrain tool ──
    var rfHeatLayer=null;
    var rfHeatEnabled=false;

    window.rfToggleHeatmap=function(){
        rfHeatEnabled=document.getElementById('rf-heatmap-toggle').checked;
        if(!rfHeatEnabled){rfClearHeatmap();return;}
        rfBuildHeatmap();
    };

    function rfClearHeatmap(){
        if(rfHeatLayer&&rfMap){rfMap.removeLayer(rfHeatLayer);rfHeatLayer=null;}
    }

    function rfBuildHeatmap(){
        rfClearHeatmap();
        if(!rfMap)return;
        var L=window.L;
        // Determine RF origin(s)
        var origins=[];
        if(rfAdvanced&&rfAdvMode==='repeater'&&rfRepeaterPts.length>=2){
            origins.push({lat:rfRepeaterPts[1].lat,lon:rfRepeaterPts[1].lon}); // repeater is origin
        } else if(rfAdvanced&&rfAdvMode==='waypoint'&&rfWaypoints.length>=1){
            origins.push({lat:rfWaypoints[0].lat,lon:rfWaypoints[0].lon}); // pilot is origin
        } else if(txPos){
            origins.push({lat:txPos.lat,lon:txPos.lon}); // TX is origin
        }
        if(origins.length===0)return;
        var proto=getProto();
        var txP=getVal('rf-tx-power'),txG=getVal('rf-tx-gain'),rxG=getVal('rf-rx-gain');
        var fade=getVal('rf-fade-margin');
        var bounds=rfMap.getBounds();
        var sw=bounds.getSouthWest(),ne=bounds.getNorthEast();
        var GRID=40;
        var latStep=(ne.lat-sw.lat)/GRID,lonStep=(ne.lng-sw.lng)/GRID;
        var rects=[];
        for(var r=0;r<GRID;r++){
            for(var c=0;c<GRID;c++){
                var lat=sw.lat+r*latStep+latStep/2;
                var lon=sw.lng+c*lonStep+lonStep/2;
                var bestMargin=-999;
                for(var i=0;i<origins.length;i++){
                    var d=haversine(origins[i].lat,origins[i].lon,lat,lon)/1000;
                    if(d<0.001)d=0.001;
                    var fspl=20*Math.log10(d)+20*Math.log10(proto.freq)+32.44;
                    var margin=txP+txG+rxG-fspl-proto.rxSens-fade;
                    if(margin>bestMargin)bestMargin=margin;
                }
                var color,opacity;
                if(bestMargin>20){color='#4ade80';opacity=0.12;}
                else if(bestMargin>10){color='#22d3ee';opacity=0.15;}
                else if(bestMargin>6){color='#eab308';opacity=0.2;}
                else if(bestMargin>0){color='#f59e0b';opacity=0.25;}
                else{color='#ef4444';opacity=0.28;}
                rects.push(L.rectangle([[sw.lat+r*latStep,sw.lng+c*lonStep],[sw.lat+(r+1)*latStep,sw.lng+(c+1)*lonStep]],{color:'none',fillColor:color,fillOpacity:opacity,weight:0}));
            }
        }
        rfHeatLayer=L.layerGroup(rects).addTo(rfMap);
        rfHeatLayer.eachLayer(function(l){l.bringToBack();});
    }

    // ── Clickable waypoint profile viewer ──
    var rfWpSightLine=null;
    window.rfShowWpProfile=async function(idx){
        var links=window._rfWpLinks;
        if(!links||!links[idx])return;
        var link=links[idx];
        var proto=getProto();
        var pilot=rfWaypoints[0];
        var wp=rfWaypoints[idx+1];
        // Compute terrain on-demand if not already done
        if(!link.result||!link.result.analysis){
            document.getElementById('rf-status').innerHTML='<span style="color:#eab308;">Computing terrain for '+link.to+'...</span>';
            try{
                var txH=getVal('rf-tx-height'),rxH=getVal('rf-rx-height');
                var txP=getVal('rf-tx-power'),txG=getVal('rf-tx-gain'),rxG=getVal('rf-rx-gain');
                var fade=getVal('rf-fade-margin'),samples=getVal('rf-samples');
                var result=await computeFullLink(pilot.lat,pilot.lon,txH,wp.lat,wp.lon,rxH,proto,txP,txG,rxG,fade,samples);
                link.result=result;
                link.margin=result.budget.margin;link.hasLOS=result.analysis.hasLOS;
                link.diffLoss=result.analysis.diffLoss;link.fsplDb=result.budget.fLoss;
                link.rxPower=result.budget.rxPow;link.quality=result.budget.quality;
            }catch(e){
                document.getElementById('rf-status').innerHTML='<span style="color:#f87171;">Terrain failed for '+link.to+'</span>';
                return;
            }
        }
        var analysis=link.result.analysis;
        drawProfile(analysis,proto.freq);
        // Show sight line from pilot to this waypoint on the map
        if(rfWpSightLine&&rfMap){rfMap.removeLayer(rfWpSightLine);rfWpSightLine=null;}
        var L=window.L;
        if(L&&rfMap&&pilot&&wp){
            rfWpSightLine=L.polyline([[pilot.lat,pilot.lon],[wp.lat,wp.lon]],{color:'#ffffff',weight:2,dashArray:'4,6',opacity:0.6}).addTo(rfMap);
            rfMap.fitBounds([[pilot.lat,pilot.lon],[wp.lat,wp.lon]],{padding:[30,30]});
        }
        var mc=link.margin>10?'#4ade80':link.margin>0?'#eab308':'#f87171';
        document.getElementById('rf-status').innerHTML='Profile: Pilot \u2192 '+link.to+' \u2014 <span style="color:'+mc+';">'+link.margin.toFixed(1)+' dB</span> \u2014 '+(link.hasLOS?'LOS clear':'LOS blocked');
    };

    window.rfExportReport=function(){
        // Capture map as image via Leaflet
        var mapCanvas=null;
        try{
            var mapEl=document.getElementById('rf-map');
            // Use html2canvas-like approach: just grab the tile container
            // For simplicity, we'll serialize the map state as coordinates
        }catch(e){}

        // Gather all data
        var proto=getProto();
        var now=new Date().toISOString().split('T')[0]+' '+new Date().toTimeString().split(' ')[0];
        var svgEl=document.getElementById('rf-profile-svg');
        var svgHtml=svgEl?svgEl.outerHTML:'';
        var resultsHtml=document.getElementById('rf-results').innerHTML||'';
        var budgetHtml=document.getElementById('rf-budget').innerHTML||'';
        var pathHtml=document.getElementById('rf-path-details').innerHTML||'';
        var advancedHtml=document.getElementById('rf-advanced-results').innerHTML||'';
        var traceEl=document.getElementById('rf-route-trace');
        var traceVisible=traceEl&&traceEl.style.display!=='none';

        // Build positions text
        var posText='';
        if(txPos)posText+='TX: '+txPos.lat.toFixed(6)+', '+txPos.lon.toFixed(6)+'\n';
        if(rxPos)posText+='RX: '+rxPos.lat.toFixed(6)+', '+rxPos.lon.toFixed(6)+'\n';
        if(rfWaypoints.length>0){
            rfWaypoints.forEach(function(wp,i){
                posText+=(i===0?'Pilot':'WP'+i)+': '+wp.lat.toFixed(6)+', '+wp.lon.toFixed(6)+'\n';
            });
        }
        if(rfRepeaterPts.length>0){
            var labels=['Pilot','Repeater','Target'];
            rfRepeaterPts.forEach(function(p,i){
                posText+=labels[i]+': '+p.lat.toFixed(6)+', '+p.lon.toFixed(6)+'\n';
            });
        }

        // Config
        var config='Protocol: '+proto.name+' ('+proto.freq+' MHz)\n';
        config+='TX Power: '+getVal('rf-tx-power')+' dBm\n';
        config+='TX Height: '+getVal('rf-tx-height')+' m AGL\n';
        config+='RX Height: '+getVal('rf-rx-height')+' m AGL\n';
        config+='TX Gain: '+getVal('rf-tx-gain')+' dBi · RX Gain: '+getVal('rf-rx-gain')+' dBi\n';
        config+='Fade Margin: '+getVal('rf-fade-margin')+' dB\n';

        // Route trace summary
        var traceHtml='';
        if(traceVisible&&rfTraceSegments._allPts){
            var pts=rfTraceSegments._allPts;
            var minM=999,maxM=-999,avgM=0;
            var lostPts=0;
            pts.forEach(function(p){
                if(p.margin<minM)minM=p.margin;
                if(p.margin>maxM)maxM=p.margin;
                avgM+=p.margin;
                if(p.margin<0)lostPts++;
            });
            avgM/=pts.length;
            var lostPct=Math.round(lostPts/pts.length*100);
            traceHtml='<div class="section"><h3>Route Trace Analysis</h3>';
            traceHtml+='<table><tr><td>Points sampled</td><td>'+pts.length+'</td></tr>';
            traceHtml+='<tr><td>Min margin</td><td style="color:'+(minM<0?'#ef4444':'#eab308')+'">'+minM.toFixed(1)+' dB</td></tr>';
            traceHtml+='<tr><td>Max margin</td><td style="color:#4ade80">'+maxM.toFixed(1)+' dB</td></tr>';
            traceHtml+='<tr><td>Avg margin</td><td>'+avgM.toFixed(1)+' dB</td></tr>';
            traceHtml+='<tr><td>Signal lost</td><td style="color:'+(lostPct>0?'#ef4444':'#4ade80')+'">'+lostPct+'% of route</td></tr>';
            traceHtml+='</table>';
            // Add issue spots to report
            var issues=rfTraceSegments._issues;
            if(issues&&issues.length>0){
                traceHtml+='<h3 style="margin-top:12px;">Issue Spots ('+issues.length+')</h3>';
                traceHtml+='<table><tr><th>Severity</th><th>Margin</th><th>Distance</th><th>Span</th><th>Coordinates</th></tr>';
                issues.forEach(function(issue){
                    var sev=issue.worst.margin<0?'LOST':issue.worst.margin<3?'CRITICAL':'WEAK';
                    var sc=issue.worst.margin<0?'#ef4444':issue.worst.margin<3?'#f87171':'#eab308';
                    var span=(issue.endPt.d-issue.startPt.d).toFixed(2);
                    traceHtml+='<tr><td style="color:'+sc+';font-weight:700;">'+sev+'</td>';
                    traceHtml+='<td style="color:'+sc+';">'+issue.worst.margin.toFixed(1)+' dB</td>';
                    traceHtml+='<td>'+issue.worst.d.toFixed(2)+' km</td>';
                    traceHtml+='<td>'+span+' km</td>';
                    traceHtml+='<td style="font-family:monospace;font-size:9px;">'+issue.worst.lat.toFixed(6)+', '+issue.worst.lon.toFixed(6)+'</td></tr>';
                });
                traceHtml+='</table>';
            }
            traceHtml+='</div>';
        }

        // Build report HTML
        var html='<!DOCTYPE html><html><head><meta charset="utf-8"><title>RF Propagation Report — Forge</title>';
        html+='<style>';
        html+='*{margin:0;padding:0;box-sizing:border-box;}';
        html+='body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;background:#0b0f14;color:#c9d1d9;padding:32px;font-size:11px;line-height:1.5;}';
        html+='h1{font-size:18px;color:#22d3ee;margin-bottom:4px;}';
        html+='h2{font-size:11px;color:#8b949e;margin-bottom:20px;text-transform:uppercase;letter-spacing:0.1em;font-weight:400;}';
        html+='h3{font-size:12px;color:#c9d1d9;margin-bottom:8px;text-transform:uppercase;letter-spacing:0.06em;border-bottom:1px solid #21262d;padding-bottom:4px;}';
        html+='.section{margin-bottom:20px;}';
        html+='pre{font-family:"JetBrains Mono",monospace;font-size:10px;color:#8b949e;line-height:1.6;background:#111820;padding:10px;border-radius:6px;border:1px solid #21262d;white-space:pre-wrap;}';
        html+='table{width:100%;border-collapse:collapse;font-size:10px;margin-bottom:8px;}';
        html+='td,th{padding:4px 8px;border-bottom:1px solid #21262d;text-align:left;color:#8b949e;}';
        html+='th{color:#4a5f75;text-transform:uppercase;font-size:9px;letter-spacing:0.06em;}';
        html+='svg{max-width:100%;height:auto;margin:8px 0;border:1px solid #21262d;border-radius:6px;background:#0b0f14;}';
        html+='.results{background:#111820;padding:12px;border-radius:6px;border:1px solid #21262d;margin-bottom:12px;}';
        html+='.footer{margin-top:24px;padding-top:12px;border-top:1px solid #21262d;font-size:9px;color:#4a5f75;text-align:center;}';
        html+='@media print{body{background:#fff;color:#1a1a1a;}h1{color:#0d6d8a;}h3{color:#333;border-color:#ddd;}pre{background:#f5f5f5;border-color:#ddd;color:#333;}td,th{color:#333;border-color:#ddd;}.results{background:#f5f5f5;border-color:#ddd;}svg{border-color:#ddd;background:#fff;}.footer{color:#999;border-color:#ddd;}}';
        html+='</style></head><body>';

        html+='<h1>RF Propagation Report</h1>';
        html+='<h2>Generated by Forge · '+now+'</h2>';

        html+='<div class="section"><h3>Configuration</h3><pre>'+config+'</pre></div>';
        html+='<div class="section"><h3>Positions</h3><pre>'+posText+'</pre></div>';

        if(svgHtml){
            html+='<div class="section"><h3>Elevation Profile</h3>'+svgHtml+'</div>';
        }
        if(resultsHtml){
            html+='<div class="section"><h3>Link Analysis</h3><div class="results">'+resultsHtml+'</div></div>';
        }
        if(budgetHtml){
            html+='<div class="section"><h3>Link Budget</h3><div class="results">'+budgetHtml+'</div></div>';
        }
        if(pathHtml){
            html+='<div class="section"><h3>Path Details</h3><div class="results">'+pathHtml+'</div></div>';
        }
        if(advancedHtml){
            html+='<div class="section"><h3>Advanced Analysis</h3><div class="results">'+advancedHtml+'</div></div>';
        }
        if(traceHtml){
            html+=traceHtml;
        }

        html+='<div class="footer">Forge RF Propagation Tool · uas-forge.com/tools/ · AI Wingman by Midwest Nice Advisory LLC<br>';
        html+='Elevation data: SRTM 30m / USGS 3DEP. Does not model vegetation, structures, atmospheric absorption, rain fade, multipath, or antenna patterns.</div>';
        html+='</'+'body></'+'html>';

        // Open in new tab — user can print to PDF
        var w=window.open('','_blank');
        if(w){w.document.write(html);w.document.close();}
    };

    // Hash routing handled by global router below
})();
// MESH / SWARM RF PLANNER
// ═══════════════════════════════════════════════════════════════════
(function(){
    // ── Radio Hardware Database ──
    var MESH_RADIOS={
        DL_2450:{name:'Doodle Labs RM-2450',freq:[2400],txPow:30,rxSens:-96,maxRate:20,mesh:true,meshType:'batman-adv',waveform:'802.11',weight:45,ground:true,air:true},
        DL_1675:{name:'Doodle Labs RM-1675',freq:[1625],txPow:30,rxSens:-96,maxRate:20,mesh:true,meshType:'batman-adv',waveform:'802.11',weight:45,ground:true,air:true},
        DL_2458:{name:'Doodle Labs DM-2458',freq:[2400,5800],txPow:30,rxSens:-93,maxRate:40,mesh:true,meshType:'batman-adv (dual)',waveform:'802.11',weight:55,ground:true,air:true},
        SC4200:{name:'Silvus SC4200',freq:[1350,1850],txPow:33,rxSens:-100,maxRate:100,mesh:true,meshType:'StreamCaster',waveform:'streamcaster',weight:310,ground:true,air:true},
        SC4400:{name:'Silvus SC4400',freq:[1350,6200],txPow:33,rxSens:-100,maxRate:100,mesh:true,meshType:'StreamCaster',waveform:'streamcaster',weight:340,ground:true,air:false},
        MPU5:{name:'Persistent MPU5',freq:[1350,6200],txPow:33,rxSens:-98,maxRate:100,mesh:true,meshType:'Wave Relay',waveform:'waverelay',weight:510,ground:true,air:false},
        TW950:{name:'TW-950 Shadow',freq:[225,2500],txPow:30,rxSens:-100,maxRate:16,mesh:true,meshType:'TSM Barrage Relay',waveform:'tsm',weight:280,ground:true,air:false},
        TW135:{name:'TW-135 Shadow HPR',freq:[225,2500],txPow:43,rxSens:-100,maxRate:16,mesh:true,meshType:'TSM Barrage Relay',waveform:'tsm',weight:1800,ground:true,air:false},
        TW750:{name:'TW-750 Shadow',freq:[225,2500],txPow:30,rxSens:-100,maxRate:16,mesh:true,meshType:'TSM Barrage Relay',waveform:'tsm',weight:350,ground:true,air:false},
        TW650:{name:'TW-650 Shadow Module',freq:[225,2500],txPow:27,rxSens:-100,maxRate:16,mesh:true,meshType:'TSM Barrage Relay',waveform:'tsm',weight:85,ground:false,air:true},
        TW870:{name:'TW-870 Ghost',freq:[225,2500],txPow:24,rxSens:-100,maxRate:8,mesh:true,meshType:'TSM Barrage Relay',waveform:'tsm',weight:130,ground:true,air:true},
        TW880:{name:'TW-880 Ghost Module',freq:[225,2500],txPow:24,rxSens:-100,maxRate:8,mesh:true,meshType:'TSM Barrage Relay',waveform:'tsm',weight:42,ground:false,air:true},
        RFD900X:{name:'RFD900x',freq:[915],txPow:30,rxSens:-121,maxRate:0.25,mesh:false,meshType:'Point-to-multipoint',waveform:'fhss',weight:29,ground:true,air:true},
        RAJANT_ES1:{name:'Rajant ES1',freq:[2400],txPow:27,rxSens:-95,maxRate:40,mesh:true,meshType:'Kinetic Mesh',waveform:'instamesh',weight:120,ground:true,air:true},
        RAJANT_PG:{name:'Rajant Peregrine',freq:[2400,5800],txPow:27,rxSens:-93,maxRate:80,mesh:true,meshType:'Kinetic Mesh',waveform:'instamesh',weight:140,ground:true,air:true},
        ELRS_900:{name:'ELRS 900M',freq:[915],txPow:27,rxSens:-123,maxRate:0.01,mesh:false,meshType:'None (C2 only)',waveform:'lora',weight:5,ground:true,air:true},
        ELRS_24:{name:'ELRS 2.4G',freq:[2400],txPow:13,rxSens:-118,maxRate:0.01,mesh:false,meshType:'None (C2 only)',waveform:'lora',weight:3,ground:true,air:true},
        CUSTOM:{name:'Custom',freq:[2400],txPow:30,rxSens:-96,maxRate:20,mesh:true,meshType:'Custom',waveform:'custom',weight:0,ground:true,air:true},
    };

    // Waveform families — radios in the same family can interoperate
    var WAVEFORM_COMPAT={
        '802.11':['802.11'],
        'streamcaster':['streamcaster'],
        'waverelay':['waverelay'],
        'tsm':['tsm'],
        'instamesh':['instamesh'],
        'fhss':['fhss'],
        'lora':['lora'],
        'custom':['custom','802.11','streamcaster','waverelay','tsm','instamesh','fhss','lora'],
    };

    // Check if two radios can interoperate
    function radiosCompatible(radioA,radioB){
        if(!radioA||!radioB)return false;
        var wfA=radioA.waveform,wfB=radioB.waveform;
        var compatA=WAVEFORM_COMPAT[wfA]||[wfA];
        if(compatA.indexOf(wfB)===-1)return false;
        // Check frequency overlap
        return freqOverlap(radioA.freq,radioB.freq);
    }

    function freqOverlap(freqsA,freqsB){
        // freq arrays: [single] or [low,high] for range, or [band1,band2] for dual
        // Simplified: check if any freq value is within ±200MHz of another
        for(var i=0;i<freqsA.length;i++){
            for(var j=0;j<freqsB.length;j++){
                if(Math.abs(freqsA[i]-freqsB[j])<500)return true;
                // Range check: if freqsA is [low,high], check if freqsB[j] is within
                if(freqsA.length===2&&freqsB[j]>=freqsA[0]&&freqsB[j]<=freqsA[1])return true;
                if(freqsB.length===2&&freqsA[i]>=freqsB[0]&&freqsA[i]<=freqsB[1])return true;
            }
        }
        return false;
    }

    // Best common frequency between two radios (for FSPL calc)
    function bestCommonFreq(radioA,radioB){
        // Use the lowest common frequency for best propagation
        var best=2400;
        var found=false;
        for(var i=0;i<radioA.freq.length;i++){
            for(var j=0;j<radioB.freq.length;j++){
                var fa=radioA.freq[i],fb=radioB.freq[j];
                if(Math.abs(fa-fb)<500){var f=Math.min(fa,fb);if(!found||f<best){best=f;found=true;}}
            }
        }
        return best;
    }

    // Throughput estimate from margin
    function estimateThroughput(margin,maxRate){
        if(margin>20)return maxRate;
        if(margin>15)return maxRate*0.8;
        if(margin>10)return maxRate*0.6;
        if(margin>6)return maxRate*0.4;
        if(margin>3)return maxRate*0.2;
        if(margin>0)return maxRate*0.05;
        return 0;
    }

    function meshGetDefaultRadio(){return MESH_RADIOS[document.getElementById('mesh-protocol').value]||MESH_RADIOS.DL_2450;}

    var meshMap=null,meshTile=null,meshInited=false;
    var meshNodes=[];
    var meshLinks=[];
    var meshKillMode=false;
    var meshIdCounter=0;

    function initMeshMap(){
        if(meshInited)return;
        var L=window.L;
        if(!L)return;
        meshInited=true;
        meshMap=L.map('mesh-map',{center:[39.0,-98.0],zoom:4,zoomControl:true});
        window._meshMapRef=meshMap;
        meshTile=L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',{attribution:'&copy; OpenStreetMap &copy; CARTO',maxZoom:19});
        watchMapTiles(meshTile, 'mesh');
        meshTile.addTo(meshMap);
        meshMap.on('click',function(e){meshMapClick(e.latlng.lat,e.latlng.lng);});
        // Retry invalidateSize until map has real dimensions
        var retries=0;
        function tryResize(){
            meshMap.invalidateSize();
            var el=document.getElementById('mesh-map');
            if(el&&el.offsetWidth>0&&el.offsetHeight>0&&retries<20){
                meshMap.invalidateSize();
            } else if(retries<20){
                retries++;
                setTimeout(tryResize,150);
            }
        }
        setTimeout(tryResize,50);
    }

    window.meshSetLayer=function(key){
        if(!meshMap||!meshTile)return;
        var urls={dark:'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',light:'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png',sat:'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',topo:'https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/{z}/{y}/{x}'};
        meshTile.setUrl(urls[key]||urls.dark);
    };

    function meshMapClick(lat,lon){
        if(meshKillMode){return;}
        var L=window.L;
        meshIdCounter++;
        var isGCS=meshIdCounter===1;
        var id=isGCS?'GCS':'N'+(meshIdCounter-1);
        var colors=['#22d3ee','#4ade80','#f59e0b','#a78bfa','#f87171','#fb923c','#38bdf8','#e879f9','#fbbf24','#6ee7b7'];
        var c=isGCS?'#22d3ee':colors[(meshIdCounter-2)%colors.length];
        var label=isGCS?'GCS':String(meshIdCounter-1);
        var size=isGCS?22:18;
        var icon=L.divIcon({className:'',html:'<div style="width:'+size+'px;height:'+size+'px;border-radius:'+(isGCS?'4px':'50%')+';background:'+c+';border:3px solid #0b0f14;box-shadow:0 0 '+(isGCS?'12':'8')+'px '+c+'88;display:flex;align-items:center;justify-content:center;font-size:'+(isGCS?'8':'7')+'px;font-weight:700;color:#0b0f14;">'+label+'</div>',iconSize:[size,size],iconAnchor:[size/2,size/2]});
        var marker=L.marker([lat,lon],{draggable:true,icon:icon}).addTo(meshMap);
        marker.bindTooltip(id+(isGCS?' (Ground Control)':''),{permanent:false,direction:'top',offset:[0,-12]});
        var defaultRadioKey=document.getElementById('mesh-protocol').value||'DL_2450';
        var node={id:id,lat:lat,lon:lon,marker:marker,active:true,label:id,color:c,num:meshIdCounter,isGCS:isGCS,
            height:isGCS?2:parseFloat(document.getElementById('mesh-node-height').value)||50,
            power:parseFloat(document.getElementById('mesh-tx-power').value)||30,
            radioKey:defaultRadioKey,radio:MESH_RADIOS[defaultRadioKey]||MESH_RADIOS.DL_2450};
        marker.on('dragend',function(){var p=marker.getLatLng();node.lat=p.lat;node.lon=p.lng;meshUpdateNodeList();});
        marker.on('click',function(){
            if(meshKillMode){node.active=!node.active;marker.setOpacity(node.active?1:0.3);meshUpdateNodeList();}
        });
        meshNodes.push(node);
        meshUpdateNodeList();
    }

    function meshUpdateNodeList(){
        var el=document.getElementById('mesh-node-list');
        if(!el)return;
        document.getElementById('mesh-node-count').textContent=meshNodes.length+' nodes';
        if(meshNodes.length===0){el.innerHTML='';return;}
        // Get compatible radios based on top selector
        var topSel=document.getElementById('mesh-protocol').value||'DL_2450';
        var topRadio=MESH_RADIOS[topSel];
        var topWf=topRadio?topRadio.waveform:'802.11';
        var compatKeysGround=getCompatibleRadioKeys(topWf,'ground');
        var compatKeysAir=getCompatibleRadioKeys(topWf,'air');
        var html='<div style="font-size:9px;">';
        meshNodes.forEach(function(n,i){
            var offline=!n.active;
            var nodeKeys=n.isGCS?compatKeysGround:compatKeysAir;
            html+='<div style="display:flex;align-items:center;gap:4px;padding:3px 0;border-bottom:1px solid var(--border-color);">';
            html+='<span style="width:8px;height:8px;border-radius:'+(n.isGCS?'2px':'50%')+';background:'+(offline?'#666':n.color)+';flex-shrink:0;"></span>';
            html+='<span style="color:'+(offline?'#f87171':'var(--text-main)')+';font-weight:600;min-width:22px;">'+n.id+'</span>';
            html+='<select onchange="meshNodeRadio('+i+',this.value)" style="flex:1;min-width:0;padding:1px 2px;font-size:8px;background:var(--bg-dark);border:1px solid var(--border-color);border-radius:2px;color:var(--text-main);font-family:var(--font-family);">';
            nodeKeys.forEach(function(k){html+='<option value="'+k+'"'+(n.radioKey===k?' selected':'')+'>'+MESH_RADIOS[k].name+'</option>';});
            html+='</select>';
            html+='<input type="number" value="'+n.height+'" min="1" max="500" step="5" style="width:36px;padding:1px 2px;font-size:8px;background:var(--bg-dark);border:1px solid var(--border-color);border-radius:2px;color:var(--text-main);" onchange="meshNodeHeight('+i+',this.value)" title="Height AGL (m)">';
            html+='<span style="font-size:7px;color:var(--text-faint);">m</span>';
            if(offline)html+='<span style="color:#f87171;font-size:7px;font-weight:700;">OFF</span>';
            html+='</div>';
        });
        html+='</div>';
        el.innerHTML=html;
    }

    window.meshNodeRadio=function(i,key){
        if(meshNodes[i]){meshNodes[i].radioKey=key;meshNodes[i].radio=MESH_RADIOS[key]||MESH_RADIOS.DL_2450;meshNodes[i].power=MESH_RADIOS[key].txPow;}
    };

    // Get compatible radio keys for a given waveform, filtered by role
    function getCompatibleRadioKeys(waveform,role){
        var keys=[];
        var radioKeys=Object.keys(MESH_RADIOS);
        for(var i=0;i<radioKeys.length;i++){
            var r=MESH_RADIOS[radioKeys[i]];
            if(r.waveform!==waveform&&r.waveform!=='custom'&&waveform!=='custom')continue;
            if(role==='ground'&&!r.ground)continue;
            if(role==='air'&&!r.air)continue;
            keys.push(radioKeys[i]);
        }
        return keys;
    }

    // When top selector changes, update all existing nodes to match + rebuild list
    window.meshFilterNodeRadios=function(){
        var sel=document.getElementById('mesh-protocol').value;
        var radio=MESH_RADIOS[sel];
        if(!radio)return;
        // Update TX power display to match selected radio
        document.getElementById('mesh-tx-power').value=radio.txPow;
        // Update all existing nodes to compatible radio if they're incompatible
        var wf=radio.waveform;
        meshNodes.forEach(function(n){
            if(n.radio&&n.radio.waveform!==wf&&wf!=='custom'&&n.radio.waveform!=='custom'){
                n.radioKey=sel;n.radio=radio;n.power=radio.txPow;
            }
        });
        meshUpdateNodeList();
    };
    window.meshNodeHeight=function(i,v){if(meshNodes[i])meshNodes[i].height=parseFloat(v)||50;};
    window.meshNodePower=function(i,v){if(meshNodes[i])meshNodes[i].power=parseFloat(v)||30;};

    window.meshUndo=function(){
        if(meshNodes.length===0)return;
        var last=meshNodes.pop();
        if(last.marker&&meshMap)meshMap.removeLayer(last.marker);
        meshUpdateNodeList();
    };

    window.meshClear=function(){
        meshNodes.forEach(function(n){if(n.marker&&meshMap)meshMap.removeLayer(n.marker);});
        meshNodes=[];meshIdCounter=0;
        meshClearLinks();
        meshClearHeatmap();
        meshUpdateNodeList();
        document.getElementById('mesh-health').style.display='none';
        document.getElementById('mesh-matrix').style.display='none';
        document.getElementById('mesh-issues').style.display='none';
    };

    window.meshToggleKill=function(){
        meshKillMode=document.getElementById('mesh-kill-mode').checked;
        meshNodes.forEach(function(n){n.marker.dragging[meshKillMode?'disable':'enable']();});
    };

    function meshClearLinks(){
        meshLinks.forEach(function(l){if(l.polyline&&meshMap)meshMap.removeLayer(l.polyline);});
        meshLinks=[];
        if(meshProfileSightLine&&meshMap){meshMap.removeLayer(meshProfileSightLine);meshProfileSightLine=null;}
        if(meshTraceMarker&&meshMap){meshMap.removeLayer(meshTraceMarker);meshTraceMarker=null;}
        meshTraceData=null;
        document.getElementById('mesh-link-count').textContent='0 links';
        document.getElementById('mesh-link-profile').style.display='none';
    }

    // ── Haversine (local copy) ──
    function hav(lat1,lon1,lat2,lon2){
        var R=6371000,dLat=(lat2-lat1)*Math.PI/180,dLon=(lon2-lon1)*Math.PI/180;
        var a=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(lat1*Math.PI/180)*Math.cos(lat2*Math.PI/180)*Math.sin(dLon/2)*Math.sin(dLon/2);
        return R*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
    }

    // ── Compute mesh ──
    window.meshCompute=async function(){
        var active=meshNodes.filter(function(n){return n.active;});
        if(active.length<2){document.getElementById('mesh-health').innerHTML='<div style="color:#f87171;padding:8px;font-size:11px;">Need at least 2 active nodes</div>';document.getElementById('mesh-health').style.display='block';return;}
        meshClearLinks();
        var defaultH=parseFloat(document.getElementById('mesh-node-height').value)||50;
        var minMargin=parseFloat(document.getElementById('mesh-min-margin').value)||6;
        var txG=2,rxG=2;
        var L=window.L;
        var links=[];
        var n=active.length;
        var totalPairs=n*(n-1)/2;
        document.getElementById('mesh-link-count').textContent='computing '+totalPairs+' pairs...';
        // Pass 1: FSPL with per-node radio + compatibility check
        for(var i=0;i<n;i++){
            for(var j=i+1;j<n;j++){
                var a=active[i],b=active[j];
                var rA=a.radio||meshGetDefaultRadio(),rB=b.radio||meshGetDefaultRadio();
                var compatible=radiosCompatible(rA,rB);
                var d=hav(a.lat,a.lon,b.lat,b.lon)/1000;
                var margin=-999,fspl=0,rxPow=-999,throughput=0,linkFreq=2400;
                if(compatible){
                    linkFreq=bestCommonFreq(rA,rB);
                    var txP=Math.min(rA.txPow,rB.txPow);
                    var sens=Math.max(rA.rxSens,rB.rxSens); // worst sensitivity
                    fspl=20*Math.log10(d>0.001?d:0.001)+20*Math.log10(linkFreq)+32.44;
                    rxPow=txP+txG+rxG-fspl;
                    margin=rxPow-sens;
                    throughput=estimateThroughput(margin,Math.min(rA.maxRate,rB.maxRate));
                }
                links.push({from:a.id,to:b.id,fromNode:a,toNode:b,dist:d,margin:margin,fspl:fspl,rxPow:rxPow,
                    diffLoss:0,hasLOS:true,terrainDone:false,quality:'',connected:false,polyline:null,
                    compatible:compatible,throughput:throughput,linkFreq:linkFreq,
                    radioA:rA.name,radioB:rB.name,waveformA:rA.waveform,waveformB:rB.waveform});
            }
        }
        // Pass 2: terrain for compatible borderline links
        var terrainLinks=links.filter(function(l){return l.compatible&&l.margin>-10&&l.margin<30;});
        var terrainOK=0,terrainFail=0;
        if(terrainLinks.length>0&&terrainLinks.length<=60){
            document.getElementById('mesh-link-count').textContent='terrain for '+terrainLinks.length+'/'+totalPairs+' links...';
            for(var k=0;k<terrainLinks.length;k++){
                var tl=terrainLinks[k];
                try{
                    var pts=window._rfInterpolatePoints(tl.fromNode.lat,tl.fromNode.lon,tl.toNode.lat,tl.toNode.lon,15);
                    var elev=null;
                    for(var attempt=0;attempt<2&&!elev;attempt++){
                        try{var result=await window._rfGetElevations(pts);if(result&&result.elevations)elev=result;}catch(e){}
                    }
                    if(!elev)elev={elevations:pts.map(function(){return 0;}),source:'flat'};
                    var dists=pts.map(function(p){return hav(tl.fromNode.lat,tl.fromNode.lon,p.lat,p.lon);});
                    var hFrom=tl.fromNode.height||defaultH,hTo=tl.toNode.height||defaultH;
                    var pwr=Math.min(tl.fromNode.radio.txPow,tl.toNode.radio.txPow);
                    var sens=Math.max(tl.fromNode.radio.rxSens,tl.toNode.radio.rxSens);
                    var analysis=window._rfAnalyzePath(elev.elevations,dists,hFrom,hTo,tl.linkFreq);
                    var budget=window._rfLinkBudget(analysis,pwr,sens,txG,rxG,tl.linkFreq,0);
                    tl.margin=budget.margin;tl.diffLoss=analysis.diffLoss;tl.hasLOS=analysis.hasLOS;
                    tl.terrainDone=true;tl.analysis=analysis;tl.elevSource=elev.source;
                    tl.throughput=estimateThroughput(budget.margin,Math.min(tl.fromNode.radio.maxRate,tl.toNode.radio.maxRate));
                    terrainOK++;
                }catch(e){terrainFail++;}
            }
        }
        // Also terrain for strong compatible connected links
        var nonTerrainConnected=links.filter(function(l){return l.compatible&&!l.terrainDone&&l.margin>=minMargin;});
        for(var k=0;k<nonTerrainConnected.length&&k<30;k++){
            var tl=nonTerrainConnected[k];
            try{
                var pts=window._rfInterpolatePoints(tl.fromNode.lat,tl.fromNode.lon,tl.toNode.lat,tl.toNode.lon,15);
                var elev=null;
                try{elev=await window._rfGetElevations(pts);}catch(e){}
                if(!elev)elev={elevations:pts.map(function(){return 0;}),source:'flat'};
                var dists=pts.map(function(p){return hav(tl.fromNode.lat,tl.fromNode.lon,p.lat,p.lon);});
                var hFrom=tl.fromNode.height||defaultH,hTo=tl.toNode.height||defaultH;
                var pwr=Math.min(tl.fromNode.radio.txPow,tl.toNode.radio.txPow);
                var sens=Math.max(tl.fromNode.radio.rxSens,tl.toNode.radio.rxSens);
                var analysis=window._rfAnalyzePath(elev.elevations,dists,hFrom,hTo,tl.linkFreq);
                var budget=window._rfLinkBudget(analysis,pwr,sens,txG,rxG,tl.linkFreq,0);
                tl.margin=budget.margin;tl.diffLoss=analysis.diffLoss;tl.hasLOS=analysis.hasLOS;
                tl.terrainDone=true;tl.analysis=analysis;tl.elevSource=elev.source;
                tl.throughput=estimateThroughput(budget.margin,Math.min(tl.fromNode.radio.maxRate,tl.toNode.radio.maxRate));
                terrainOK++;
            }catch(e){terrainFail++;}
        }
        // Classify and draw
        links.forEach(function(l){
            if(!l.compatible){
                l.quality='incompatible';l.connected=false;
                l.polyline=L.polyline([[l.fromNode.lat,l.fromNode.lon],[l.toNode.lat,l.toNode.lon]],{color:'#9333ea',weight:1.5,opacity:0.4,dashArray:'2,6'}).addTo(meshMap);
                l.polyline.bindTooltip(l.from+' \u2194 '+l.to+': INCOMPATIBLE ('+l.waveformA+' \u2260 '+l.waveformB+')',{sticky:true});
                (function(link){l.polyline.on('click',function(){meshShowLinkProfile(link);});})(l);
                return;
            }
            l.quality=l.margin>20?'excellent':l.margin>minMargin?'good':l.margin>0?'weak':'fail';
            l.connected=l.margin>=minMargin;
            var color=l.margin>20?'#4ade80':l.margin>minMargin?'#eab308':l.margin>0?'#f59e0b':'#ef4444';
            var tip=l.from+' \u2194 '+l.to+': '+l.margin.toFixed(1)+' dB';
            if(l.throughput>0)tip+=' \u00B7 ~'+(l.throughput>=1?l.throughput.toFixed(0):l.throughput.toFixed(2))+' Mbps';
            if(l.terrainDone)tip+=' (terrain'+(l.hasLOS?'':' \u2014 LOS blocked')+')';
            tip+=' \u2014 click for profile';
            if(l.connected){
                l.polyline=L.polyline([[l.fromNode.lat,l.fromNode.lon],[l.toNode.lat,l.toNode.lon]],{color:color,weight:3,opacity:0.7}).addTo(meshMap);
                l.polyline.bindTooltip(tip,{sticky:true});
            } else {
                l.polyline=L.polyline([[l.fromNode.lat,l.fromNode.lon],[l.toNode.lat,l.toNode.lon]],{color:'#333',weight:1,opacity:0.3,dashArray:'4,8'}).addTo(meshMap);
            }
            (function(link){l.polyline.on('click',function(){meshShowLinkProfile(link);});})(l);
        });
        meshLinks=links;
        var connCount=links.filter(function(l){return l.connected;}).length;
        var incompatCount=links.filter(function(l){return !l.compatible;}).length;
        var terrainCount=links.filter(function(l){return l.terrainDone;}).length;
        var statusParts=[connCount+'/'+links.length+' connected'];
        if(incompatCount>0)statusParts.push(incompatCount+' incompatible');
        if(terrainCount>0)statusParts.push(terrainCount+' terrain');
        document.getElementById('mesh-link-count').textContent=statusParts.join(' \u00B7 ');
        meshAnalyze(active,links,minMargin);
        if(meshHeatEnabled)meshBuildHeatmap();
    };

    function meshAnalyze(nodes,links,minMargin){
        var n=nodes.length;
        var idMap={};nodes.forEach(function(nd,i){idMap[nd.id]=i;});
        // Build adjacency with weights
        var adj=[];var adjWeights=[];for(var i=0;i<n;i++){adj.push([]);adjWeights.push([]);}
        links.forEach(function(l){
            if(!l.connected)return;
            var a=idMap[l.from],b=idMap[l.to];
            if(a!==undefined&&b!==undefined){
                adj[a].push(b);adj[b].push(a);
                var w=l.margin>0?1/l.margin:999;
                adjWeights[a].push(w);adjWeights[b].push(w);
            }
        });
        // BFS connectivity
        function bfsReachable(start,skip){
            var visited=new Set();var q=[start];visited.add(start);
            while(q.length>0){var c=q.shift();adj[c].forEach(function(nb){if(!visited.has(nb)&&nb!==skip){visited.add(nb);q.push(nb);}});}
            return visited;
        }
        var reachable=bfsReachable(0,-1);
        var connected=reachable.size===n;
        var components=[];
        var assigned=new Set();
        for(var i=0;i<n;i++){
            if(!assigned.has(i)){
                var comp=bfsReachable(i,-1);
                comp.forEach(function(c){assigned.add(c);});
                components.push(Array.from(comp).map(function(c){return nodes[c].id;}));
            }
        }
        // SPOF detection
        var spofNodes=[];
        for(var i=0;i<n;i++){
            if(n<=2)continue;
            var startNode=i===0?1:0;
            var reach=new Set();var q=[startNode];reach.add(startNode);reach.add(i);
            while(q.length>0){var c=q.shift();adj[c].forEach(function(nb){if(!reach.has(nb)){reach.add(nb);q.push(nb);}});}
            if(reach.size<n){spofNodes.push(nodes[i].id);}
        }
        // Dijkstra from GCS (node 0) for hop count + path
        var gcs=0;
        var dist=[];var prev=[];var hops=[];
        for(var i=0;i<n;i++){dist.push(Infinity);prev.push(-1);hops.push(Infinity);}
        dist[gcs]=0;hops[gcs]=0;
        var visited=new Set();
        for(var iter=0;iter<n;iter++){
            var u=-1,bestD=Infinity;
            for(var i=0;i<n;i++){if(!visited.has(i)&&dist[i]<bestD){bestD=dist[i];u=i;}}
            if(u===-1)break;
            visited.add(u);
            for(var k=0;k<adj[u].length;k++){
                var v=adj[u][k],w=adjWeights[u][k];
                if(dist[u]+w<dist[v]){dist[v]=dist[u]+w;prev[v]=u;hops[v]=hops[u]+1;}
            }
        }
        function getPath(target){
            var path=[];var c=target;
            while(c!==-1){path.unshift(nodes[c].id);c=prev[c];}
            return path;
        }
        // Weakest link, isolated, avg degree
        var connectedLinks=links.filter(function(l){return l.connected;});
        var incompatLinks=links.filter(function(l){return !l.compatible;});
        var weakest=connectedLinks.length>0?connectedLinks.reduce(function(a,b){return a.margin<b.margin?a:b;}):null;
        var isolated=[];
        for(var i=0;i<n;i++){if(adj[i].length===0)isolated.push(nodes[i].id);}
        var avgDeg=adj.reduce(function(s,a){return s+a.length;},0)/n;
        // Radio mix summary
        var radioMix={};nodes.forEach(function(nd){var rn=nd.radio?nd.radio.name:'Unknown';radioMix[rn]=(radioMix[rn]||0)+1;});
        var waveformMix={};nodes.forEach(function(nd){var wf=nd.radio?nd.radio.waveform:'?';waveformMix[wf]=(waveformMix[wf]||0)+1;});
        var multiWaveform=Object.keys(waveformMix).length>1;
        // Non-mesh radio warnings
        var nonMeshNodes=nodes.filter(function(nd){return nd.radio&&!nd.radio.mesh;});
        // Render health
        var hEl=document.getElementById('mesh-health');
        var html='<div style="padding:10px;background:var(--bg-panel);border:1px solid var(--border-color);border-radius:var(--radius-sm);">';
        html+='<div style="font-size:10px;font-weight:600;color:var(--text-main);text-transform:uppercase;letter-spacing:0.08em;margin-bottom:8px;">Network Health</div>';
        if(multiWaveform)html+='<div style="padding:6px 8px;margin-bottom:8px;background:rgba(147,51,234,0.1);border:1px solid rgba(147,51,234,0.3);border-radius:var(--radius-sm);font-size:10px;color:#a78bfa;">\u26A0 Mixed waveforms: '+Object.keys(waveformMix).join(', ')+' \u2014 cross-waveform nodes cannot communicate even on same frequency.</div>';
        nonMeshNodes.forEach(function(nd){
            html+='<div style="padding:4px 8px;margin-bottom:4px;background:rgba(234,179,8,0.1);border:1px solid rgba(234,179,8,0.3);border-radius:var(--radius-sm);font-size:9px;color:#eab308;">\u26A0 '+nd.id+': '+nd.radio.name+' is '+nd.radio.meshType+' \u2014 cannot relay for other nodes.</div>';
        });
        var statusColor=connected?'#4ade80':(isolated.length>0?'#f87171':'#eab308');
        html+='<div style="margin-bottom:8px;padding:8px 10px;border-radius:var(--radius-sm);background:'+statusColor+'10;border:1px solid '+statusColor+'40;color:'+statusColor+';font-size:12px;font-weight:700;">'+(connected?'\u2713 FULLY CONNECTED':'\u26A0 NETWORK PARTITIONED \u2014 '+components.length+' fragments')+'</div>';
        html+='<div style="display:grid;grid-template-columns:1fr 1fr;gap:4px;font-size:10px;">';
        html+='<span style="color:var(--text-muted);">Nodes</span><span style="color:var(--text-main);">'+n+' active</span>';
        html+='<span style="color:var(--text-muted);">Links</span><span style="color:var(--text-main);">'+connectedLinks.length+' / '+links.length+' pairs'+(incompatLinks.length>0?' ('+incompatLinks.length+' incompatible)':'')+'</span>';
        html+='<span style="color:var(--text-muted);">Avg degree</span><span style="color:var(--text-main);">'+avgDeg.toFixed(1)+' connections/node</span>';
        html+='<span style="color:var(--text-muted);">Radios</span><span style="color:var(--text-main);">'+Object.keys(radioMix).map(function(k){return k+' \u00D7'+radioMix[k];}).join(', ')+'</span>';
        if(weakest)html+='<span style="color:var(--text-muted);">Weakest link</span><span style="color:#eab308;">'+weakest.from+' \u2194 '+weakest.to+': '+weakest.margin.toFixed(1)+' dB</span>';
        if(isolated.length>0)html+='<span style="color:var(--text-muted);">Isolated</span><span style="color:#f87171;">'+isolated.join(', ')+'</span>';
        if(spofNodes.length>0)html+='<span style="color:var(--text-muted);">SPOF nodes</span><span style="color:#f59e0b;">'+spofNodes.join(', ')+' \u2014 removing breaks mesh</span>';
        html+='</div>';
        // Hop count from GCS
        if(connected||connectedLinks.length>0){
            html+='<div style="margin-top:8px;border-top:1px solid var(--border-color);padding-top:8px;">';
            html+='<div style="font-size:9px;font-weight:600;color:var(--text-main);text-transform:uppercase;letter-spacing:0.08em;margin-bottom:4px;">Hop Count from GCS ('+nodes[0].id+')</div>';
            html+='<div style="display:grid;grid-template-columns:1fr 1fr;gap:2px;font-size:9px;">';
            for(var i=1;i<n;i++){
                var hopC=hops[i]===Infinity?'\u221E':hops[i];
                var hopColor=hops[i]<=1?'#4ade80':hops[i]<=2?'#eab308':hops[i]<=3?'#f59e0b':'#f87171';
                if(hops[i]===Infinity)hopColor='#f87171';
                var path=hops[i]!==Infinity?getPath(i).join('\u2192'):'unreachable';
                var latencyMs=hops[i]!==Infinity?(hops[i]*5).toFixed(0):'--';
                html+='<span style="color:var(--text-muted);">'+nodes[i].id+'</span>';
                html+='<span style="color:'+hopColor+';">'+hopC+' hop'+(hops[i]>1?'s':'')+' \u00B7 ~'+latencyMs+'ms \u00B7 '+path+'</span>';
            }
            html+='</div></div>';
        }
        html+='</div>';
        hEl.innerHTML=html;hEl.style.display='block';
        // Mark SPOF nodes on map
        spofNodes.forEach(function(sid){
            var nd=meshNodes.find(function(nn){return nn.id===sid;});
            if(nd&&nd.marker){nd.marker.setZIndexOffset(1000);}
        });
        // Connectivity matrix
        var mEl=document.getElementById('mesh-matrix');
        var mHtml='<div style="padding:10px;background:var(--bg-panel);border:1px solid var(--border-color);border-radius:var(--radius-sm);">';
        mHtml+='<div style="font-size:10px;font-weight:600;color:var(--text-main);text-transform:uppercase;letter-spacing:0.08em;margin-bottom:8px;">Connectivity Matrix (dB margin)</div>';
        mHtml+='<table style="width:100%;font-size:9px;border-collapse:collapse;font-family:var(--font-mono);">';
        mHtml+='<tr><th style="padding:3px;"></th>';
        nodes.forEach(function(nd){mHtml+='<th style="padding:3px;color:'+nd.color+';text-align:center;">'+nd.id+'</th>';});
        mHtml+='</tr>';
        nodes.forEach(function(row,ri){
            mHtml+='<tr><td style="padding:3px;color:'+row.color+';font-weight:600;">'+row.id+'</td>';
            nodes.forEach(function(col,ci){
                if(ri===ci){mHtml+='<td style="padding:3px;text-align:center;color:var(--text-faint);">—</td>';return;}
                var link=links.find(function(l){return(l.from===row.id&&l.to===col.id)||(l.from===col.id&&l.to===row.id);});
                var m=link?link.margin:null;
                var mc=m===null?'var(--text-faint)':m>20?'#4ade80':m>minMargin?'#eab308':m>0?'#f59e0b':'#ef4444';
                mHtml+='<td style="padding:3px;text-align:center;color:'+mc+';font-weight:'+(link&&link.connected?'700':'400')+';">'+(m!==null?m.toFixed(0):'—')+'</td>';
            });
            mHtml+='</tr>';
        });
        mHtml+='</table></div>';
        mEl.innerHTML=mHtml;mEl.style.display='block';
        // Issues
        var iEl=document.getElementById('mesh-issues');
        var issues=[];
        if(!connected)issues.push({sev:'CRITICAL',color:'#ef4444',msg:'Network partitioned into '+components.length+' fragments'});
        isolated.forEach(function(nid){issues.push({sev:'CRITICAL',color:'#ef4444',msg:'Node '+nid+' is isolated — no connections'});});
        spofNodes.forEach(function(nid){issues.push({sev:'WARNING',color:'#f59e0b',msg:'Node '+nid+' is a single point of failure'});});
        if(weakest&&weakest.margin<10)issues.push({sev:'WEAK',color:'#eab308',msg:'Weakest link '+weakest.from+'↔'+weakest.to+' only '+weakest.margin.toFixed(1)+' dB'});
        links.filter(function(l){return!l.connected&&l.margin>0;}).forEach(function(l){
            issues.push({sev:'INFO',color:'var(--text-faint)',msg:l.from+'↔'+l.to+' has '+l.margin.toFixed(1)+' dB — below '+minMargin+' dB threshold'});
        });
        if(issues.length===0){
            iEl.innerHTML='<div style="padding:6px 10px;font-size:10px;color:#4ade80;background:rgba(74,222,128,0.06);border:1px solid rgba(74,222,128,0.2);border-radius:var(--radius-sm);">✓ No issues — mesh is healthy</div>';
        } else {
            var iHtml='<div style="padding:8px 10px;background:var(--bg-panel);border:1px solid var(--border-color);border-radius:var(--radius-sm);">';
            iHtml+='<div style="font-size:10px;font-weight:600;color:var(--text-main);text-transform:uppercase;letter-spacing:0.08em;margin-bottom:6px;">⚠ '+issues.length+' Issue'+(issues.length>1?'s':'')+'</div>';
            issues.forEach(function(iss){
                iHtml+='<div style="padding:4px 8px;margin-bottom:3px;border-left:3px solid '+iss.color+';font-size:10px;color:var(--text-muted);"><span style="color:'+iss.color+';font-weight:700;">'+iss.sev+'</span> '+iss.msg+'</div>';
            });
            iHtml+='</div>';
            iEl.innerHTML=iHtml;
        }
        iEl.style.display='block';
    }

    // Export
    // ── RF Coverage Heatmap ──
    var meshHeatLayer=null;
    var meshHeatEnabled=false;

    window.meshToggleHeatmap=function(){
        meshHeatEnabled=document.getElementById('mesh-heatmap-toggle').checked;
        if(!meshHeatEnabled){meshClearHeatmap();return;}
        if(meshNodes.filter(function(n){return n.active;}).length>=1)meshBuildHeatmap();
    };

    function meshClearHeatmap(){
        if(meshHeatLayer&&meshMap){meshMap.removeLayer(meshHeatLayer);meshHeatLayer=null;}
    }

    function meshBuildHeatmap(){
        meshClearHeatmap();
        if(!meshMap)return;
        var L=window.L;
        var active=meshNodes.filter(function(n){return n.active;});
        if(active.length<1)return;
        var proto=meshGetDefaultRadio();
        var txP=parseFloat(document.getElementById('mesh-tx-power').value)||30;
        var nodeH=parseFloat(document.getElementById('mesh-node-height').value)||50;
        var txG=2,rxG=2;
        var bounds=meshMap.getBounds();
        var sw=bounds.getSouthWest(),ne=bounds.getNorthEast();
        // Expand bounds slightly beyond visible area
        var latRange=ne.lat-sw.lat,lonRange=ne.lng-sw.lng;
        var GRID=40; // 40x40 grid = 1600 sample points
        var latStep=latRange/GRID,lonStep=lonRange/GRID;
        // Build rectangles
        var rects=[];
        for(var r=0;r<GRID;r++){
            for(var c=0;c<GRID;c++){
                var lat=sw.lat+r*latStep+latStep/2;
                var lon=sw.lng+c*lonStep+lonStep/2;
                // Find best margin from any active node
                var bestMargin=-999;
                for(var i=0;i<active.length;i++){
                    var d=hav(active[i].lat,active[i].lon,lat,lon)/1000;
                    if(d<0.001)d=0.001;
                    var fspl=20*Math.log10(d)+20*Math.log10(proto.freq[0])+32.44;
                    var margin=txP+txG+rxG-fspl-proto.rxSens;
                    if(margin>bestMargin)bestMargin=margin;
                }
                // Color by margin
                var color,opacity;
                if(bestMargin>20){color='#4ade80';opacity=0.15;}
                else if(bestMargin>10){color='#22d3ee';opacity=0.18;}
                else if(bestMargin>6){color='#eab308';opacity=0.22;}
                else if(bestMargin>0){color='#f59e0b';opacity=0.28;}
                else{color='#ef4444';opacity=0.3;}
                var cellBounds=[[sw.lat+r*latStep,sw.lng+c*lonStep],[sw.lat+(r+1)*latStep,sw.lng+(c+1)*lonStep]];
                rects.push(L.rectangle(cellBounds,{color:'none',fillColor:color,fillOpacity:opacity,weight:0}));
            }
        }
        meshHeatLayer=L.layerGroup(rects).addTo(meshMap);
        // Move heatmap below markers
        if(meshHeatLayer._map)meshHeatLayer.eachLayer(function(l){l.bringToBack();});
    }

    // ── Link profile viewer ──
    async function meshShowLinkProfile(link){
        var panel=document.getElementById('mesh-link-profile');
        var title=document.getElementById('mesh-profile-title');
        var detail=document.getElementById('mesh-profile-detail');
        var svg=document.getElementById('mesh-profile-svg');
        if(!panel||!svg)return;
        title.textContent=link.from+' \u2194 '+link.to+' \u2014 '+link.dist.toFixed(2)+' km';
        panel.style.display='block';
        if(!link.analysis){
            svg.innerHTML='<text x="350" y="90" fill="#eab308" font-size="11" text-anchor="middle">Computing terrain...</text>';
            try{
                var proto=meshGetDefaultRadio();
                var dH=parseFloat(document.getElementById('mesh-node-height').value)||50;
                var dP=parseFloat(document.getElementById('mesh-tx-power').value)||30;
                var pts=window._rfInterpolatePoints(link.fromNode.lat,link.fromNode.lon,link.toNode.lat,link.toNode.lon,20);
                var elev;try{elev=await window._rfGetElevations(pts);}catch(e){elev={elevations:pts.map(function(){return 0;}),source:'flat'};}
                var dists=pts.map(function(p){return hav(link.fromNode.lat,link.fromNode.lon,p.lat,p.lon);});
                var hF=link.fromNode.height||dH,hT=link.toNode.height||dH;
                var pwr=Math.min(link.fromNode.power||dP,link.toNode.power||dP);
                var analysis=window._rfAnalyzePath(elev.elevations,dists,hF,hT,proto.freq[0]);
                var budget=window._rfLinkBudget(analysis,pwr,proto.rxSens,2,2,proto.freq[0],0);
                link.analysis=analysis;link.terrainDone=true;link.elevSource=elev.source;
                link.margin=budget.margin;link.diffLoss=analysis.diffLoss;link.hasLOS=analysis.hasLOS;
            }catch(e){svg.innerHTML='<text x="350" y="90" fill="#f87171" font-size="11" text-anchor="middle">Terrain error</text>';}
        }
        if(link.analysis){
            // Draw the profile using the RF terrain draw function but targeting our SVG
            var analysis=link.analysis;
            var proto=meshGetDefaultRadio();
            // Build profile HTML manually (same as drawProfile but for mesh SVG)
            var pts=analysis.points,totalD=analysis.totalD;
            var W=700,H=180,PX=50,PY=14,PB=22;
            var plotW=W-2*PX,plotH=H-PY-PB;
            var elevs=pts.map(function(p){return p.elev;}),losVals=pts.map(function(p){return p.los;});
            var maxF=Math.max.apply(null,pts.map(function(p){return p.f1;}));
            var allH=elevs.concat(losVals);
            var minHv=Math.min.apply(null,allH)-maxF*0.3,maxHv=Math.max.apply(null,allH)+maxF*0.5;
            var rangeH=maxHv-minHv||1;
            function toX(d){return PX+(plotW*d)/totalD;}
            function toY(h){return PY+plotH*(1-(h-minHv)/rangeH);}
            var html='<defs><linearGradient id="mtg" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#1a3a2a" stop-opacity="0.9"/><stop offset="100%" stop-color="#0a1510" stop-opacity="0.5"/></linearGradient></defs>';
            // Grid
            var step=Math.pow(10,Math.floor(Math.log10(rangeH/4)));
            for(var v=Math.ceil(minHv/step)*step;v<=maxHv;v+=step){
                html+='<line x1="'+PX+'" y1="'+toY(v)+'" x2="'+(W-PX)+'" y2="'+toY(v)+'" stroke="#162030" stroke-width="0.3"/>';
                html+='<text x="'+(PX-4)+'" y="'+(toY(v)+3)+'" fill="#4a5f75" font-size="7" text-anchor="end">'+Math.round(v)+'m</text>';
            }
            // Terrain fill
            var tPath=pts.map(function(p,i){return(i===0?'M':'L')+toX(p.dist).toFixed(1)+','+toY(p.elev).toFixed(1);}).join(' ');
            tPath+=' L'+toX(totalD).toFixed(1)+','+(H-PB)+' L'+PX+','+(H-PB)+' Z';
            html+='<path d="'+tPath+'" fill="url(#mtg)"/>';
            // Terrain color segments
            for(var i=0;i<pts.length-1;i++){
                var c=pts[i].obstructed?'#f87171':pts[i].fresnelObs?'#eab308':'#4ade80';
                html+='<line x1="'+toX(pts[i].dist)+'" y1="'+toY(pts[i].elev)+'" x2="'+toX(pts[i+1].dist)+'" y2="'+toY(pts[i+1].elev)+'" stroke="'+c+'" stroke-width="1.5"/>';
            }
            // LOS line
            html+='<line x1="'+toX(0)+'" y1="'+toY(pts[0].los)+'" x2="'+toX(totalD)+'" y2="'+toY(pts[pts.length-1].los)+'" stroke="#22d3ee" stroke-width="1.5"/>';
            // Fresnel zone
            var fTop=pts.map(function(p,i){return(i===0?'M':'L')+toX(p.dist).toFixed(1)+','+toY(p.los+p.f60).toFixed(1);}).join(' ');
            var fBot=pts.slice().reverse().map(function(p){return'L'+toX(p.dist).toFixed(1)+','+toY(p.los-p.f60).toFixed(1);}).join(' ');
            html+='<path d="'+fTop+' '+fBot+' Z" fill="rgba(34,211,238,0.06)" stroke="rgba(34,211,238,0.3)" stroke-width="0.5" stroke-dasharray="3,2"/>';
            // Endpoints
            html+='<circle cx="'+toX(0)+'" cy="'+toY(pts[0].los)+'" r="4" fill="'+link.fromNode.color+'" stroke="#0b0f14" stroke-width="1.5"/>';
            html+='<text x="'+toX(0)+'" y="'+(toY(pts[0].los)-8)+'" fill="'+link.fromNode.color+'" font-size="8" text-anchor="middle" font-weight="700">'+link.from+'</text>';
            html+='<circle cx="'+toX(totalD)+'" cy="'+toY(pts[pts.length-1].los)+'" r="4" fill="'+link.toNode.color+'" stroke="#0b0f14" stroke-width="1.5"/>';
            html+='<text x="'+toX(totalD)+'" y="'+(toY(pts[pts.length-1].los)-8)+'" fill="'+link.toNode.color+'" font-size="8" text-anchor="middle" font-weight="700">'+link.to+'</text>';
            svg.innerHTML=html;
        } else {
            svg.innerHTML='<text x="350" y="90" fill="#4a5f75" font-size="11" text-anchor="middle">No terrain data — analyze mesh first</text>';
        }
        // Detail line
        var mc=link.margin>10?'#4ade80':link.margin>0?'#eab308':'#f87171';
        detail.innerHTML='<span style="color:'+mc+';font-weight:700;">'+link.margin.toFixed(1)+' dB margin</span> · FSPL '+link.fspl.toFixed(1)+' dB · '+(link.terrainDone?'Diffr '+link.diffLoss.toFixed(1)+' dB · LOS '+(link.hasLOS?'<span style=color:#4ade80>clear</span>':'<span style=color:#f87171>blocked</span>')+' · '+link.elevSource:'FSPL only');
        // Sight line on map
        if(meshProfileSightLine&&meshMap){meshMap.removeLayer(meshProfileSightLine);meshProfileSightLine=null;}
        var L=window.L;
        if(L&&meshMap){
            meshProfileSightLine=L.polyline([[link.fromNode.lat,link.fromNode.lon],[link.toNode.lat,link.toNode.lon]],{color:'#ffffff',weight:2,dashArray:'4,6',opacity:0.6}).addTo(meshMap);
            meshMap.fitBounds([[link.fromNode.lat,link.fromNode.lon],[link.toNode.lat,link.toNode.lon]],{padding:[40,40]});
        }
        panel.style.display='block';
        panel.scrollIntoView({behavior:'smooth',block:'nearest'});
        // Build trace slider for this link
        meshBuildLinkTrace(link);
    }
    var meshProfileSightLine=null;
    var meshTraceMarker=null;
    var meshTraceData=null;

    // Build trace for mesh link when profile is shown
    function meshBuildLinkTrace(link){
        var proto=meshGetDefaultRadio();
        var STEPS=50;
        var pts=[];
        for(var s=0;s<=STEPS;s++){
            var t=s/STEPS;
            var lat=link.fromNode.lat+(link.toNode.lat-link.fromNode.lat)*t;
            var lon=link.fromNode.lon+(link.toNode.lon-link.fromNode.lon)*t;
            var d=hav(link.fromNode.lat,link.fromNode.lon,lat,lon)/1000;
            var fspl=d>0.001?(20*Math.log10(d)+20*Math.log10(proto.freq[0])+32.44):0;
            var pwr=Math.min(link.fromNode.power||30,link.toNode.power||30);
            var margin=pwr+2+2-fspl-proto.rxSens;
            pts.push({lat:lat,lon:lon,d:d,margin:margin});
        }
        meshTraceData=pts;
        document.getElementById('mesh-link-trace').style.display='block';
        document.getElementById('mesh-trace-slider').value=0;
        meshTraceUpdate();
    }

    window.meshTraceUpdate=function(){
        if(!meshTraceData||!meshTraceData.length)return;
        var pct=parseInt(document.getElementById('mesh-trace-slider').value);
        var idx=Math.min(Math.floor(pct/100*meshTraceData.length),meshTraceData.length-1);
        var pt=meshTraceData[idx];
        var L=window.L;
        if(!meshTraceMarker&&L&&meshMap){
            meshTraceMarker=L.circleMarker([pt.lat,pt.lon],{radius:5,color:'#fff',fillColor:'#22d3ee',fillOpacity:1,weight:2}).addTo(meshMap);
        } else if(meshTraceMarker){
            meshTraceMarker.setLatLng([pt.lat,pt.lon]);
        }
        var mc=pt.margin>15?'#4ade80':pt.margin>6?'#eab308':pt.margin>0?'#f59e0b':'#ef4444';
        var status=pt.margin>15?'STRONG':pt.margin>6?'OK':pt.margin>0?'WEAK':'LOST';
        document.getElementById('mesh-trace-info').innerHTML='<span style="color:'+mc+';">'+status+'</span> '+pt.margin.toFixed(1)+' dB \u00B7 '+pt.d.toFixed(2)+' km';
    };

    window.meshExport=function(){
        var h=document.getElementById('mesh-health');
        var m=document.getElementById('mesh-matrix');
        var i=document.getElementById('mesh-issues');
        var now=new Date().toISOString().split('T')[0];
        var proto=meshGetDefaultRadio();
        var html='<!DOCTYPE html><html><head><meta charset="utf-8"><title>Mesh RF Report</title>';
        html+='<style>*{margin:0;padding:0;box-sizing:border-box;}body{font-family:sans-serif;background:#0b0f14;color:#c9d1d9;padding:32px;font-size:11px;}h1{font-size:18px;color:#22d3ee;margin-bottom:4px;}h2{font-size:11px;color:#8b949e;margin-bottom:20px;}table{width:100%;border-collapse:collapse;font-size:10px;margin:8px 0;}td,th{padding:4px;border-bottom:1px solid #21262d;}@media print{body{background:#fff;color:#1a1a1a;}td,th{border-color:#ddd;}}</style>';
        html+='</head><body><h1>Mesh / Swarm RF Report</h1><h2>'+proto.name+' | '+meshNodes.filter(function(n){return n.active;}).length+' nodes | '+now+'</h2>';
        if(h)html+=h.innerHTML;
        if(m)html+=m.innerHTML;
        if(i)html+=i.innerHTML;
        html+='<div style="margin-top:24px;font-size:9px;color:#4a5f75;text-align:center;border-top:1px solid #21262d;padding-top:12px;">Forge Mesh Planner · uas-forge.com/tools/ · AI Wingman by Midwest Nice Advisory LLC</'+'div>';
        html+='</'+'body></'+'html>';
        var w=window.open('','_blank');
        if(w){w.document.write(html);w.document.close();}
    };

    document.addEventListener('toolchange', function(event) {
        if (event.detail === 'mesh-planner') {
            initMeshMap();
            if (meshMap) setTimeout(function(){ meshMap.invalidateSize(); }, 50);
        }
    });
    // Hash routing handled by global router below
})();

