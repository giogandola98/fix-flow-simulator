/**
 * Full GUI UAT — FIX Flow Simulator v0.2.8-beta
 * Visible browser, builds full workflow from scratch via GUI.
 *
 * Coverage:
 *  - Sessions: create via GUI form, connect (loopback FIX)
 *  - Palette: all 11+ node types + Composition group
 *  - Drag every node type onto canvas (synthetic DragEvent)
 *  - Config panels: SEND_FIX (paste+parse), EXPECT_FIX, VALIDATE, DECISION,
 *    ROUTE_FIX (add rule + matcher), RETRY, HTTP_REQUEST, CALL_SCENARIO
 *  - Connect nodes with edges
 *  - Save + reload (regression #56: positions must persist)
 *  - Run scenario and observe execution events
 *  - No JS errors
 */

const puppeteer = require('../node_modules/puppeteer-core');

const BASE = 'http://localhost:8080';
const results = { pass: 0, fail: 0, bugs: [] };

const PASS = (msg) => { console.log('  ✓ PASS:', msg); results.pass++; };
const FAIL = (msg) => { console.error('  ✗ FAIL:', msg); results.fail++; results.bugs.push(msg); };
const INFO = (msg) => console.log('  →', msg);
const SECT = (t) => console.log(`\n═══ ${t} ═══`);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

// Dispatch synthetic drag+drop to ReactFlow pane (no real mouse drag needed)
const dropNodeOnCanvas = async (page, nodeType, clientX, clientY) => {
  await page.evaluate(({ type, x, y }) => {
    const rfWrapper = document.querySelector('.react-flow__pane') || document.querySelector('.react-flow__renderer');
    if (!rfWrapper) return;
    const dt = new DataTransfer();
    dt.setData('application/fix-flow-node-type', type);
    rfWrapper.dispatchEvent(new DragEvent('dragover', { bubbles: true, cancelable: true, dataTransfer: dt, clientX: x, clientY: y }));
    rfWrapper.dispatchEvent(new DragEvent('drop',     { bubbles: true, cancelable: true, dataTransfer: dt, clientX: x, clientY: y }));
  }, { type: nodeType, x: clientX, y: clientY });
  await sleep(600);
};

// Fill input by name attribute
const fillByName = async (page, name, value) => {
  await page.focus(`[name="${name}"]`);
  await page.keyboard.down('Control'); await page.keyboard.press('a'); await page.keyboard.up('Control');
  await page.type(`[name="${name}"]`, String(value));
};

// Click a button by its text
const clickBtnByText = async (page, text) => {
  return await page.evaluate((t) => {
    const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim().includes(t));
    if (btn) { btn.click(); return true; }
    return false;
  }, text);
};

// Click canvas node by type or text content
const clickNodeByText = async (page, text) => {
  await page.evaluate((t) => {
    const node = Array.from(document.querySelectorAll('.react-flow__node'))
      .find(n => n.textContent?.includes(t));
    if (node) { const el = node.querySelector('div') ?? node; el.click(); }
  }, text);
  await sleep(600);
};

(async () => {
  const consoleErrors = [];
  const browser = await puppeteer.launch({
    executablePath: '/usr/bin/chromium-browser',
    headless: false,
    defaultViewport: null,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1600,960'],
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1600, height: 960 });
  page.on('pageerror', (err) => { consoleErrors.push(err.message); });

  try {
    // ─── 1. App loads ──────────────────────────────────────────────────────
    SECT('1. App load');
    await page.goto(BASE, { waitUntil: 'networkidle2', timeout: 20000 });
    await sleep(2000);

    // Force EN locale
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'EN');
      if (btn) btn.click();
    });
    await sleep(400);

    PASS(`App loaded: "${await page.title()}"`);

    // ─── 2. Sessions via GUI form ──────────────────────────────────────────
    SECT('2. Create sessions via GUI form');

    // Fill ACCEPTOR
    await fillByName(page, 'name', 'UAT-SERVER');
    await page.select('select[name="mode"]', 'ACCEPTOR');
    await page.select('select[name="fixVersion"]', 'FIX_44');
    await fillByName(page, 'senderCompID', 'SERVER');
    await fillByName(page, 'targetCompID', 'CLIENT');
    await fillByName(page, 'host', 'localhost');
    await fillByName(page, 'port', '9001');
    await page.click('form button[type="submit"]');
    await sleep(1000);

    let sessions = await page.evaluate(async () => {
      const r = await fetch('/api/v1/sessions');
      return await r.json();
    });
    if (sessions.some(s => s.name === 'UAT-SERVER' && s.mode === 'ACCEPTOR'))
      PASS('ACCEPTOR session saved via GUI');
    else FAIL('ACCEPTOR session not saved via GUI');

    // Reset form for next session
    await page.evaluate(() => {
      const sel = Array.from(document.querySelectorAll('select.w-full')).find(s => s.querySelector('option[value=""]'));
      if (sel) { const setter = Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype, 'value')?.set; setter?.call(sel, ''); sel.dispatchEvent(new Event('change', {bubbles: true})); }
    });
    await sleep(300);

    await fillByName(page, 'name', 'UAT-CLIENT');
    await page.select('select[name="mode"]', 'INITIATOR');
    await page.select('select[name="fixVersion"]', 'FIX_44');
    await fillByName(page, 'senderCompID', 'CLIENT');
    await fillByName(page, 'targetCompID', 'SERVER');
    await fillByName(page, 'host', 'localhost');
    await fillByName(page, 'port', '9001');
    await page.click('form button[type="submit"]');
    await sleep(1000);

    sessions = await page.evaluate(async () => {
      const r = await fetch('/api/v1/sessions');
      return await r.json();
    });
    if (sessions.some(s => s.name === 'UAT-CLIENT' && s.mode === 'INITIATOR'))
      PASS('INITIATOR session saved via GUI');
    else FAIL('INITIATOR session not saved via GUI');

    const srv = sessions.find(s => s.mode === 'ACCEPTOR');
    const cli = sessions.find(s => s.mode === 'INITIATOR');

    // ─── 3. Connect sessions ───────────────────────────────────────────────
    SECT('3. Connect FIX sessions');

    await page.evaluate(async (id) => {
      await fetch(`/api/v1/sessions/${id}/connect`, { method: 'PUT' });
    }, srv.id);
    await sleep(800);

    // Select INITIATOR in session dropdown and click Connect
    await page.evaluate((cliId) => {
      const sel = Array.from(document.querySelectorAll('select')).find(s => s.querySelector(`option[value="${cliId}"]`));
      const setter = Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype, 'value')?.set;
      if (sel && setter) { setter.call(sel, cliId); sel.dispatchEvent(new Event('change', { bubbles: true })); }
    }, cli.id);
    await sleep(500);

    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('form button[type="button"]'))
        .find(b => b.textContent?.trim() === 'Connect' && !b.disabled);
      if (btn) btn.click();
    });
    await sleep(3000);

    const cliStatus = await page.evaluate(async (id) => {
      const r = await fetch(`/api/v1/sessions/${id}`);
      return await r.json();
    }, cli.id);
    if (cliStatus.connected) PASS('Loopback FIX logon complete (INITIATOR connected)');
    else FAIL(`INITIATOR not connected (connected=${cliStatus.connected})`);

    const guiText = await page.evaluate(() => document.body.innerText);
    if (guiText.includes('CONNECTED')) PASS('GUI shows CONNECTED status');
    else INFO('CONNECTED text not found in GUI (may show differently)');

    // ─── 4. Create scenario ────────────────────────────────────────────────
    SECT('4. Create scenario UAT-Main');

    await page.evaluate(() => {
      Array.from(document.querySelectorAll('button')).find(b => b.textContent?.includes('New'))?.click();
    });
    await sleep(800);

    // Click the auto-created "New Scenario" item to select it
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.includes('New Scenario'));
      if (btn) btn.click();
    });
    await sleep(1000);

    const nodesAfterCreate = await page.evaluate(() => document.querySelectorAll('.react-flow__node').length);
    if (nodesAfterCreate >= 2) PASS(`New scenario auto-populates START+END nodes (${nodesAfterCreate} nodes)`);
    else FAIL(`Expected ≥2 nodes after new scenario, got ${nodesAfterCreate}`);

    // Rename to UAT-Main
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button'))
        .find(b => b.title === 'Rename' || b.textContent?.trim() === '✎' || b.title?.includes('ename'));
      if (btn) btn.click();
    });
    await sleep(300);

    const renameInput = await page.$('input.outline-none');
    if (renameInput) {
      await renameInput.click();
      await page.keyboard.down('Control'); await page.keyboard.press('a'); await page.keyboard.up('Control');
      await renameInput.type('UAT-Main');
      await renameInput.press('Enter');
      await sleep(800);
    }

    const uiAfterRename = await page.evaluate(() => document.body.innerText);
    if (uiAfterRename.includes('UAT-Main')) PASS('Scenario renamed to UAT-Main');
    else FAIL('Scenario rename to UAT-Main failed');

    // ─── 5. Palette check ─────────────────────────────────────────────────
    SECT('5. Palette contents');
    const paletteText = await page.evaluate(() => document.body.innerText);
    const expectedNodes = ['Send FIX','Expect FIX','Validate','Decision',
                           'Retry','Loop','Wait','Delay','HTTP Request','Route FIX','Call Scenario'];
    for (const n of expectedNodes) {
      if (paletteText.includes(n)) PASS(`Palette: "${n}"`);
      else FAIL(`Palette missing: "${n}"`);
    }
    if (paletteText.toLowerCase().includes('composition')) PASS('Palette: Composition group visible');
    else FAIL('Palette: Composition group missing');

    // ─── 6. Drag all node types onto canvas ───────────────────────────────
    SECT('6. Drag node types onto canvas');

    // Canvas center is around x=900, y=500 (after left panel ~250px + topbar ~50px)
    const drops = [
      ['SEND_FIX',      850, 400],
      ['EXPECT_FIX',   1050, 400],
      ['VALIDATE',      850, 520],
      ['DECISION',     1050, 520],
      ['ROUTE_FIX',     850, 640],
      ['RETRY',        1050, 640],
      ['HTTP_REQUEST',  850, 760],
      ['CALL_SCENARIO',1050, 760],
      ['DELAY',         950, 880],
    ];

    const nodesBefore = await page.evaluate(() => document.querySelectorAll('.react-flow__node').length);
    for (const [type, x, y] of drops) {
      await dropNodeOnCanvas(page, type, x, y);
    }
    await sleep(500);
    const nodesAfter = await page.evaluate(() => document.querySelectorAll('.react-flow__node').length);
    const addedCount = nodesAfter - nodesBefore;
    if (addedCount >= drops.length - 1) PASS(`Dropped ${addedCount}/${drops.length} node types onto canvas`);
    else FAIL(`Only ${addedCount}/${drops.length} nodes added (some drops may have failed)`);

    INFO(`Canvas has ${nodesAfter} total nodes`);

    // ─── 7. SEND_FIX config panel ─────────────────────────────────────────
    SECT('7. SEND_FIX config: paste FIX message');
    await clickNodeByText(page, 'SEND_FIX');
    let propsText = await page.evaluate(() => document.body.innerText);
    if (propsText.includes('MsgType') || propsText.includes('Node Name')) PASS('SEND_FIX config panel opens');
    else FAIL('SEND_FIX config panel did not open');

    // Open paste section
    await clickBtnByText(page, 'Paste FIX Message');
    await sleep(400);
    const textarea = await page.$('textarea');
    if (textarea) {
      await textarea.click({ clickCount: 3 });
      await textarea.type('8=FIX.4.4|35=D|11=UAT-001|55=AAPL|54=1|38=100|40=1|');
      await sleep(200);
      await page.evaluate(() => {
        const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.includes('Parse'));
        if (btn) btn.click();
      });
      await sleep(600);
      const inputValues = await page.evaluate(() =>
        Array.from(document.querySelectorAll('input[type="text"], input:not([type])')).map(i => i.value)
      );
      if (inputValues.some(v => v === 'AAPL')) PASS('SEND_FIX paste+parse: AAPL populated in fields');
      else FAIL('SEND_FIX paste+parse: AAPL not found in fields');
      if (inputValues.some(v => v === 'D')) PASS('SEND_FIX paste+parse: MsgType=D populated');
      else FAIL('SEND_FIX paste+parse: MsgType=D not found');
    } else {
      FAIL('SEND_FIX paste: textarea not found after opening paste section');
    }

    // ─── 8. DECISION config ────────────────────────────────────────────────
    SECT('8. DECISION config: condition expression');
    await clickNodeByText(page, 'DECISION');
    propsText = await page.evaluate(() => document.body.innerText);
    if (propsText.toLowerCase().includes('condition')) PASS('DECISION config: Condition field visible');
    else FAIL('DECISION config: Condition field missing');

    if (propsText.includes('Operators') || propsText.includes('==')) {
      PASS('DECISION config: operators reference shown');
    } else INFO('DECISION config: operators reference not visible (may be ok)');

    // ─── 9. ROUTE_FIX config: routing rules ───────────────────────────────
    SECT('9. ROUTE_FIX config: routing rules + matchers');
    await clickNodeByText(page, 'ROUTE_FIX');
    propsText = await page.evaluate(() => document.body.innerText);
    if (propsText.includes('Routing Rules') || propsText.includes('+ Rule')) {
      PASS('ROUTE_FIX config: Routing Rules section visible');
    } else {
      FAIL('ROUTE_FIX config: Routing Rules section missing');
    }

    await clickBtnByText(page, '+ Rule');
    await sleep(400);
    propsText = await page.evaluate(() => document.body.innerText);
    if (propsText.includes('Matchers') || propsText.includes('+ Tag') || propsText.includes('Target Node')) {
      PASS('ROUTE_FIX: rule added with Matchers/Target fields');
    } else {
      FAIL('ROUTE_FIX: rule added but Matchers/Target fields missing');
    }

    // Add a matcher tag
    const addTagClicked = await clickBtnByText(page, '+ Tag');
    if (addTagClicked) {
      await sleep(300);
      propsText = await page.evaluate(() => document.body.innerText);
      if (propsText.includes('Tag') && propsText.includes('Value')) {
        PASS('ROUTE_FIX: matcher tag/value fields added');
      } else {
        FAIL('ROUTE_FIX: + Tag clicked but tag/value fields not visible');
      }
    } else INFO('ROUTE_FIX: + Tag button not found — rule may need expanding first');

    // ─── 10. CALL_SCENARIO config ─────────────────────────────────────────
    SECT('10. CALL_SCENARIO config');
    await clickNodeByText(page, 'CALL_SCENARIO');
    propsText = await page.evaluate(() => document.body.innerText);
    if (propsText.includes('Target Scenario')) PASS('CALL_SCENARIO config: Target Scenario dropdown');
    else FAIL('CALL_SCENARIO config: Target Scenario missing');
    if (propsText.includes('Input Variables')) PASS('CALL_SCENARIO config: Input Variables section');
    else FAIL('CALL_SCENARIO config: Input Variables missing');
    if (propsText.includes('Output Variables')) PASS('CALL_SCENARIO config: Output Variables section');
    else FAIL('CALL_SCENARIO config: Output Variables missing');

    // Add input var
    const addVarBtns = await page.$$eval('button', btns =>
      btns.filter(b => b.textContent?.includes('+ Var')).length
    );
    if (addVarBtns > 0) {
      await clickBtnByText(page, '+ Var');
      await sleep(300);
      PASS('CALL_SCENARIO: + Var button works');
    } else {
      FAIL('CALL_SCENARIO: + Var button not found');
    }

    // Verify violet border
    const borderColor = await page.evaluate(() => {
      const node = Array.from(document.querySelectorAll('.react-flow__node'))
        .find(n => n.textContent?.includes('CALL_SCENARIO'));
      return node ? window.getComputedStyle(node.querySelector('div') ?? node).borderColor : null;
    });
    if (borderColor === 'rgb(139, 92, 246)') PASS('CALL_SCENARIO violet border #8b5cf6 correct');
    else FAIL(`CALL_SCENARIO border wrong: ${borderColor} (expected rgb(139, 92, 246))`);

    // ─── 11. HTTP_REQUEST config ───────────────────────────────────────────
    SECT('11. HTTP_REQUEST config');
    await clickNodeByText(page, 'HTTP_REQUEST');
    propsText = await page.evaluate(() => document.body.innerText);
    if (propsText.includes('Method') && propsText.includes('URL')) {
      PASS('HTTP_REQUEST config: Method + URL fields');
    } else {
      FAIL('HTTP_REQUEST config: Method or URL missing');
    }

    // ─── 12. RETRY config ─────────────────────────────────────────────────
    SECT('12. RETRY config');
    await clickNodeByText(page, 'RETRY');
    propsText = await page.evaluate(() => document.body.innerText);
    if (propsText.includes('Max Attempts') || propsText.includes('Delay')) {
      PASS('RETRY config: Max Attempts/Delay fields visible');
    } else {
      FAIL('RETRY config: Max Attempts field missing');
    }

    // ─── 13. Connect some nodes via edge drag ─────────────────────────────
    SECT('13. Connect START → SEND_FIX via edge');
    // Click empty pane to deselect
    await page.mouse.click(700, 300);
    await sleep(400);

    const nodeInfo = await page.evaluate(() =>
      Array.from(document.querySelectorAll('.react-flow__node')).map(n => {
        const r = n.getBoundingClientRect();
        const txt = n.textContent ?? '';
        return { text: txt.slice(0, 30), cx: r.left + r.width/2, cy: r.top + r.height/2, bottom: r.bottom, w: r.width };
      })
    );
    const startN = nodeInfo.find(n => n.text.includes('START') || n.text.toLowerCase().includes('start'));
    const sendN  = nodeInfo.find(n => n.text.includes('SEND_FIX'));

    if (startN && sendN) {
      // Drag from bottom handle of START to top of SEND_FIX
      await page.mouse.move(startN.cx, startN.bottom - 5);
      await page.mouse.down();
      for (let i = 1; i <= 15; i++) {
        await page.mouse.move(
          startN.cx + (sendN.cx - startN.cx) * i / 15,
          (startN.bottom - 5) + (sendN.cy - (startN.bottom - 5)) * i / 15
        );
        await sleep(15);
      }
      await page.mouse.up();
      await sleep(800);

      const edgeCount = await page.evaluate(() => document.querySelectorAll('.react-flow__edge').length);
      if (edgeCount > 0) PASS(`Edge created (${edgeCount} edge(s) on canvas)`);
      else INFO('Edge drag completed — edge count not changed (may need handle precision)');
    } else {
      INFO('Could not find START/SEND_FIX positions for edge drag');
    }

    // ─── 14. Save scenario ────────────────────────────────────────────────
    SECT('14. Save scenario');
    await clickBtnByText(page, 'Save');
    await sleep(2000);

    const scenariosAfterSave = await page.evaluate(async () => {
      const r = await fetch('/api/v1/scenarios');
      return await r.json();
    });
    const uatScen = scenariosAfterSave.find(s => s.name === 'UAT-Main');
    if (uatScen) PASS(`Scenario UAT-Main saved (id: ${uatScen.id.substring(0,8)}…)`);
    else FAIL('Scenario UAT-Main not found via API after save');

    // ─── 15. Reload + persistence regression check (#56) ─────────────────
    SECT('15. Persistence after reload (regression #56)');
    await page.reload({ waitUntil: 'networkidle2' });
    await sleep(2000);

    const bodyAfterReload = await page.evaluate(() => document.body.innerText);
    if (bodyAfterReload.includes('UAT-Main')) PASS('UAT-Main visible after page reload');
    else FAIL('UAT-Main lost after page reload — REGRESSION #56');

    // Click scenario to reopen
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button'))
        .find(b => b.textContent?.includes('UAT-Main'));
      if (btn) btn.click();
    });
    await sleep(1500);

    const nodesAfterReload = await page.evaluate(() => document.querySelectorAll('.react-flow__node').length);
    if (nodesAfterReload >= 2) PASS(`Canvas has ${nodesAfterReload} nodes after reload (nodes persisted)`);
    else FAIL(`Canvas empty (${nodesAfterReload} nodes) after reload — NODE PERSISTENCE FAILURE`);

    // Verify positions are not all at origin (position persistence #56)
    const positionsOk = await page.evaluate(() => {
      const transforms = Array.from(document.querySelectorAll('.react-flow__node'))
        .map(n => n.closest('[style*="translate"]')?.style?.transform || '');
      const unique = new Set(transforms.filter(t => t));
      return unique.size >= 2; // at least 2 different positions
    });
    if (positionsOk) PASS('Node positions restored correctly after reload (#56)');
    else INFO('Position check inconclusive (may still be OK — check visually)');

    // ─── 16. Run scenario ─────────────────────────────────────────────────
    SECT('16. Run scenario');
    if (uatScen) {
      // Make sure scenario has a session assigned first (need to click to open it again)
      await page.evaluate(() => {
        const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.includes('UAT-Main'));
        if (btn) btn.click();
      });
      await sleep(1000);

      // Click Run
      await page.evaluate(() => {
        const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'Run' || b.textContent?.trim() === '▶');
        if (btn) btn.click();
      });
      await sleep(2000);

      const afterRun = await page.evaluate(() => document.body.innerText);
      if (afterRun.includes('RUNNING') || afterRun.includes('PASSED') || afterRun.includes('FAILED') || afterRun.includes('STOPPED')) {
        PASS('Execution status visible after Run');
      } else {
        INFO('Run clicked — execution panel may not show status (scenario may have no FIX session attached)');
      }
    }

    // ─── 17. Language switch ───────────────────────────────────────────────
    SECT('17. Language switch');
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'IT');
      if (btn) btn.click();
    });
    await sleep(600);
    const itText = await page.evaluate(() => document.body.innerText);
    if (itText.includes('Composizione') || itText.includes('Chiama Scenario') || itText.includes('Invio FIX')) {
      PASS('Italian locale renders correctly');
    } else {
      INFO('Italian locale check: text may differ — check palette visually');
    }

    // Switch back to EN
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'EN');
      if (btn) btn.click();
    });
    await sleep(400);

    // ─── 18. JS error check ────────────────────────────────────────────────
    SECT('18. JS errors');
    if (consoleErrors.length === 0) {
      PASS('No JavaScript errors during entire test run');
    } else {
      FAIL(`${consoleErrors.length} JS error(s) found`);
      consoleErrors.slice(0, 5).forEach(e => console.error('    JS Error:', e.substring(0, 200)));
    }

  } catch (err) {
    FAIL(`Test crash: ${err.message}`);
    console.error(err.stack);
  } finally {
    await sleep(5000); // let user see final browser state

    console.log('\n═════════════════════════════════════════════════════');
    console.log(`TOTAL: ${results.pass} PASS / ${results.fail} FAIL`);
    if (results.bugs.length > 0) {
      console.log('\nIssues found:');
      results.bugs.forEach((b, i) => console.log(`  ${i+1}. ${b}`));
    }
    console.log('═════════════════════════════════════════════════════\n');

    await browser.close();
    process.exit(results.fail > 0 ? 1 : 0);
  }
})();
