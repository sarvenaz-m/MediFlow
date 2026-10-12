/* End-to-end checks run against the exact, self-contained distribution file. */
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { chromium } = require('playwright');
const axePath = require.resolve('axe-core/axe.min.js');

async function main() {
  const executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH;
  const browser = await chromium.launch({
    ...(executablePath ? { executablePath } : {}),
    headless: true,
    args: executablePath ? ['--no-sandbox', '--single-process', '--no-zygote', '--disable-dev-shm-usage'] : [],
  });
  const checks = [];
  const violations = [];
  const auditStates = [];
  const errors = [];
  const externalRequests = [];
  const context = await browser.newContext({ viewport: { width: 1440, height: 1100 }, acceptDownloads: true });
  await context.route(/^https?:/, route => {
    externalRequests.push(route.request().url());
    return route.abort();
  });
  const page = await context.newPage();
  page.on('pageerror', error => errors.push(error.message));
  const check = async (name, fn) => { await fn(); checks.push(name); console.log(`PASS ${name}`); };
  const state = () => page.evaluate(() => JSON.parse(localStorage.getItem('mediflow.preview.v1')));
  const click = action => page.locator(`[data-action="${action}"]:visible`).first().click();
  const nav = async view => page.locator(`[data-action="navigate"][data-view="${view}"]:visible`).first().click();
  async function audit(name) {
    auditStates.push(name);
    await page.evaluate(() => Promise.allSettled(document.getAnimations().map(animation => animation.finished)));
    await page.addScriptTag({ path: axePath });
    const result = await page.evaluate(async () => axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21aa'] } }));
    violations.push(...result.violations.map(v => ({ page: name, rule: v.id, impact: v.impact, nodes: v.nodes.map(n => ({ selector: n.target, summary: n.failureSummary })) })));
    console.log(`AUDIT ${name}: ${result.violations.length} violations`);
  }
  try {
    await page.goto(pathToFileURL(path.resolve(__dirname, '../../index.html')).href);
    await check('Offline preview renders a fictional routine', async () => {
      await page.getByRole('heading', { name: 'Your day, in balance.' }).waitFor();
      assert.equal(await page.locator('.dose-card').count(), 3);
      assert.match(await page.locator('.footer').innerText(), /Fictional sample data/);
    });
    await audit('Today · desktop · light');
    await check('Record, undo and reload preserve exact inventory', async () => {
      await page.locator('[data-testid="take-demo-vitamin"]').click();
      let snapshot = await state();
      const medicine = snapshot.medications.find(m => m.id === 'demo-vitamin');
      assert.equal(medicine.stockMilli, 23000);
      await page.reload();
      await page.locator('[data-testid="dose-demo-vitamin"] [data-action="undo"]').click();
      snapshot = await state();
      assert.equal(snapshot.medications.find(m => m.id === 'demo-vitamin').stockMilli, 24000);
    });
    await check('Skip and undo leave stock unchanged', async () => {
      await page.locator('[data-testid="dose-demo-vitamin"] [data-action="dose-options"]').click();
      await click('skip');
      assert.equal((await state()).medications.find(m => m.id === 'demo-vitamin').stockMilli, 24000);
      await page.locator('[data-testid="dose-demo-vitamin"] [data-action="undo"]').click();
    });
    await check('Fractional medication validates and persists', async () => {
      await click('add');
      await page.locator('[data-testid="save-medication"]').click();
      assert.ok((await page.locator('#form-error').innerText()).length > 0);
      await page.locator('#f-name').fill('Half tablet sample');
      await page.locator('#f-quantity').fill('0.5');
      await page.locator('#f-stock').fill('12.5');
      await page.locator('#f-times').fill('09:00, 21:00');
      await audit('Medication editor');
      await page.locator('[data-testid="save-medication"]').click();
      const created = (await state()).medications.find(m => m.name === 'Half tablet sample');
      assert.equal(created.quantityMilli, 500);
      assert.equal(created.stockMilli, 12500);
      assert.equal(created.times, '09:00,21:00');
    });
    await nav('cabinet');
    await audit('Cabinet · light');
    await check('Cabinet search and fractional refill', async () => {
      await page.getByRole('searchbox').fill('Half tablet');
      assert.equal(await page.locator('.medicine-card').count(), 1);
      await click('refill');
      await page.locator('#f-refill').fill('2.5');
      await click('apply-refill');
      assert.equal((await state()).medications.find(m => m.name === 'Half tablet sample').stockMilli, 15000);
      await page.getByRole('searchbox').fill('');
    });
    await check('Backup exports real data and rejects an invalid restore', async () => {
      await nav('settings');
      const downloaded = page.waitForEvent('download');
      await click('export');
      const download = await downloaded;
      const backup = JSON.parse(await fs.readFile(await download.path(), 'utf8'));
      assert.equal(backup.medications.length, 4);
      assert.equal(backup.version, 1);
      const before = JSON.stringify(await state());
      await page.locator('#import-file').setInputFiles({ name: 'invalid.json', mimeType: 'application/json', buffer: Buffer.from('{"version":99}') });
      await page.waitForFunction(() => document.getElementById('toast').textContent.includes('not restored'));
      assert.equal(JSON.stringify(await state()), before);
      await page.locator('#import-file').setInputFiles({ name: 'backup.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(backup)) });
      await page.getByRole('heading', { name: 'Replace your current routine?' }).waitFor();
      await click('restore');
      assert.equal((await state()).medications.length, 4);
    });
    await audit('Settings · light');
    await check('Dark appearance survives reload', async () => {
      await page.locator('[data-theme="DARK"]').click();
      assert.equal(await page.locator('html').getAttribute('data-theme'), 'dark');
      await page.reload();
      assert.equal(await page.locator('html').getAttribute('data-theme'), 'dark');
    });
    await audit('Today · dark');
    await nav('insights');
    await audit('Insights · dark');
    await nav('cabinet');
    await audit('Cabinet · dark');
    await nav('settings');
    await audit('Settings · dark');
    await check('Dialog traps keyboard focus and Escape closes it', async () => {
      await nav('today');
      await click('add');
      await audit('Medication editor · dark');
      await page.getByRole('button', { name: 'Close dialog' }).focus();
      await page.keyboard.press('Shift+Tab');
      assert.equal(await page.evaluate(() => document.activeElement.dataset.testid), 'save-medication');
      await page.keyboard.press('Tab');
      assert.equal(await page.evaluate(() => document.activeElement.getAttribute('aria-label')), 'Close dialog');
      await page.keyboard.press('Escape');
      assert.equal(await page.getByRole('dialog').count(), 0);
    });
    await check('Archive retains existing records', async () => {
      const before = (await state()).records.filter(r => r.medicationId === 'demo-vitamin').length;
      await nav('cabinet');
      await page.locator('[data-testid="medicine-demo-vitamin"] [data-action="medicine-options"]').click();
      await click('archive-confirm');
      await click('archive');
      const snapshot = await state();
      assert.equal(snapshot.medications.find(m => m.id === 'demo-vitamin').archived, true);
      assert.equal(snapshot.records.filter(r => r.medicationId === 'demo-vitamin').length, before);
    });
    await nav('settings');
    await page.locator('[data-theme="LIGHT"]').click();
    await click('reset');
    await click('reset-confirm');
    for (const width of [320, 390, 768, 1024, 1440]) {
      await check(`All destinations fit a ${width}px viewport`, async () => {
        await page.setViewportSize({ width, height: 900 });
        for (const view of ['today', 'cabinet', 'insights', 'settings']) {
          await nav(view);
          assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${view} overflows ${width}px`);
        }
      });
    }
    await page.setViewportSize({ width: 390, height: 844 });
    await nav('today');
    await audit('Today · mobile · light');
    await nav('insights');
    await audit('Insights · mobile · light');
    await check('No remote requests or browser runtime errors', async () => {
      assert.deepEqual(errors, []);
      assert.deepEqual(externalRequests, []);
    });
    const report = { engine: 'Chromium', source: 'index.html via file://', checks, accessibility: { standard: 'Automated WCAG 2.1 A/AA checks with axe-core; not a manual certification', checkedStates: auditStates, violations }, errors, externalRequests };
    await fs.mkdir(path.resolve(__dirname, '../../docs/validation'), { recursive: true });
    await fs.writeFile(path.resolve(__dirname, '../../docs/validation/web-journey.json'), JSON.stringify(report, null, 2) + '\n');
    assert.equal(violations.length, 0, JSON.stringify(violations, null, 2));
    console.log(`${checks.length} browser checks passed; no automated accessibility violations.`);
  } finally { await browser.close(); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
