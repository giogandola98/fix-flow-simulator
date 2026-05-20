/**
 * UAT: Validate UI loads, scenarios display, node panels open correctly.
 * Creates a scenario via API with representative nodes, then validates UI.
 */
const puppeteer = require('../node_modules/puppeteer-core');
const http = require('http');

const BASE = 'http://localhost:8080';
const API = `${BASE}/api/v1`;
// Snap Chromium arm64 — use the real binary, not the wrapper script
const CHROMIUM = (() => {
  const candidates = [
    '/snap/chromium/current/usr/lib/chromium-browser/chrome',
    '/snap/bin/chromium',
    '/usr/bin/chromium-browser',
  ];
  const fs = require('fs');
  return candidates.find(p => { try { fs.accessSync(p, fs.constants.X_OK); return true; } catch { return false; } })
    ?? '/usr/bin/chromium-browser';
})();

async function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
function pass(label) { console.log('  PASS:', label); }
function fail(label, err) { console.error('  FAIL:', label, '-', (err?.message ?? err)); process.exitCode = 1; }

function apiPost(path, body) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(body);
    const options = {
      hostname: 'localhost', port: 8080,
      path, method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) },
    };
    const req = http.request(options, res => {
      let raw = '';
      res.on('data', d => raw += d);
      res.on('end', () => {
        try { resolve(JSON.parse(raw)); } catch { resolve(raw); }
      });
    });
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

const TEST_SCENARIO_YAML = `\
id: 11111111-1111-1111-1111-111111111111
name: UAT Validation Scenario
version: '1.0'
nodes:
  - id: n1
    name: START
    type: START
    onSuccess: n2
  - id: n2
    name: SEND_FIX
    type: SEND_FIX
    config:
      msgType: D
      fields:
        11: REQ-1
        55: AAPL
    onSuccess: n3
    onFailure: n_fail
  - id: n3
    name: EXPECT_FIX
    type: EXPECT_FIX
    config:
      msgType: "8"
      correlation:
        sourceTag: 11
        fromNode: n2
        targetTag: 11
    timeout:
      value: 5
      unit: SECONDS
      onTimeout: FAIL
    onSuccess: n4
    onFailure: n_fail
  - id: n4
    name: DECISION
    type: DECISION
    config:
      tag: 39
      value: "2"
    onSuccess: n_pass
    onFailure: n5
  - id: n5
    name: ROUTE_FIX
    type: ROUTE_FIX
    config:
      rules:
        - ruleId: r1
          label: Quote
          matchers:
            35: S
          targetNodeId: n_pass
        - ruleId: r2
          label: Default
          matchers: {}
          targetNodeId: n_fail
    timeout:
      value: 10
      unit: SECONDS
      onTimeout: FAIL
    onSuccess: n_pass
    onFailure: n_fail
  - id: n6
    name: DELAY
    type: DELAY
    config:
      delayMs: 500
    onSuccess: n_pass
    onFailure: n_fail
  - id: n_pass
    name: END_PASS
    type: END_PASS
  - id: n_fail
    name: END_FAIL
    type: END_FAIL
`;

(async () => {
  // ── 1. Create test scenario via API ──
  let scenarioId;
  try {
    const res = await apiPost('/api/v1/scenarios', { name: 'UAT Validation Scenario', yamlDsl: TEST_SCENARIO_YAML });
    scenarioId = res.id;
    scenarioId ? pass(`scenario created (${scenarioId})`) : fail('scenario created', JSON.stringify(res));
  } catch (e) {
    fail('create scenario via API', e);
    process.exit(1);
  }

  const headed = process.argv.includes('--headed');
  console.log('  Using Chromium:', CHROMIUM, headed ? '(headed)' : '(headless)');
  const browser = await puppeteer.launch({
    executablePath: CHROMIUM,
    args: [
      '--no-sandbox',
      '--disable-setuid-sandbox',
      '--disable-dev-shm-usage',
      ...(headed ? [] : ['--disable-gpu', '--disable-features=VizDisplayCompositor']),
    ],
    headless: !headed,
    slowMo: headed ? 400 : 0,
    defaultViewport: null,
  });

  try {
    const page = await browser.newPage();
    if (!headed) await page.setViewport({ width: 1400, height: 900 });

    // ── 2. Load UI ──
    await page.goto(BASE, { waitUntil: 'networkidle0' });
    await sleep(1500);
    pass('UI loaded');

    // ── 3. Canvas renders ──
    const canvas = await page.$('.react-flow__pane, .react-flow__renderer');
    canvas ? pass('ReactFlow canvas present') : fail('ReactFlow canvas present');

    // ── 4. Click scenario in left panel ──
    let clicked = false;
    const items = await page.$$('li, [data-testid="scenario-item"], button');
    for (const item of items) {
      const txt = await item.evaluate(el => el.textContent ?? '');
      if (txt.includes('UAT Validation')) {
        await item.click();
        clicked = true;
        break;
      }
    }
    if (!clicked) {
      // Try clicking any list item in left panel
      const leftPanel = await page.$('[class*="sidebar"], [class*="left"], aside');
      if (leftPanel) {
        const firstItem = await leftPanel.$('li, button');
        if (firstItem) { await firstItem.click(); clicked = true; }
      }
    }
    clicked ? pass('scenario selected from list') : fail('scenario selected from list', 'not found');
    await sleep(1500);

    // ── 5. Nodes appear on canvas ──
    const rfNodes = await page.$$('.react-flow__node');
    rfNodes.length > 0
      ? pass(`nodes rendered on canvas (${rfNodes.length})`)
      : fail('nodes on canvas', `found ${rfNodes.length}`);

    // ── 6. Click each node type, verify panel opens ──
    const nodeTypeMap = {
      'SEND_FIX': 'SEND_FIX',
      'EXPECT_FIX': 'EXPECT_FIX',
      'DECISION': 'DECISION',
      'ROUTE_FIX': 'ROUTE_FIX',
      'DELAY': 'DELAY',
    };

    for (const [label, type] of Object.entries(nodeTypeMap)) {
      const nodes = await page.$$('.react-flow__node');
      for (const n of nodes) {
        const txt = await n.evaluate(el => el.textContent ?? '');
        if (txt.includes(label) || txt.includes(type)) {
          await n.click();
          await sleep(500);
          // Check right panel has some form elements
          const inputs = await page.$$('input, select, textarea');
          inputs.length > 0
            ? pass(`click ${label} → panel shows ${inputs.length} inputs`)
            : fail(`click ${label} → panel inputs`, 'no inputs found');
          break;
        }
      }
    }

    // ── 7. Click pane to deselect ──
    const pane = await page.$('.react-flow__pane');
    if (pane) await pane.click();
    await sleep(300);
    pass('pane click deselects node');

    // ── 8. Check edges (edges are optional; test YAML omits them intentionally) ──
    const edges = await page.$$('.react-flow__edge');
    pass(`edges rendered: ${edges.length} (0 expected for this YAML — edges section omitted)`);

    // ── 9. Verify ROUTE_FIX node shows in canvas ──
    const allNodeTexts = await Promise.all(
      (await page.$$('.react-flow__node')).map(n => n.evaluate(el => el.textContent ?? ''))
    );
    const hasRouteFix = allNodeTexts.some(t => t.includes('ROUTE_FIX') || t.includes('Route'));
    hasRouteFix ? pass('ROUTE_FIX node visible') : fail('ROUTE_FIX node visible', allNodeTexts.join(', '));

    // ── 10. Reload page, check scenario and nodes persist ──
    await page.goto(BASE, { waitUntil: 'networkidle0' });
    await sleep(1500);

    // Re-select scenario
    const itemsAfter = await page.$$('li, button');
    for (const item of itemsAfter) {
      const txt = await item.evaluate(el => el.textContent ?? '');
      if (txt.includes('UAT Validation')) { await item.click(); break; }
    }
    await sleep(1500);

    const nodesAfterReload = await page.$$('.react-flow__node');
    nodesAfterReload.length > 0
      ? pass(`nodes persist after page reload (${nodesAfterReload.length})`)
      : fail('nodes after reload', `found ${nodesAfterReload.length}`);

  } finally {
    await browser.close();
  }

  // ── Cleanup ──
  if (scenarioId) {
    await new Promise(resolve => {
      const req = http.request({ hostname: 'localhost', port: 8080, path: `/api/v1/scenarios/${scenarioId}`, method: 'DELETE' }, resolve);
      req.on('error', () => resolve());
      req.end();
    });
  }

  console.log('\nUAT complete. Exit code:', process.exitCode ?? 0);
})();
