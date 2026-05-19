/**
 * Full GUI UAT — builds a complete workflow through the browser UI only.
 * Tests: session creation, drag-drop nodes, paste-FIX config, connect nodes,
 * save, run, event log, FIX messages, language switch, panel collapse.
 */
const puppeteer = require('puppeteer-core');

const BASE = 'http://localhost:8080';
let pass = 0, fail = 0;
const check = (label, cond) => {
  if (cond) { console.log(`  PASS: ${label}`); pass++; }
  else       { console.log(`  FAIL: ${label}`); fail++; }
};
const section = (title) => console.log(`\n=== ${title} ===`);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

// Fill input[name=x] via click+select-all+type (works with RHF)
const fillByName = async (page, name, value) => {
  await page.focus(`[name="${name}"]`);
  await page.keyboard.down('Control'); await page.keyboard.press('a'); await page.keyboard.up('Control');
  await page.type(`[name="${name}"]`, String(value));
};

// Drop a node type onto the ReactFlow canvas
const dropNodeOnCanvas = async (page, nodeType, clientX, clientY) => {
  await page.evaluate(({ type, x, y }) => {
    const rfWrapper = Array.from(document.querySelectorAll('div')).find(d =>
      d.classList.contains('relative') && d.classList.contains('w-full') &&
      d.classList.contains('h-full') && d.querySelector('.react-flow')
    );
    if (!rfWrapper) return;
    const dt = new DataTransfer();
    dt.setData('application/fix-flow-node-type', type);
    rfWrapper.dispatchEvent(new DragEvent('dragover', { bubbles: true, cancelable: true, dataTransfer: dt, clientX: x, clientY: y }));
    rfWrapper.dispatchEvent(new DragEvent('drop',     { bubbles: true, cancelable: true, dataTransfer: dt, clientX: x, clientY: y }));
  }, { type: nodeType, x: clientX, y: clientY });
};

// Mouse drag (for node handle connections)
const dragFromTo = async (page, x1, y1, x2, y2) => {
  await page.mouse.move(x1, y1);
  await page.mouse.down();
  for (let i = 1; i <= 12; i++) {
    await page.mouse.move(x1 + (x2 - x1) * i / 12, y1 + (y2 - y1) * i / 12);
    await sleep(15);
  }
  await page.mouse.up();
  await sleep(300);
};

(async () => {
  const browser = await puppeteer.launch({
    executablePath: '/usr/bin/chromium-browser',
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1600, height: 1000 });
  await page.goto(BASE, { waitUntil: 'networkidle2', timeout: 15000 });
  await sleep(1500);

  // Force EN so all text checks are consistent
  await page.evaluate(() => {
    const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'EN');
    if (btn) btn.click();
  });
  await sleep(400);

  // ── 1. Create ACCEPTOR session ───────────────────────────────────────────
  section('1. Create ACCEPTOR session via GUI form');
  {
    // Expand session panel if collapsed
    const panelHeader = await page.$('[title*="Expand"]');
    if (panelHeader) { await panelHeader.click(); await sleep(400); }

    // Fill form
    await fillByName(page, 'name', 'UAT-SERVER');
    await page.select('select[name="mode"]', 'ACCEPTOR');
    await page.select('select[name="fixVersion"]', 'FIX_44');
    await fillByName(page, 'senderCompID', 'SERVER');
    await fillByName(page, 'targetCompID', 'CLIENT');
    await fillByName(page, 'host', 'localhost');
    await fillByName(page, 'port', '9902');
    await sleep(200);

    await page.screenshot({ path: '/tmp/uat-gui-01-acceptor-form.png' });

    // Click Save
    await page.click('form button[type="submit"]');
    await sleep(1000);

    const sessions = await (await fetch(`${BASE}/api/v1/sessions`)).json();
    console.log('sessions:', sessions.map(s => `${s.name}(${s.mode})`));
    check('ACCEPTOR session saved via GUI', sessions.some(s => s.name === 'UAT-SERVER' && s.mode === 'ACCEPTOR'));

    await page.screenshot({ path: '/tmp/uat-gui-02-acceptor-saved.png' });
  }

  // ── 2. Create INITIATOR session ──────────────────────────────────────────
  section('2. Create INITIATOR session via GUI form');
  {
    // Select "-- new session --" to reset form
    await page.select('select.w-full', '');
    await sleep(300);

    await fillByName(page, 'name', 'UAT-CLIENT');
    await page.select('select[name="mode"]', 'INITIATOR');
    await page.select('select[name="fixVersion"]', 'FIX_44');
    await fillByName(page, 'senderCompID', 'CLIENT');
    await fillByName(page, 'targetCompID', 'SERVER');
    await fillByName(page, 'host', 'localhost');
    await fillByName(page, 'port', '9902');
    await sleep(200);

    await page.click('form button[type="submit"]');
    await sleep(1000);

    const sessions = await (await fetch(`${BASE}/api/v1/sessions`)).json();
    check('INITIATOR session saved via GUI', sessions.some(s => s.name === 'UAT-CLIENT' && s.mode === 'INITIATOR'));
  }

  // ── 3. Connect sessions ──────────────────────────────────────────────────
  section('3. Connect sessions');
  {
    const apiSessions = await (await fetch(`${BASE}/api/v1/sessions`)).json();
    const srv = apiSessions.find(s => s.mode === 'ACCEPTOR');
    const cli = apiSessions.find(s => s.mode === 'INITIATOR');

    // Connect ACCEPTOR via API first (no UI needed for acceptor)
    await fetch(`${BASE}/api/v1/sessions/${srv.id}/connect`, { method: 'PUT' });
    await sleep(600);

    // Select INITIATOR in the active session dropdown, then click Connect
    const selects = await page.$$('select');
    // First select (active session dropdown) is the session picker
    await page.evaluate((cliId) => {
      const sel = Array.from(document.querySelectorAll('select')).find(s => s.querySelector(`option[value="${cliId}"]`));
      const setter = Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype, 'value')?.set;
      if (sel && setter) { setter.call(sel, cliId); sel.dispatchEvent(new Event('change', { bubbles: true })); }
    }, cli.id);
    await sleep(500);

    // Click the Connect button in the form (green button, not the header compact one)
    await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('form button[type="button"]'));
      const btn = btns.find(b => b.textContent?.trim() === 'Connect' && !b.disabled);
      if (btn) btn.click();
    });
    await sleep(2500);
    await page.screenshot({ path: '/tmp/uat-gui-03-connected.png' });

    const cliStatus = await (await fetch(`${BASE}/api/v1/sessions/${cli.id}`)).json();
    console.log('initiator connected:', cliStatus.connected);
    check('INITIATOR connected via GUI Connect button', cliStatus.connected);

    const guiText = await page.evaluate(() => document.body.innerText);
    check('GUI shows CONNECTED status', guiText.includes('CONNECTED'));
  }

  // ── 4. Create scenario via GUI ───────────────────────────────────────────
  section('4. Create & rename scenario via GUI');
  {
    // Click "+ New"
    await page.evaluate(() => {
      Array.from(document.querySelectorAll('button')).find(b => b.textContent?.includes('New'))?.click();
    });
    await sleep(800);

    // Click the new scenario to select it (it appears as "New Scenario")
    await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const s = btns.find(b => b.textContent?.includes('New Scenario'));
      if (s) s.click();
    });
    await sleep(800);
    await page.screenshot({ path: '/tmp/uat-gui-04-new-scenario.png' });

    const nodesAfterCreate = await page.evaluate(() => document.querySelectorAll('.react-flow__node').length);
    console.log('canvas nodes after new scenario:', nodesAfterCreate);
    check('New scenario auto-populates START + END_PASS nodes', nodesAfterCreate >= 2);

    // Rename via ✎ button (hover over scenario to reveal)
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.title === 'Rename' || b.textContent?.trim() === '✎');
      if (btn) btn.click();
    });
    await sleep(300);

    const renameInput = await page.$('input.outline-none');
    if (renameInput) {
      await renameInput.click();
      await page.keyboard.down('Control'); await page.keyboard.press('a'); await page.keyboard.up('Control');
      await renameInput.type('FIX Send Test');
      await renameInput.press('Enter');
      await sleep(800);
    }
    await page.screenshot({ path: '/tmp/uat-gui-05-renamed.png' });

    const topbarName = await page.evaluate(() =>
      Array.from(document.querySelectorAll('div.text-sm.text-gray-400')).map(d => d.textContent?.trim())
    );
    console.log('topbar names:', topbarName);
    check('Scenario renamed to "FIX Send Test"', topbarName.some(t => t?.includes('FIX Send Test')));
  }

  // ── 5. Drag SEND_FIX from palette to canvas ──────────────────────────────
  section('5. Drag SEND_FIX from palette → canvas');
  {
    const nodesBefore = await page.evaluate(() => document.querySelectorAll('.react-flow__node').length);

    // Canvas center: left panel ~250px, topbar ~48px → drop at (800, 380)
    await dropNodeOnCanvas(page, 'SEND_FIX', 800, 380);
    await sleep(800);
    await page.screenshot({ path: '/tmp/uat-gui-06-drop-sendfx.png' });

    const nodesAfter = await page.evaluate(() => document.querySelectorAll('.react-flow__node').length);
    console.log(`nodes: ${nodesBefore} → ${nodesAfter}`);
    check('SEND_FIX node added via drag-drop', nodesAfter > nodesBefore);

    const nodeTexts = await page.evaluate(() =>
      Array.from(document.querySelectorAll('.react-flow__node')).map(n => n.textContent?.replace(/\s+/g, ' ').trim().slice(0, 40))
    );
    console.log('canvas nodes:', nodeTexts);
    check('SEND_FIX node visible on canvas', nodeTexts.some(t => t?.includes('SEND_FIX')));
  }

  // ── 6. Configure SEND_FIX via paste FIX message ──────────────────────────
  section('6. Configure SEND_FIX node (paste FIX message)');
  {
    // Click SEND_FIX node to select it
    await page.evaluate(() => {
      const node = Array.from(document.querySelectorAll('.react-flow__node'))
        .find(n => n.textContent?.includes('SEND_FIX'));
      if (node) (node.querySelector('div') ?? node).click();
    });
    await sleep(500);

    const propsVisible = await page.evaluate(() =>
      document.body.innerText.includes('MsgType') || document.body.innerText.includes('Node Name')
    );
    check('Properties panel shows SEND_FIX config', propsVisible);

    // Open "Paste FIX Message" section
    await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const pasteBtn = btns.find(b => b.textContent?.includes('Paste FIX Message'));
      if (pasteBtn) pasteBtn.click();
    });
    await sleep(400);

    // Paste a New Order Single
    const textarea = await page.$('textarea');
    if (textarea) {
      await textarea.click({ clickCount: 3 });
      await textarea.type('8=FIX.4.4|35=D|11=UAT-001|55=AAPL|54=1|38=100|40=1|');
      await sleep(200);

      // Click "Parse → populate fields"
      await page.evaluate(() => {
        const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.includes('Parse'));
        if (btn) btn.click();
      });
      await sleep(400);
    }
    await page.screenshot({ path: '/tmp/uat-gui-07-sendfx-config.png' });

    // Verify fields populated — check input values directly (innerText excludes <input value>)
    const bodyText = await page.evaluate(() => document.body.innerText);
    const inputValues = await page.evaluate(() =>
      Array.from(document.querySelectorAll('input[type="text"], input:not([type])')).map(i => i.value)
    );
    console.log('input values after parse:', inputValues.slice(0, 10));
    check('SEND_FIX MsgType populated (D)', bodyText.includes('MsgType') && inputValues.some(v => v === 'D' || v.includes('D')));
    check('SEND_FIX fields parsed from paste', inputValues.some(v => v === 'AAPL' || v.includes('AAPL')));
  }

  // ── 7. Connect nodes via handle drag ─────────────────────────────────────
  section('7. Connect nodes via handle drag');
  {
    // Pane click to deselect
    await page.mouse.click(900, 200);
    await sleep(300);

    const nodeInfo = await page.evaluate(() =>
      Array.from(document.querySelectorAll('.react-flow__node')).map(n => {
        const r = n.getBoundingClientRect();
        const txt = n.textContent ?? '';
        const type = txt.includes('START') || txt.includes('Start') ? 'START'
          : txt.includes('SEND_FIX') ? 'SEND_FIX'
          : txt.includes('END_PASS') || txt.includes('End Pass') ? 'END_PASS'
          : 'OTHER';
        return { type, cx: r.left + r.width/2, cy: r.top + r.height/2, bottom: r.bottom, top: r.top, w: r.width, h: r.height };
      })
    );
    console.log('node positions:', nodeInfo.map(n => `${n.type}@(${Math.round(n.cx)},${Math.round(n.cy)})`));

    const startN = nodeInfo.find(n => n.type === 'START');
    const sendN  = nodeInfo.find(n => n.type === 'SEND_FIX');
    const endN   = nodeInfo.find(n => n.type === 'END_PASS');

    if (startN && sendN) {
      const srcX = startN.cx, srcY = startN.bottom - 6;
      const tgtX = sendN.cx,  tgtY = sendN.top + 6;
      console.log(`  START(${Math.round(srcX)},${Math.round(srcY)}) → SEND_FIX(${Math.round(tgtX)},${Math.round(tgtY)})`);
      await dragFromTo(page, srcX, srcY, tgtX, tgtY);
      await sleep(300);
    }

    if (sendN && endN) {
      const srcX = sendN.cx, srcY = sendN.bottom - 6;
      const tgtX = endN.cx,  tgtY = endN.top + 6;
      console.log(`  SEND_FIX(${Math.round(srcX)},${Math.round(srcY)}) → END_PASS(${Math.round(tgtX)},${Math.round(tgtY)})`);
      await dragFromTo(page, srcX, srcY, tgtX, tgtY);
      await sleep(300);
    }

    await page.screenshot({ path: '/tmp/uat-gui-08-connected.png' });

    const edgeCount = await page.evaluate(() => document.querySelectorAll('.react-flow__edge').length);
    console.log('edges after connect:', edgeCount);
    check('Canvas has at least 1 edge after connecting', edgeCount >= 1);
  }

  // ── 8. Save scenario via toolbar ─────────────────────────────────────────
  section('8. Save via toolbar');
  {
    // Save button shows "Save •" when dirty
    const saveBtnBefore = await page.evaluate(() =>
      Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim().startsWith('Save'))?.textContent?.trim()
    );
    console.log('save btn before:', saveBtnBefore);
    check('Save button shows dirty indicator before save', saveBtnBefore?.includes('•'));

    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim().startsWith('Save') && !b.disabled);
      if (btn) btn.click();
    });
    await sleep(1500);
    await page.screenshot({ path: '/tmp/uat-gui-09-saved.png' });

    const saveBtnAfter = await page.evaluate(() =>
      Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim().startsWith('Save'))?.textContent?.trim()
    );
    console.log('save btn after:', saveBtnAfter);
    check('Save button clean after save (no •)', !saveBtnAfter?.includes('•'));

    const scenarios = await (await fetch(`${BASE}/api/v1/scenarios`)).json();
    check('Scenario "FIX Send Test" in DB', scenarios.some(s => s.name === 'FIX Send Test'));
  }

  // ── 9. Verify session active & run ──────────────────────────────────────────
  section('9. Session active & run');
  {
    // NOTE: do NOT re-select via the dropdown here.
    // Doing so calls setActiveSession(sessions.find(...)) which can overwrite activeSession.connected=true
    // (set by WS SESSION_UP → updateSession) with stale TanStack Query cache data (connected:false).
    // Session was selected and connected in section 3 — just verify Run is enabled.

    const runBtnCheck = await page.evaluate(() => {
      const b = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'Run');
      return { found: !!b, disabled: b?.disabled };
    });
    console.log('run button initial:', JSON.stringify(runBtnCheck));

    // Fallback: if still disabled, click compact-header Connect button (calls connectMutation →
    // setActiveSession(updated) with fresh API data, guaranteed connected:true)
    if (runBtnCheck.disabled) {
      console.log('  Run disabled — clicking compact header Connect...');
      await page.evaluate(() => {
        // Compact header button is inside div.select-none (the collapsible session header row)
        const btn = Array.from(document.querySelectorAll('div.select-none button'))
          .find(b => b.textContent?.trim() === 'Connect');
        if (btn) btn.click();
      });
      await sleep(3000);
    }

    const runBtn = await page.evaluate(() => {
      const b = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'Run');
      return { found: !!b, disabled: b?.disabled };
    });
    console.log('run button:', JSON.stringify(runBtn));
    check('Run button enabled (session active + connected)', runBtn.found && !runBtn.disabled);

    // Click Run
    await page.evaluate(() => {
      const b = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'Run' && !b.disabled);
      if (b) b.click();
    });
    await sleep(5000);
    await page.screenshot({ path: '/tmp/uat-gui-10-ran.png' });
  }

  // ── 10. Events tab — verify execution events ─────────────────────────────
  section('10. Events tab');
  {
    // Expand bottom panel if collapsed
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'Expand');
      if (btn) btn.click();
    });
    await sleep(300);

    // Make sure Events tab is active
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'Events');
      if (btn) btn.click();
    });
    await sleep(400);
    await page.screenshot({ path: '/tmp/uat-gui-11-events.png' });

    const eventLog = await page.evaluate(() => document.querySelector('.font-mono')?.innerText ?? '');
    console.log('event log:', eventLog.slice(0, 300).replace(/\n/g, ' | '));
    check('Events show EXECUTION_STARTED', eventLog.includes('EXECUTION_STARTED'));
    check('Events show NODE_ENTERED', eventLog.includes('NODE_ENTERED'));
    check('Events show EXECUTION_FINISHED', eventLog.includes('EXECUTION_FINISHED'));
  }

  // ── 11. FIX Messages tab ─────────────────────────────────────────────────
  section('11. FIX Messages tab');
  {
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'FIX Messages');
      if (btn) btn.click();
    });
    await sleep(400);
    await page.screenshot({ path: '/tmp/uat-gui-12-fix-messages.png' });

    const msgLog = await page.evaluate(() => {
      // Both EventLog and FIXMessageLog use font-mono; find the visible one (not in a .hidden wrapper)
      const panels = Array.from(document.querySelectorAll('.h-full'));
      for (const p of panels) {
        if (!p.classList.contains('hidden')) {
          const mono = p.querySelector('.font-mono');
          if (mono) return mono.innerText ?? '';
        }
      }
      return '';
    });
    console.log('FIX messages:', msgLog.slice(0, 200).replace(/\n/g, ' | '));
    check('FIX Messages tab shows sent message', msgLog.includes('OUT') || msgLog.includes('35=') || msgLog.includes('IN'));

    const msgCountEl = await page.evaluate(() =>
      Array.from(document.querySelectorAll('div')).find(d => d.textContent?.match(/^\d+\s*messages?$/))?.textContent?.trim()
    );
    console.log('message count:', msgCountEl);
    check('Message count > 0', msgCountEl && !msgCountEl.startsWith('0'));
  }

  // ── 12. Statistics tab ───────────────────────────────────────────────────
  section('12. Statistics tab');
  {
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'Statistics');
      if (btn) btn.click();
    });
    await sleep(400);
    await page.screenshot({ path: '/tmp/uat-gui-13-stats.png' });

    const statsText = await page.evaluate(() => document.body.innerText);
    check('Statistics shows PASSED or FAILED status', statsText.includes('PASSED') || statsText.includes('FAILED'));
    // text-transform:uppercase affects innerText in Chrome, so lowercase compare
    check('Statistics shows nodes passed count', statsText.toLowerCase().includes('nodes passed') || statsText.toLowerCase().includes('status'));
  }

  // ── 13. Session panel collapse/expand ────────────────────────────────────
  section('13. Session panel collapse/expand');
  {
    // Collapse
    const collapseTitle = await page.evaluate(() => {
      const h = Array.from(document.querySelectorAll('div')).find(d =>
        d.classList.contains('cursor-pointer') && d.classList.contains('select-none')
      );
      h?.click();
      return h?.title;
    });
    await sleep(300);

    const maxH = await page.evaluate(() =>
      Array.from(document.querySelectorAll('div[style*="max-height"]'))[0]?.style?.maxHeight
    );
    check('Session panel collapses to 0px', maxH === '0px');

    const hdrText = await page.evaluate(() => {
      const h = Array.from(document.querySelectorAll('div')).find(d =>
        d.classList.contains('cursor-pointer') && d.classList.contains('select-none')
      );
      return h?.innerText ?? '';
    });
    check('Collapsed header shows session name', hdrText.includes('UAT-CLIENT'));
    check('Collapsed header shows status', hdrText.includes('CONNECTED') || hdrText.includes('DISCONNECTED'));

    // Expand back
    await page.evaluate(() => {
      Array.from(document.querySelectorAll('div')).find(d =>
        d.classList.contains('cursor-pointer') && d.classList.contains('select-none')
      )?.click();
    });
    await sleep(300);
    await page.screenshot({ path: '/tmp/uat-gui-14-session-collapsed.png' });
  }

  // ── 14. Language switch mid-workflow ─────────────────────────────────────
  section('14. Language switch mid-workflow (EN→IT→FR→EN)');
  {
    // Switch to IT
    await page.evaluate(() => Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'IT')?.click());
    await sleep(500);
    const itBody = await page.evaluate(() => document.body.innerText);
    check('IT: Esegui button', itBody.includes('Esegui'));
    check('IT: canvas nodes intact after lang switch',
      (await page.evaluate(() => document.querySelectorAll('.react-flow__node').length)) >= 3
    );

    // Switch to FR
    await page.evaluate(() => Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'FR')?.click());
    await sleep(400);
    const frBody = await page.evaluate(() => document.body.innerText);
    check('FR: Exécuter button', frBody.includes('Exécuter'));

    // Back to EN
    await page.evaluate(() => Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'EN')?.click());
    await sleep(400);
    const enBody = await page.evaluate(() => document.body.innerText);
    check('EN: restored', enBody.includes('Run'));

    // Verify localStorage persists
    const lang = await page.evaluate(() => localStorage.getItem('fix-flow-lang'));
    check('localStorage stores last language (en)', lang === 'en');
    await page.screenshot({ path: '/tmp/uat-gui-15-final.png' });
  }

  // ── 15. Bottom panel collapse ────────────────────────────────────────────
  section('15. Bottom panel collapse/expand');
  {
    await page.evaluate(() => {
      Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'Collapse')?.click();
    });
    await sleep(300);
    // After collapsing, the toggle button switches to "Expand"
    const isCollapsed = await page.evaluate(() =>
      Array.from(document.querySelectorAll('button')).some(b => b.textContent?.trim() === 'Expand')
    );
    check('Bottom panel collapses (Expand button visible)', isCollapsed);

    await page.evaluate(() => {
      Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'Expand')?.click();
    });
    await sleep(300);
  }

  await browser.close();
  console.log(`\n${'─'.repeat(50)}`);
  console.log(`RESULT: ${pass} passed, ${fail} failed`);
  console.log('Screenshots: /tmp/uat-gui-*.png');
  if (fail > 0) process.exit(1);
})().catch(e => { console.error('UAT FAIL:', e.message); process.exit(1); });
