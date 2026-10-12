(function () {
  'use strict';
  const E = window.MediFlowEngine;
  const STORAGE = 'mediflow.preview.v1';
  const zone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  const now = () => Date.now();
  const today = () => E.localDate(now(), zone);
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const time = stamp => new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: zone }).format(stamp);
  const dayLabel = (date, options) => new Intl.DateTimeFormat('en-GB', { timeZone: 'UTC', ...options }).format(new Date(date + 'T12:00:00Z'));
  const icons = {
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/>',
    pill: '<path d="M8 4a5.65 5.65 0 0 1 8 8l-4 4a5.65 5.65 0 0 1-8-8z" transform="translate(2 2)"/><path d="m8 8 8 8"/>',
    chart: '<path d="M4 19h16M6 15v-5m6 5V5m6 10V8"/>',
    settings: '<path d="M4 7h5m4 0h7M4 17h9m4 0h3"/><circle cx="11" cy="7" r="2"/><circle cx="15" cy="17" r="2"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    check: '<path d="m5 12 4 4L19 6"/>',
    more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
    close: '<path d="m6 6 12 12M6 18 18 6"/>',
    bell: '<path d="M5 17h14l-2-3V9a5 5 0 0 0-10 0v5zM10 20h4"/>',
    calendar: '<rect x="4" y="5" width="16" height="16" rx="3"/><path d="M8 3v4m8-4v4M4 11h16"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    search: '<circle cx="10" cy="10" r="6"/><path d="m15 15 5 5"/>',
    shield: '<path d="M12 3 4 6v6c0 5 8 9 8 9s8-4 8-9V6z"/><path d="m8 12 3 3 5-6"/>',
    arrow: '<path d="M5 12h14m-5-5 5 5-5 5"/>',
    download: '<path d="M12 3v12m-5-5 5 5 5-5M4 17v4h16v-4"/>',
    upload: '<path d="M12 16V4m-5 5 5-5 5 5M4 17v4h16v-4"/>',
    moon: '<path d="M20 14A8.5 8.5 0 0 1 10 4a8.5 8.5 0 1 0 10 10Z"/>',
    monitor: '<rect x="3" y="4" width="18" height="13" rx="2"/><path d="M9 21h6m-3-4v4"/>',
    leaf: '<path d="M19 4C7 3 3 8 5 15s12 5 14-11Z"/><path d="m6 19 8-9"/>',
    undo: '<path d="M8 5 3 10l5 5M3 10h10a6 6 0 0 1 6 6v3"/>',
  };
  const icon = name => `<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[name] || icons.pill}</svg>`;
  const logo = () => '<svg viewBox="0 0 44 44" aria-hidden="true"><g transform="rotate(-18 22 22)" fill="currentColor" style="color:var(--accent)"><rect x="7" y="16" width="30" height="12" rx="6"/><rect x="16" y="7" width="12" height="30" rx="6"/></g><circle cx="19" cy="19" r="3" fill="white" opacity=".4"/></svg>';
  const brand = () => `<div class="brand">${logo()}<span>mediflow</span></div>`;
  const flower = () => `<svg viewBox="0 0 200 200" fill="none" aria-hidden="true"><circle cx="100" cy="100" r="87" fill="#f6f2fa"/><circle cx="100" cy="100" r="75" stroke="#e7deef" stroke-dasharray="2 6"/>${[0,72,144,216,288].map(a=>`<g transform="rotate(${a} 100 100)"><ellipse cx="100" cy="66" rx="25" ry="42" fill="#b4a1cc" opacity=".72"/><ellipse cx="95" cy="63" rx="17" ry="33" fill="#d9cce8" opacity=".85"/><path d="M100 37v51" stroke="#f5effa" stroke-width="1" opacity=".6"/></g>`).join('')}<circle cx="100" cy="100" r="22" fill="#faf4e7"/><circle cx="100" cy="100" r="10" fill="#c1a779"/><circle cx="96" cy="96" r="3" fill="#fff" opacity=".8"/><circle cx="163" cy="50" r="4" fill="#c4b592" opacity=".65"/><circle cx="37" cy="136" r="3" fill="#b4a1cc" opacity=".7"/></svg>`;
  const glyph = m => `<span class="medicine-glyph tone-${m.color}"><svg viewBox="0 0 30 36" fill="none" aria-hidden="true"><rect x="6" y="2" width="18" height="32" rx="9" stroke="currentColor" stroke-width="1.6"/><path d="M6 18h18v7a9 9 0 0 1-18 0Z" fill="currentColor" opacity=".25"/><path d="M6 18h18M11 7l-1 4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg></span>`;
  const pill = (text, color = '') => `<span class="pill ${color}">${escape(text)}</span>`;
  const button = (text, action, cls = '', extra = '', ico = '') => `<button type="button" class="btn ${cls}" data-action="${action}" ${extra}>${ico ? icon(ico) : ''}${text}</button>`;
  let state, storageAvailable = true, storageIssue = '', view = 'today', selectedDate = today(), query = '', editor = null, selectedDays = [], selectedColor = 0, stagedBackup = null, lastFocus = null;
  try {
    const saved = localStorage.getItem(STORAGE);
    if (saved) {
      const parsed = JSON.parse(saved);
      state = E.importBackup(JSON.stringify({ ...parsed, exportedAt: new Date().toISOString() }));
      state.settings = { ...state.settings, ...parsed.settings };
    } else state = E.seed(now(), zone);
  } catch {
    state = E.seed(now(), zone);
    storageIssue = 'Previous preview data could not be read. This session uses a sample routine.';
  }
  function persist() {
    try { localStorage.setItem(STORAGE, JSON.stringify(state)); storageAvailable = true; }
    catch { storageAvailable = false; toast('Changes are kept for this session only. Browser storage is unavailable.'); }
  }
  function transact(operation, success) {
    const candidate = structuredClone(state);
    try { operation(candidate); state = candidate; persist(); render(); if (success) toast(success); return true; }
    catch (error) { toast(error.message); return false; }
  }
  let toastTimeout;
  function toast(message) {
    const node = document.getElementById('toast'); node.textContent = message; node.classList.add('visible');
    clearTimeout(toastTimeout); toastTimeout = setTimeout(() => node.classList.remove('visible'), 4200);
  }
  const destinations = [['today','sun','Today'],['cabinet','pill','Cabinet'],['insights','chart','Insights'],['settings','settings','Settings']];
  function nav(mobile = false) {
    return destinations.map(([key, ico, label]) => `<button type="button" class="${view === key ? 'active' : ''}" data-action="navigate" data-view="${key}" ${view === key ? 'aria-current="page"' : ''}><span class="nav-icon">${icon(ico)}</span><span>${label}</span>${view === key && !mobile ? '<i class="nav-dot"></i>' : ''}</button>`).join('');
  }
  function header(title, subtitle, eyebrow = '', add = false) {
    return `<header class="page-header"><div>${eyebrow ? `<div class="eyebrow">${escape(eyebrow)}</div>` : ''}<h1>${title}</h1>${subtitle ? `<p>${subtitle}</p>` : ''}</div>${add ? button('<span>Add medication</span>', 'add', '', 'aria-label="Add medication"', 'plus') : ''}</header>`;
  }
  function render() {
    const theme = state.settings.theme;
    document.documentElement.dataset.theme = theme === 'DARK' || (theme === 'SYSTEM' && matchMedia('(prefers-color-scheme:dark)').matches) ? 'dark' : 'light';
    document.getElementById('app').innerHTML = `<div class="layout"><aside class="sidebar">${brand()}<div class="subtitle">A little more calm.</div><nav class="nav" aria-label="Main navigation">${nav()}</nav><div class="sidebar-bottom"><div class="mini-garden">${flower()}</div><p class="sidebar-note">Small steps.<br>A steadier everyday.</p><div class="privacy-chip">${icon('shield')}<span>${storageAvailable ? 'Only on this device' : 'Session-only preview'}</span></div></div></aside><div class="main-shell"><div class="topbar"><div class="breadcrumb">Your personal care space <span style="padding:0 8px">/</span> <strong>${destinations.find(d=>d[0]===view)[2]}</strong></div><div class="mobile-brand">${brand()}</div><div class="top-actions"><span class="pill"><span class="dot"></span>Interactive preview</span><button type="button" class="icon-btn" data-action="navigate" data-view="settings" aria-label="Reminder settings">${icon('bell')}</button><div class="avatar" aria-hidden="true">MF</div></div></div><main id="main" tabindex="-1">${storageIssue ? `<div class="error-banner">${escape(storageIssue)}</div>` : ''}${({today: todayView, cabinet: cabinetView, insights: insightsView, settings: settingsView})[view]()}</main><footer class="footer"><span>MediFlow · A thoughtful home for your routine.</span><span>${state.settings.sample ? 'Fictional sample data' : 'Personal preview data'} · Device reminders run in the Android app</span></footer></div><nav class="mobile-nav" aria-label="Mobile navigation">${nav(true)}</nav></div>`;
    document.title = `MediFlow · ${destinations.find(d=>d[0]===view)[2]}`;
  }
  function todayView() {
    const doses = E.dayOccurrences(state, selectedDate, zone), records = new Map(state.records.map(r=>[r.key,r]));
    const taken = doses.filter(d=>records.get(d.key)?.status === 'TAKEN').length;
    const weekday = new Date(selectedDate+'T12:00:00Z').getUTCDay() || 7, monday = E.addDays(selectedDate, -(weekday-1));
    const active = state.medications.filter(m=>!m.archived);
    const pending = E.dayOccurrences(state,today(),zone).filter(d=>!records.has(d.key) && d.scheduledAt>=now());
    const next = pending[0] || E.dayOccurrences(state,E.addDays(today(),1),zone).find(d=>!records.has(d.key));
    return `${header(selectedDate===today()?'Your day, in balance.':'Your daily routine.','',dayLabel(selectedDate,{weekday:'long',month:'long',day:'numeric'}),true)}<div class="dashboard-grid"><section class="content-col"><div class="hero"><div class="hero-copy"><div class="eyebrow">One step at a time</div><h2>Small moments.<br>Steady care.</h2><p>${taken} of ${doses.length} scheduled entries recorded</p><div class="progress-track" role="progressbar" aria-label="Today's entries recorded as taken" aria-valuemin="0" aria-valuemax="${doses.length || 1}" aria-valuenow="${taken}"><span style="width:${doses.length?taken/doses.length*100:0}%"></span></div><div class="hero-meta"><span>${state.settings.sample?'SAMPLE ROUTINE':'YOUR ROUTINE'}</span><span>${doses.length?Math.round(taken/doses.length*100):0}%</span></div></div><div class="hero-art">${flower()}</div></div><div class="weekbar" aria-label="Choose a day">${Array.from({length:7},(_,i)=>{const d=E.addDays(monday,i);return `<button type="button" class="day ${d===selectedDate?'active':''}" data-action="date" data-date="${d}" aria-label="${dayLabel(d,{weekday:'long',month:'long',day:'numeric'})}" aria-pressed="${d===selectedDate}"><small>${dayLabel(d,{weekday:'short'}).slice(0,3)}</small><strong>${Number(d.slice(-2))}</strong><i class="day-dot" style="opacity:${d===today()?1:0}"></i></button>`}).join('')}</div><div class="section-heading"><div><h2>Your schedule</h2><p>${escape(zone.replaceAll('_',' '))} · Times follow your chosen travel setting</p></div><button type="button" class="icon-btn" data-action="calendar" aria-label="Choose another date">${icon('calendar')}</button></div><div class="dose-list">${doses.length?doses.map(d=>doseCard(d,records.get(d.key))).join(''):`<div class="card empty">${glyph({color:0})}<h3>A little room for your routine</h3><p>No medication is scheduled for this day.</p>${button('Add medication','add','secondary','','plus')}</div>`}</div><button type="button" class="add-row" data-action="add">${icon('plus')}Add medication to your routine</button></section><aside class="aside"><div class="card next-card"><div class="eyebrow">Your next moment</div><div class="next-time">${next?time(next.scheduledAt):'All clear'}</div><h3>${next?escape(next.medication.name):'A little breathing room'}</h3><p style="margin-top:5px">${next?`${E.quantity(next.medication.quantityMilli)} ${E.forms[next.medication.form][1]} · ${E.localDate(next.scheduledAt,zone)===today()?'Today':'Tomorrow'}`:'No upcoming entries in the next day.'}</p><div class="divider"></div>${button('View routine','next','outline full',next?`data-date="${E.localDate(next.scheduledAt,zone)}"`:'', 'arrow')}</div><div class="card"><div class="row between"><h3>Cabinet check</h3>${icon('pill')}</div><div class="aside-list">${active.slice(0,3).map(m=>`<div class="aside-medicine">${glyph(m)}<div><strong>${escape(m.name)}</strong><small>${m.stockMilli<=m.lowStockMilli?'Review stock':'On hand'}</small></div><span class="count" style="color:${m.stockMilli<=m.lowStockMilli?'var(--peach)':'var(--ink)'}">${E.quantity(m.stockMilli)}</span></div>`).join('') || '<p>Your cabinet is ready for its first entry.</p>'}</div><div style="margin-top:15px">${button('Open cabinet','navigate','text full','data-view="cabinet"','arrow')}</div></div><div class="card gentle-card">${icon('leaf')}<h3>One small step,<br>then the next.</h3><p>Your routine, at your pace.<br>You can always come back.</p></div></aside></div>`;
  }
  function doseCard(dose, record) {
    const m=dose.medication, key=escape(dose.key), future=E.localDate(dose.scheduledAt,zone)>today();
    return `<article class="card dose-card ${record?.status==='TAKEN'?'taken':''}" data-testid="dose-${m.id}"><div class="dose-head">${glyph(m)}<div class="dose-title"><h3>${escape(m.name)}</h3><p>${E.quantity(m.quantityMilli)} ${E.forms[m.form][1]}${m.strength?' · '+escape(m.strength):''}</p></div><time class="dose-time">${time(dose.scheduledAt)}</time></div><div class="dose-bottom">${record?`${pill(record.status==='TAKEN'?'✓ Taken':'Skipped',record.status==='TAKEN'?'sage':'')} ${button('Undo','undo','text',`data-key="${key}"`,'undo')}`:`<span class="state-label">${future?'Upcoming':dose.scheduledAt>now()?'Scheduled':'Ready to record'}</span><div class="actions">${future?'':`${button('Mark taken','taken','secondary',`data-key="${key}" data-testid="take-${m.id}"`,'check')}<button type="button" class="icon-btn" data-action="dose-options" data-key="${key}" aria-label="More actions for ${escape(m.name)}">${icon('more')}</button>`}</div>`}</div></article>`;
  }
  function cabinetView() {
    const meds=state.medications.filter(m=>!m.archived && `${m.name} ${m.strength}`.toLowerCase().includes(query.toLowerCase()));
    return `${header('Your cabinet','Everything you need, in one place.','A little organisation',true)}<label class="search">${icon('search')}<input id="search" type="search" placeholder="Find a medication" aria-label="Find a medication" value="${escape(query)}"></label><div class="cabinet-grid">${meds.map(m=>`<article class="card medicine-card" data-testid="medicine-${m.id}"><div class="dose-head">${glyph(m)}<div class="dose-title"><h3>${escape(m.name)}</h3><p>${escape(m.strength || E.forms[m.form][0])} · ${E.forms[m.form][0]}</p></div><button type="button" class="icon-btn" data-action="medicine-options" data-id="${m.id}" aria-label="Options for ${escape(m.name)}">${icon('more')}</button></div><div class="row between" style="margin-top:23px"><div><div class="stock-number">${E.quantity(m.stockMilli)} <small>${E.forms[m.form][1]}${m.form==='LIQUID'?'':'s'} left</small></div><div class="small muted" style="font-size:10px;margin-top:4px">${m.stockMilli<=m.lowStockMilli?'Time to review your stock':'Stock looks comfortable'}</div></div>${button('Refill','refill','outline',`data-id="${m.id}"`,'plus')}</div><div class="divider"></div><div class="row between"><div class="schedule-line">${icon('clock')}${escape(m.times.replaceAll(',',' · '))}</div>${m.stockMilli<=m.lowStockMilli?pill('Low stock','peach'):pill('In your routine','sage')}</div></article>`).join('') || `<div class="card empty" style="grid-column:1/-1"><h3>${query?'No matching medications':'Your cabinet starts here'}</h3><p>${query?'Try a different name.':'Add a medication to begin your personal routine.'}</p>${query?'':button('Add medication','add','','','plus')}</div>`}</div><p class="note" style="margin-top:22px">Stock changes when you record or undo an entry. Skipped entries leave it unchanged.</p>`;
  }
  function insightsView() {
    const days=Array.from({length:7},(_,i)=>E.addDays(today(),i-6)), week=state.records.filter(r=>days.includes(E.localDate(r.scheduledAt,zone)));
    const taken=week.filter(r=>r.status==='TAKEN').length, skipped=week.length-taken, active=state.medications.filter(m=>!m.archived).length;
    const counts=days.map(d=>week.filter(r=>E.localDate(r.scheduledAt,zone)===d && r.status==='TAKEN').length), skips=days.map(d=>week.filter(r=>E.localDate(r.scheduledAt,zone)===d && r.status==='SKIPPED').length);
    const max=Math.max(3,...counts.map((v,i)=>v+skips[i]));
    const chart=`<svg viewBox="0 0 720 205" role="img" aria-label="Entries this week: ${counts.map((n,i)=>`${dayLabel(days[i],{weekday:'short'})} ${n} taken, ${skips[i]} skipped`).join('; ')}">${[0,1,2,3].map(i=>`<line x1="35" y1="${15+i*48}" x2="707" y2="${15+i*48}" stroke="var(--line)" stroke-dasharray="3 4"/><text x="18" y="${19+i*48}" fill="var(--muted)" font-size="9" text-anchor="middle">${Math.round(max*(3-i)/3)}</text>`).join('')}${counts.map((n,i)=>{const height=n/max*143, sh=skips[i]/max*143,x=65+i*95;return `<rect x="${x}" y="${159-height-sh}" width="34" height="${height+sh||2}" rx="7" fill="var(--lav)"/>${n?`<rect x="${x}" y="${159-height}" width="34" height="${height}" rx="7" fill="var(--accent)"/>`:''}<text x="${x+17}" y="185" fill="var(--muted)" font-size="10" text-anchor="middle">${dayLabel(days[i],{weekday:'short'})}</text>`}).join('')}</svg>`;
    return `${header('A little perspective','Your recorded routine, at a glance.','Your week in view')}<div class="stats-grid"><div class="card"><div class="stat-label">Recorded as taken</div><div class="stat-number">${taken}</div><div class="stat-foot">Over the last seven days</div></div><div class="card"><div class="stat-label">Recorded as skipped</div><div class="stat-number">${skipped}</div><div class="stat-foot">Stock left unchanged</div></div><div class="card"><div class="stat-label">In your routine</div><div class="stat-number">${active}</div><div class="stat-foot">Active medications</div></div></div><div class="card chart-card"><div class="row between"><div><h2>Your week</h2><p class="small muted" style="margin-top:5px">Entries over the last seven days</p></div>${pill('7 days')}</div>${chart}<div class="legend"><span><i></i>Taken</span><span><i style="background:var(--lav)"></i>Skipped</span></div>${state.settings.sample?'<p class="note" style="margin-top:12px">Includes fictional sample history.</p>':''}</div><div class="section-heading"><div><h2>Recent entries</h2><p>An accurate record of what you logged</p></div>${button('Export backup','export','text','','download')}</div><div class="card" style="padding-top:7px;padding-bottom:7px">${state.records.slice().sort((a,b)=>b.recordedAt-a.recordedAt).slice(0,30).map(r=>`<div class="history-row"><span class="history-icon">${icon(r.status==='TAKEN'?'check':'arrow')}</span><div class="grow"><div class="history-title">${escape(r.medicationName)}</div><div class="history-date">${new Intl.DateTimeFormat('en-GB',{month:'short',day:'numeric',hour:'2-digit',minute:'2-digit',timeZone:zone}).format(r.scheduledAt)}</div></div>${pill(r.status==='TAKEN'?'Taken':'Skipped',r.status==='TAKEN'?'sage':'')}${button('Undo','undo','text',`data-key="${escape(r.key)}"`)}</div>`).join('') || '<div class="empty"><h3>Your history will appear here</h3><p>Record an entry from Today to get started.</p></div>'}</div>`;
  }
  function settingsView() {
    return `${header('Make it yours','A routine that fits your day.','Thoughtfully personal')}<div class="settings-grid"><div class="stack"><section class="card"><h2>Your reminders</h2><div class="setting-row"><div><h3>Device reminders</h3><p>Native reminders are configured on your Android device. This preview does not send notifications.</p></div>${icon('bell')}</div><div class="divider" style="margin:4px 0 10px"></div><div class="setting-row"><div><h3>Discreet notification text</h3><p>Preview the setting that hides medication names in Android notifications.</p></div><button type="button" class="switch" role="switch" aria-label="Discreet notification preview" aria-checked="${state.settings.discreet}" data-action="discreet"></button></div><p class="note">Changes in this browser preview are separate from settings on your Android device.</p></section><section class="card"><h2>Appearance</h2><p class="note" style="margin-top:5px">Choose the space that feels right.</p><div class="theme-options">${[['LIGHT','sun','Light'],['DARK','moon','Dark'],['SYSTEM','monitor','System']].map(([value,ico,label])=>`<button type="button" class="theme-option ${state.settings.theme===value?'active':''}" data-action="theme" data-theme="${value}" aria-pressed="${state.settings.theme===value}">${icon(ico)}${label}</button>`).join('')}</div></section></div><div class="stack"><section class="card"><div class="row" style="margin-bottom:13px">${icon('shield')}<h2>Your data stays with you</h2></div><p class="note">No account, advertising or cloud sync. This preview stores your routine in this browser.</p><div class="stack" style="gap:9px;margin-top:21px">${button('Export a backup','export','outline full','','download')}${button('Restore a backup','import','text full','','upload')}</div><div class="divider"></div><p class="note">Backups use the same format as the Android app. Exported files include medication history and are not password protected.</p></section><div class="settings-about">${brand()}<div class="version">VERSION 1.0.0</div><p class="note" style="margin-top:15px">Organizes the schedules you enter.<br>Does not prescribe treatment.</p><div style="margin-top:10px">${button('Reset sample routine','reset','text')}</div></div></div></div>`;
  }
  function modal(title, content, footer = '') {
    lastFocus = document.activeElement;
    document.getElementById('app').inert = true;
    document.getElementById('modal-root').innerHTML = `<div class="modal-backdrop"><section class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title"><div class="modal-header"><h2 id="modal-title">${title}</h2><button type="button" class="icon-btn" data-action="close" aria-label="Close dialog">${icon('close')}</button></div>${content}${footer?`<div class="modal-footer">${footer}</div>`:''}</section></div>`;
    document.body.style.overflow='hidden';
    requestAnimationFrame(()=>document.querySelector('.modal input, .modal select, .modal button')?.focus());
  }
  function closeModal() { document.getElementById('app').inert = false;document.getElementById('modal-root').innerHTML='';document.body.style.overflow='';editor=null;stagedBackup=null;if(lastFocus?.isConnected)lastFocus.focus();else document.getElementById('main')?.focus({preventScroll:true}); }
  const field = (label,name,value,type='text',full=false,extra='') => `<div class="field ${full?'full':''}"><label for="f-${name}">${label}</label><input id="f-${name}" name="${name}" type="${type}" value="${escape(value)}" ${extra}></div>`;
  function openEditor(id) {
    editor = id ? structuredClone(state.medications.find(m=>m.id===id)) : {id:'med-'+crypto.randomUUID(),name:'',strength:'',form:'TABLET',quantityMilli:1000,stockMilli:30000,lowStockMilli:5000,times:'08:00',weekdays:'1,2,3,4,5,6,7',startDate:today(),endDate:null,timePolicy:'LOCAL_CLOCK',homeZone:zone,notes:'',color:0,archived:false,revision:0};
    selectedDays=editor.weekdays.split(',');selectedColor=editor.color;
    modal(id?'Edit medication':'Add medication',`<p class="modal-intro">Use the details and schedule from your own care plan.</p><form id="medicine-form" novalidate><div class="form-grid">${field('Medication name','name',editor.name,'text',true,'required maxlength="100" placeholder="Name from your packaging"')}${field('Strength or label (optional)','strength',editor.strength,'text',true,'maxlength="100"')}<div class="field"><label for="f-form">Form</label><select id="f-form" name="form">${Object.entries(E.forms).map(([key,[label]])=>`<option value="${key}" ${editor.form===key?'selected':''}>${label}</option>`).join('')}</select></div>${field('Units per entry','quantity',E.quantity(editor.quantityMilli),'text',false,'inputmode="decimal"')}${field('Current stock','stock',E.quantity(editor.stockMilli),'text',false,'inputmode="decimal"')}${field('Reminder times','times',editor.times.replaceAll(',',', '),'text',false,'placeholder="08:00, 20:00"')}<div class="field full"><small>Use 24-hour times separated by commas. Up to 12 times.</small></div><div class="field full"><span class="field-label">Days of the week</span><div class="weekdays">${['Mo','Tu','We','Th','Fr','Sa','Su'].map((d,i)=>`<button type="button" class="weekday ${selectedDays.includes(String(i+1))?'active':''}" data-action="weekday" data-day="${i+1}" aria-pressed="${selectedDays.includes(String(i+1))}">${d}</button>`).join('')}</div></div><div class="field full"><span class="field-label">Color</span><div class="color-choices">${['Lilac','Sage','Peach','Blue'].map((name,i)=>`<button type="button" class="color-choice ${selectedColor===i?'active':''}" data-action="color" data-color="${i}" aria-pressed="${selectedColor===i}"><i style="background:${['#b5a2ce','#8fb5a1','#d8b292','#9bb7cf'][i]}"></i>${name}</button>`).join('')}</div></div></div><details><summary>Dates, travel and notes</summary><div class="form-grid">${field('Start date','start',editor.startDate,'date')}${field('End date (optional)','end',editor.endDate||'','date')}${field('Low stock threshold','threshold',E.quantity(editor.lowStockMilli),'text',false,'inputmode="decimal"')}<div class="field"><label for="f-policy">When travelling</label><select id="f-policy" name="policy"><option value="LOCAL_CLOCK" ${editor.timePolicy==='LOCAL_CLOCK'?'selected':''}>Follow device clock</option><option value="HOME_ZONE" ${editor.timePolicy==='HOME_ZONE'?'selected':''}>Keep home time</option></select></div>${field('Home time zone','zone',editor.homeZone,'text',true)}<div class="field full"><label for="f-notes">Personal notes</label><textarea id="f-notes" name="notes" maxlength="1000">${escape(editor.notes)}</textarea></div></div></details><div id="form-error" role="alert"></div><div class="modal-footer">${button('Cancel','close','outline')}<button type="submit" class="btn" data-testid="save-medication">${icon('check')}Save medication</button></div></form>`);
  }
  document.addEventListener('submit', event=>{
    if(event.target.id!=='medicine-form')return;
    event.preventDefault();const data=new FormData(event.target);
    try {
      const medicine={...editor,name:data.get('name').trim(),strength:data.get('strength').trim(),form:data.get('form'),quantityMilli:E.parseQuantity(data.get('quantity')),stockMilli:E.parseQuantity(data.get('stock')),lowStockMilli:E.parseQuantity(data.get('threshold')),times:data.get('times').split(',').map(t=>t.trim()).sort().join(','),weekdays:selectedDays.slice().sort().join(','),startDate:data.get('start'),endDate:data.get('end')||null,timePolicy:data.get('policy'),homeZone:data.get('zone').trim(),notes:data.get('notes').trim(),color:selectedColor};
      const candidate=structuredClone(state);E.save(candidate,medicine);state=candidate;persist();closeModal();render();toast('Medication saved');
    }catch(error){document.getElementById('form-error').innerHTML=`<div class="form-error">${escape(error.message)}</div>`;document.getElementById('form-error').scrollIntoView({block:'nearest'});}
  });
  document.addEventListener('input',event=>{
    if(event.target.id==='search'){const position=event.target.selectionStart;query=event.target.value;render();const node=document.getElementById('search');node.focus();try{node.setSelectionRange(position,position)}catch{}}
  });
  document.addEventListener('click',event=>{
    const trigger=event.target.closest('[data-action]');if(!trigger)return;
    const {action,id,key}=trigger.dataset;
    if(action==='navigate'){view=trigger.dataset.view;query='';render();window.scrollTo({top:0});}
    else if(action==='date'){selectedDate=trigger.dataset.date;render();}
    else if(action==='next'){selectedDate=trigger.dataset.date||today();view='today';render();}
    else if(action==='calendar')modal('Choose a day',field('Date','calendar',selectedDate,'date',true),button('View day','apply-date'));
    else if(action==='apply-date'){const d=document.getElementById('f-calendar').value;if(E.validDate(d)){selectedDate=d;closeModal();render();}}
    else if(action==='add')openEditor();
    else if(action==='edit'){closeModal();openEditor(id);}
    else if(action==='close')closeModal();
    else if(action==='taken')transact(s=>E.record(s,key,'TAKEN',now(),zone),'Recorded. Inventory updated.');
    else if(action==='skip'){closeModal();transact(s=>E.record(s,key,'SKIPPED',now(),zone),'Skipped. Inventory unchanged.');}
    else if(action==='undo')transact(s=>E.undo(s,key),'Entry undone. Inventory restored.');
    else if(action==='dose-options')modal('This scheduled entry','<p class="modal-intro">Choose how you want to record this moment.</p><div class="stack">'+button('Skip this entry','skip','outline full',`data-key="${escape(key)}"`,'arrow')+button('Preview a 10-minute snooze','snooze','secondary full',`data-key="${escape(key)}"`,'clock')+'</div>');
    else if(action==='snooze'){closeModal();toast('Snooze preview: 10 minutes. Device reminders run in the Android app.');}
    else if(action==='medicine-options'){const m=state.medications.find(m=>m.id===id);modal(escape(m.name),'<div class="stack">'+button('Edit medication','edit','secondary full',`data-id="${id}"`)+button('Archive medication','archive-confirm','outline full',`data-id="${id}"`)+'</div>');}
    else if(action==='archive-confirm')modal('Archive this medication?','<p class="note">Upcoming entries will stop. Your recorded history will remain available.</p>',button('Keep medication','close','outline')+button('Archive','archive','danger',`data-id="${id}"`));
    else if(action==='archive'){closeModal();transact(s=>{const m=s.medications.find(m=>m.id===id);m.archived=true;m.revision++;},'Medication archived. History retained.');}
    else if(action==='refill'){const m=state.medications.find(m=>m.id===id);modal('Refill '+escape(m.name),'<p class="modal-intro">Add the quantity you received to your current stock.</p>'+field('Quantity added','refill','30','text',true,'inputmode="decimal"'),button('Cancel','close','outline')+button('Add to stock','apply-refill','',`data-id="${id}"`));}
    else if(action==='apply-refill'){try{const amount=E.parseQuantity(document.getElementById('f-refill').value);if(transact(s=>E.refill(s,id,amount),'Inventory updated'))closeModal();}catch(error){toast(error.message);}}
    else if(action==='weekday'){const d=trigger.dataset.day;selectedDays=selectedDays.includes(d)?selectedDays.filter(v=>v!==d):[...selectedDays,d];trigger.classList.toggle('active',selectedDays.includes(d));trigger.setAttribute('aria-pressed',selectedDays.includes(d));}
    else if(action==='color'){selectedColor=Number(trigger.dataset.color);document.querySelectorAll('.color-choice').forEach(n=>{n.classList.toggle('active',Number(n.dataset.color)===selectedColor);n.setAttribute('aria-pressed',Number(n.dataset.color)===selectedColor);});}
    else if(action==='theme')transact(s=>{s.settings.theme=trigger.dataset.theme;});
    else if(action==='discreet')transact(s=>{s.settings.discreet=!s.settings.discreet;});
    else if(action==='export'){const blob=new Blob([E.exportBackup(state)],{type:'application/json'}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=`MediFlow-backup-${today()}.json`;a.click();setTimeout(()=>URL.revokeObjectURL(url),2000);toast('Backup prepared');}
    else if(action==='import')document.getElementById('import-file').click();
    else if(action==='restore'){if(stagedBackup){state=stagedBackup;persist();closeModal();render();toast('Routine restored');}}
    else if(action==='reset')modal('Reset the sample routine?','<p class="note">This replaces changes made in this browser preview. Export a backup first if you want to keep them.</p>',button('Keep my routine','close','outline')+button('Reset sample','reset-confirm',''));
    else if(action==='reset-confirm'){state=E.seed(now(),zone);selectedDate=today();persist();closeModal();view='today';render();toast('Sample routine reset');}
  });
  document.getElementById('import-file').addEventListener('change',async event=>{
    const file=event.target.files[0];event.target.value='';if(!file)return;
    try{if(file.size>2097152)throw new Error('Backup must be smaller than 2 MB.');const restored=E.importBackup(await file.text());modal('Replace your current routine?',`<p class="note">This backup contains ${restored.medications.length} medications and ${restored.records.length} recorded entries. Export your current routine first if you want to keep a copy.</p>`,button('Cancel','close','outline')+button('Restore backup','restore'));stagedBackup=restored;}catch(error){toast('Backup was not restored. '+error.message);}
  });
  document.addEventListener('keydown',event=>{
    const dialog=document.querySelector('.modal');if(!dialog)return;
    if(event.key==='Escape'){closeModal();return;}
    if(event.key==='Tab'){
      const nodes=[...dialog.querySelectorAll('button,input,select,textarea,summary,[tabindex="0"]')].filter(n=>!n.disabled&&n.getClientRects().length);
      const first=nodes[0],last=nodes.at(-1);
      if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}
      else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
    }
  });
  window.addEventListener('storage',event=>{if(event.key===STORAGE&&event.newValue){try{const incoming=JSON.parse(event.newValue);const checked=E.importBackup(JSON.stringify({...incoming,exportedAt:new Date().toISOString()}));state={...checked,settings:incoming.settings};render();}catch{toast('A changed browser session could not be loaded.');}}});
  matchMedia('(prefers-color-scheme:dark)').addEventListener('change',()=>{if(state.settings.theme==='SYSTEM')render();});
  render();
})();
