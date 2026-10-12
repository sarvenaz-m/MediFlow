/* MediFlow companion domain model. No network calls or third-party runtime dependencies. */
(function (root, factory) {
  const engine = factory();
  if (typeof module === 'object' && module.exports) module.exports = engine;
  else root.MediFlowEngine = engine;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const MAX = 1000000000;
  const forms = { TABLET: ['Tablet', 'tablet'], CAPSULE: ['Capsule', 'capsule'], SOFTGEL: ['Softgel', 'softgel'], LIQUID: ['Liquid', 'mL'] };
  const formatters = new Map(), instants = new Map();
  const assert = (condition, message) => { if (!condition) throw new Error(message); };
  function parseQuantity(value) {
    const text = String(value).trim();
    assert(/^[0-9]{1,7}(\.[0-9]{1,3})?$/.test(text), 'Use a positive number with up to 3 decimal places.');
    const [whole, fraction = ''] = text.split('.');
    const number = Number(whole) * 1000 + Number(fraction.padEnd(3, '0'));
    assert(number <= MAX, 'Quantity is outside the supported range.');
    return number;
  }
  function quantity(value) {
    assert(Number.isSafeInteger(value), 'Invalid quantity.');
    return `${Math.floor(value / 1000)}${value % 1000 ? '.' + String(value % 1000).padStart(3, '0').replace(/0+$/, '') : ''}`;
  }
  function validDate(value) {
    return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(value + 'T00:00:00Z')) && new Date(value + 'T00:00:00Z').toISOString().slice(0, 10) === value;
  }
  function addDays(date, n) { const value = new Date(date + 'T12:00:00Z'); value.setUTCDate(value.getUTCDate() + n); return value.toISOString().slice(0, 10); }
  function parts(instant, zone) {
    if (!formatters.has(zone)) formatters.set(zone, new Intl.DateTimeFormat('en-GB', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }));
    return Object.fromEntries(formatters.get(zone).formatToParts(new Date(instant)).filter(p => p.type !== 'literal').map(p => [p.type, Number(p.value)]));
  }
  const wall = p => Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second || 0);
  function localDate(instant, zone) { const p = parts(instant, zone); return `${p.year}-${String(p.month).padStart(2, '0')}-${String(p.day).padStart(2, '0')}`; }
  function zonedEpoch(date, time, zone) {
    const cacheKey = `${date}|${time}|${zone}`;
    if (instants.has(cacheKey)) return instants.get(cacheKey);
    assert(validDate(date) && /^([01]\d|2[0-3]):[0-5]\d$/.test(time), 'Check the date and time.');
    const target = Date.parse(`${date}T${time}:00Z`), offsets = new Set();
    for (let hour = -36; hour <= 36; hour += 6) {
      const probe = target + hour * 3600000;
      offsets.add(wall(parts(probe, zone)) - probe);
    }
    const candidates = [...offsets].map(offset => target - offset);
    const exact = candidates.filter(candidate => wall(parts(candidate, zone)) === target).sort((a, b) => a - b);
    // Earlier offset in a repeated hour; shift forward by the gap for nonexistent times.
    const shifted = candidates.filter(candidate => wall(parts(candidate, zone)) > target).sort((a, b) => wall(parts(a, zone)) - wall(parts(b, zone)));
    const result = exact[0] ?? shifted[0];
    assert(result !== undefined, 'This time could not be resolved.');
    instants.set(cacheKey, result);
    return result;
  }
  function validateMedicine(m) {
    assert(m && typeof m === 'object' && /^[A-Za-z0-9_-]{1,80}$/.test(m.id), 'Invalid medication identifier.');
    assert(typeof m.name === 'string' && m.name.trim() && m.name.length <= 100, 'Enter a medication name (up to 100 characters).');
    assert(typeof m.strength === 'string' && m.strength.length <= 100 && typeof m.notes === 'string' && m.notes.length <= 1000, 'The label or notes are too long.');
    assert(Object.hasOwn(forms, m.form), 'Choose a medication form.');
    for (const key of ['quantityMilli', 'stockMilli', 'lowStockMilli']) assert(Number.isSafeInteger(m[key]) && m[key] >= (key === 'quantityMilli' ? 1 : 0) && m[key] <= MAX, 'Check the medication quantities.');
    assert(typeof m.times === 'string' && typeof m.weekdays === 'string', 'Invalid schedule.');
    const times = m.times.split(','), weekdays = m.weekdays.split(',');
    assert(times.length > 0 && times.length <= 12 && times.every(t => /^([01]\d|2[0-3]):[0-5]\d$/.test(t)) && new Set(times).size === times.length, 'Use distinct 24-hour times, such as 08:00, 20:00.');
    assert(weekdays.length > 0 && weekdays.every(d => /^[1-7]$/.test(d)) && new Set(weekdays).size === weekdays.length, 'Choose at least one day.');
    assert(validDate(m.startDate) && (m.endDate === null || (validDate(m.endDate) && m.endDate >= m.startDate)), 'Check the start and end dates.');
    assert(['LOCAL_CLOCK', 'HOME_ZONE'].includes(m.timePolicy), 'Choose a travel time policy.');
    parts(Date.now(), m.homeZone);
    assert(Number.isInteger(m.color) && m.color >= 0 && m.color <= 3 && typeof m.archived === 'boolean' && Number.isSafeInteger(m.revision) && m.revision >= 0, 'Invalid medication details.');
    return m;
  }
  function occurrence(m, date, time, zone) {
    const day = new Date(date + 'T12:00:00Z').getUTCDay() || 7;
    if (m.archived || date < m.startDate || (m.endDate && date > m.endDate) || !m.weekdays.split(',').includes(String(day)) || !m.times.split(',').includes(time)) return null;
    return { key: `${m.id}|${date}|${time}`, medication: m, scheduledAt: zonedEpoch(date, time, m.timePolicy === 'HOME_ZONE' ? m.homeZone : zone), localDate: date, localTime: time };
  }
  function resolve(m, key, zone) {
    try { const p = key.split('|'); if (p.length !== 3 || p[0] !== m.id || !validDate(p[1])) return null; return occurrence(m, p[1], p[2], zone); } catch { return null; }
  }
  function dayOccurrences(state, date, zone) {
    const from = zonedEpoch(date, '00:00', zone), until = zonedEpoch(addDays(date, 1), '00:00', zone), result = [];
    for (const m of state.medications) {
      const effective = m.timePolicy === 'HOME_ZONE' ? m.homeZone : zone;
      let cursor = addDays(localDate(from, effective), -1), last = addDays(localDate(until, effective), 1);
      while (cursor <= last) {
        for (const time of m.times.split(',')) { const d = occurrence(m, cursor, time, zone); if (d && d.scheduledAt >= from && d.scheduledAt < until) result.push(d); }
        cursor = addDays(cursor, 1);
      }
    }
    return result.sort((a, b) => a.scheduledAt - b.scheduledAt || a.medication.name.localeCompare(b.medication.name));
  }
  function record(state, key, status, now, zone) {
    assert(['TAKEN', 'SKIPPED'].includes(status), 'Invalid status.');
    const m = state.medications.find(med => med.id === key.split('|')[0]), dose = m && resolve(m, key, zone);
    assert(dose, 'This schedule has changed.');
    const previous = state.records.find(r => r.key === key);
    if (previous?.status === status) return false;
    const newStock = m.stockMilli + (previous?.status === 'TAKEN' ? previous.quantityMilli : 0) - (status === 'TAKEN' ? m.quantityMilli : 0);
    assert(newStock >= 0 && newStock <= MAX, 'Not enough stock. Update inventory in your cabinet.');
    m.stockMilli = newStock; m.revision++;
    state.records = state.records.filter(r => r.key !== key);
    state.records.push({ key, medicationId: m.id, medicationName: m.name, scheduledAt: dose.scheduledAt, recordedAt: now, quantityMilli: m.quantityMilli, status });
    return true;
  }
  function undo(state, key) {
    const r = state.records.find(r => r.key === key); if (!r) return false;
    const m = state.medications.find(m => m.id === r.medicationId); assert(m, 'Medication no longer exists.');
    const stock = m.stockMilli + (r.status === 'TAKEN' ? r.quantityMilli : 0);
    assert(stock <= MAX, 'Update stock before undoing this entry.');
    m.stockMilli = stock; m.revision++; state.records = state.records.filter(r => r.key !== key); return true;
  }
  function refill(state, id, amount) {
    assert(Number.isSafeInteger(amount) && amount > 0, 'Enter a refill greater than zero.');
    const m = state.medications.find(m => m.id === id); assert(m && m.stockMilli + amount <= MAX, 'The resulting stock is too large.');
    m.stockMilli += amount; m.revision++;
  }
  function save(state, medicine) {
    validateMedicine(medicine);
    const i = state.medications.findIndex(m => m.id === medicine.id);
    assert(i < 0 || state.medications[i].revision === medicine.revision, 'Medication changed. Reopen the editor to keep the latest stock.');
    medicine.revision++;
    if (i < 0) state.medications.push(medicine); else state.medications[i] = medicine;
  }
  function seed(now, zone) {
    const today = localDate(now, zone);
    const state = { version: 1, medications: [], records: [], settings: { theme: 'LIGHT', sample: true, discreet: true } };
    [['demo-vitamin', 'Vitamin D3', 'SOFTGEL', '08:00', 30000, 0], ['demo-omega', 'Omega-3', 'CAPSULE', '12:30', 24000, 1], ['demo-magnesium', 'Magnesium', 'TABLET', '20:00', 9000, 2]].forEach(([id, name, form, times, stockMilli, color]) => {
      state.medications.push({ id, name, strength: 'Sample entry', form, quantityMilli: 1000, stockMilli, lowStockMilli: 5000, times, weekdays: '1,2,3,4,5,6,7', startDate: addDays(today, -6), endDate: null, timePolicy: 'LOCAL_CLOCK', homeZone: zone, notes: 'Illustrative routine', color, archived: false, revision: 0 });
    });
    for (let ago = 6; ago >= 1; ago--) state.medications.forEach((m, i) => {
      if (!(ago === 3 && i === 1)) { const d = occurrence(m, addDays(today, -ago), m.times, zone); record(state, d.key, ago === 4 && i === 2 ? 'SKIPPED' : 'TAKEN', d.scheduledAt + 300000, zone); }
    });
    return state;
  }
  function exportBackup(state, now = Date.now()) { return JSON.stringify({ version: 1, exportedAt: new Date(now).toISOString(), medications: state.medications, records: state.records }, null, 2); }
  function importBackup(text) {
    assert(new TextEncoder().encode(text).length <= 2097152, 'Backup must be smaller than 2 MB.');
    const data = JSON.parse(text);
    assert(data.version === 1 && !Number.isNaN(Date.parse(data.exportedAt)), 'This backup version is not supported.');
    assert(Array.isArray(data.medications) && data.medications.length <= 500 && Array.isArray(data.records) && data.records.length <= 20000, 'Backup exceeds the supported size.');
    data.medications.forEach(validateMedicine);
    const ids = new Set(data.medications.map(m => m.id)), keys = new Set();
    assert(ids.size === data.medications.length, 'Duplicate medication identifiers.');
    for (const r of data.records) {
      const p = typeof r.key === 'string' ? r.key.split('|') : [];
      assert(p.length === 3 && p[0] === r.medicationId && ids.has(r.medicationId) && validDate(p[1]) && /^([01]\d|2[0-3]):[0-5]\d$/.test(p[2]), 'Invalid history entry.');
      assert(!keys.has(r.key), 'Duplicate dose identifiers.'); keys.add(r.key);
      assert(typeof r.medicationName === 'string' && r.medicationName.trim() && r.medicationName.length <= 100 && ['TAKEN', 'SKIPPED'].includes(r.status), 'Invalid history entry.');
      assert(Number.isSafeInteger(r.quantityMilli) && r.quantityMilli > 0 && r.quantityMilli <= MAX && [r.scheduledAt, r.recordedAt].every(n => Number.isSafeInteger(n) && n >= 946684800000 && n <= 7258118400000), 'Invalid history quantity or date.');
    }
    return { version: 1, medications: data.medications, records: data.records, settings: { theme: 'LIGHT', sample: data.medications.some(m => m.id.startsWith('demo-')), discreet: true } };
  }
  return { MAX, forms, parseQuantity, quantity, validDate, addDays, localDate, zonedEpoch, validateMedicine, occurrence, resolve, dayOccurrences, record, undo, refill, save, seed, exportBackup, importBackup };
});
