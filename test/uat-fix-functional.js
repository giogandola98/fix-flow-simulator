/**
 * UAT: Comprehensive FIX functional test.
 * Tests: ROUTE_FIX (quote + execreport branch), DECISION, LOOP, RETRY,
 *        all variable placeholders, cross-message {{node:id:tagN}} reads.
 *
 * Requires: app running on :8080.
 * Run (headed): DISPLAY=:0 node test/uat-fix-functional.js
 * Run (headless): node test/uat-fix-functional.js --headless
 */
const puppeteer = require('../node_modules/puppeteer-core');
const http = require('http');
const fs = require('fs');

const BASE = 'http://localhost:8080';

const CHROMIUM = (() => {
  const candidates = [
    '/snap/chromium/current/usr/lib/chromium-browser/chrome',
    '/snap/bin/chromium',
    '/usr/bin/chromium-browser',
  ];
  return candidates.find(p => { try { fs.accessSync(p, fs.constants.X_OK); return true; } catch { return false; } })
    ?? '/usr/bin/chromium-browser';
})();

// ── Logging ──
async function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
function pass(label) { console.log('  \x1b[32mPASS\x1b[0m:', label); }
function fail(label, err) { console.error('  \x1b[31mFAIL\x1b[0m:', label, '-', (err?.message ?? String(err))); process.exitCode = 1; }
function info(label) { console.log('  \x1b[36mINFO\x1b[0m:', label); }

// ── HTTP helpers ──
function httpReq(method, path, body) {
  return new Promise((resolve, reject) => {
    const data = body != null ? JSON.stringify(body) : null;
    const req = http.request({
      hostname: 'localhost', port: 8080, path, method,
      headers: {
        'Content-Type': 'application/json',
        ...(data ? { 'Content-Length': Buffer.byteLength(data) } : {}),
      },
    }, res => {
      let raw = '';
      res.on('data', d => raw += d);
      res.on('end', () => {
        if (res.statusCode >= 400 && res.statusCode !== 404) {
          reject(new Error(`HTTP ${res.statusCode} ${method} ${path}: ${raw}`));
          return;
        }
        try { resolve(JSON.parse(raw)); } catch { resolve(raw); }
      });
    });
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}
const apiGet    = p      => httpReq('GET', p, null);
const apiPost   = (p, b) => httpReq('POST', p, b);
const apiPut    = (p, b) => httpReq('PUT', p, b ?? {});
const apiDelete = p      => httpReq('DELETE', p, null).catch(() => null);

// ── Execution polling ──
async function pollExecution(execId, timeoutMs = 35000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const res = await apiGet(`/api/v1/executions/${execId}`);
    if (['PASSED', 'FAILED', 'STOPPED'].includes(res.status)) return res.status;
    await sleep(300);
  }
  return 'TIMEOUT';
}

async function runPair(respId, mainId, label) {
  const [rSt, mSt] = await Promise.all([
    pollExecution(respId),
    pollExecution(mainId),
  ]);
  mSt === 'PASSED' ? pass(`${label} main → PASSED`) : fail(`${label} main → ${mSt}`);
  rSt === 'PASSED' ? pass(`${label} responder → PASSED`) : fail(`${label} responder → ${rSt}`);
  return { main: mSt, resp: rSt };
}

const exec = async (scenarioId, sessionId) => {
  const res = await apiPost(`/api/v1/scenarios/${scenarioId}/execute`, { sessionId });
  return res.executionId;
};

// ── Fixed scenario IDs (deterministic cleanup) ──
const ID = {
  routeMainA: '33333333-0000-0000-0000-000000000001',
  routeRespA: '33333333-0000-0000-0000-000000000002',
  routeMainB: '33333333-0000-0000-0000-000000000003',
  routeRespB: '33333333-0000-0000-0000-000000000004',
  decMain:    '33333333-0000-0000-0000-000000000005',
  decResp:    '33333333-0000-0000-0000-000000000006',
  loopMain:   '33333333-0000-0000-0000-000000000007',
  loopResp:   '33333333-0000-0000-0000-000000000008',
  retryMain:  '33333333-0000-0000-0000-000000000009',
  retryResp:  '33333333-0000-0000-0000-000000000010',
  phMain:     '33333333-0000-0000-0000-000000000011',
  phResp:     '33333333-0000-0000-0000-000000000012',
};

// ── YAML templates ──
const ROUTE_MAIN = (id, name) => `\
id: ${id}
name: ${name}
version: '1.0'
nodes:
  - id: n_start
    type: START
    name: START
    onSuccess: n_send
  - id: n_send
    type: SEND_FIX
    name: SEND_ORDER
    config:
      msgType: D
      fields:
        11: "{{uuid}}"
        54: "1"
        55: AAPL
        38: "100"
        40: "2"
        60: "{{now}}"
    onSuccess: n_route
    onFailure: n_fail
  - id: n_route
    type: ROUTE_FIX
    name: ROUTE_FIX
    config:
      rules:
        - ruleId: r_quote
          label: Quote
          matchers:
            35: S
          targetNodeId: n_pass_q
        - ruleId: r_exec
          label: ExecReport
          matchers:
            35: "8"
          targetNodeId: n_pass_e
        - ruleId: r_default
          label: Default
          matchers: {}
          targetNodeId: n_fail
    timeout:
      value: 10
      unit: SECONDS
      onTimeout: FAIL
    onSuccess: n_fail
    onFailure: n_fail
  - id: n_pass_q
    type: END_PASS
    name: END_PASS_QUOTE
  - id: n_pass_e
    type: END_PASS
    name: END_PASS_EXEC
  - id: n_fail
    type: END_FAIL
    name: END_FAIL`;

const ROUTE_RESP_QUOTE = (id, name) => `\
id: ${id}
name: ${name}
version: '1.0'
nodes:
  - id: n_start
    type: START
    name: START
    onSuccess: n_exp
  - id: n_exp
    type: EXPECT_FIX
    name: EXPECT_ORDER
    config:
      msgType: D
    timeout:
      value: 10
      unit: SECONDS
      onTimeout: FAIL
    onSuccess: n_resp
    onFailure: n_fail
  - id: n_resp
    type: SEND_FIX
    name: SEND_QUOTE
    config:
      msgType: S
      fields:
        117: QT-001
    onSuccess: n_pass
    onFailure: n_fail
  - id: n_pass
    type: END_PASS
    name: END_PASS
  - id: n_fail
    type: END_FAIL
    name: END_FAIL`;

const ROUTE_RESP_EXEC = (id, name) => `\
id: ${id}
name: ${name}
version: '1.0'
nodes:
  - id: n_start
    type: START
    name: START
    onSuccess: n_exp
  - id: n_exp
    type: EXPECT_FIX
    name: EXPECT_ORDER
    config:
      msgType: D
    timeout:
      value: 10
      unit: SECONDS
      onTimeout: FAIL
    onSuccess: n_resp
    onFailure: n_fail
  - id: n_resp
    type: SEND_FIX
    name: SEND_EXEC_REPORT
    config:
      msgType: "8"
      fields:
        11: "{{node:n_exp:tag11}}"
        37: ORD-001
        17: EXEC-001
        39: "0"
        150: "0"
        54: "1"
        55: AAPL
        14: "0"
        151: "100"
        6: "0"
    onSuccess: n_pass
    onFailure: n_fail
  - id: n_pass
    type: END_PASS
    name: END_PASS
  - id: n_fail
    type: END_FAIL
    name: END_FAIL`;

const DECISION_MAIN = id => `\
id: ${id}
name: Test DECISION Main
version: '1.0'
nodes:
  - id: n_start
    type: START
    name: START
    onSuccess: n_send
  - id: n_send
    type: SEND_FIX
    name: SEND_ORDER
    config:
      msgType: D
      fields:
        11: "{{uuid}}"
        54: "1"
        55: AAPL
        38: "100"
        40: "2"
        60: "{{now}}"
    onSuccess: n_exp
    onFailure: n_fail
  - id: n_exp
    type: EXPECT_FIX
    name: EXPECT_EXEC
    config:
      msgType: "8"
      correlation:
        sourceTag: 11
        fromNode: n_send
        targetTag: 11
    timeout:
      value: 10
      unit: SECONDS
      onTimeout: FAIL
    onSuccess: n_decision
    onFailure: n_fail
  - id: n_decision
    type: DECISION
    name: CHECK_FILLED
    config:
      condition: "{{node:n_exp:tag39}} == 2"
    onSuccess: n_pass
    onFailure: n_fail
  - id: n_pass
    type: END_PASS
    name: END_PASS
  - id: n_fail
    type: END_FAIL
    name: END_FAIL`;

const DECISION_RESP = id => `\
id: ${id}
name: Test DECISION Responder
version: '1.0'
nodes:
  - id: n_start
    type: START
    name: START
    onSuccess: n_exp
  - id: n_exp
    type: EXPECT_FIX
    name: EXPECT_ORDER
    config:
      msgType: D
    timeout:
      value: 10
      unit: SECONDS
      onTimeout: FAIL
    onSuccess: n_resp
    onFailure: n_fail
  - id: n_resp
    type: SEND_FIX
    name: SEND_FILLED
    config:
      msgType: "8"
      fields:
        11: "{{node:n_exp:tag11}}"
        37: ORD-DEC-001
        17: EXEC-DEC-001
        39: "2"
        150: "2"
        54: "1"
        55: AAPL
        14: "100"
        151: "0"
        6: "99.50"
        31: "99.50"
        32: "100"
    onSuccess: n_pass
    onFailure: n_fail
  - id: n_pass
    type: END_PASS
    name: END_PASS
  - id: n_fail
    type: END_FAIL
    name: END_FAIL`;

const LOOP_MAIN = id => `\
id: ${id}
name: Test LOOP Main
version: '1.0'
nodes:
  - id: n_start
    type: START
    name: START
    onSuccess: n_loop
  - id: n_loop
    type: LOOP
    name: LOOP_3X
    config:
      iterations: 3
      targetNodeId: n_send
    onSuccess: n_pass
    onFailure: n_fail
  - id: n_send
    type: SEND_FIX
    name: SEND_ITER
    config:
      msgType: D
      fields:
        11: "{{seq:loop-test}}"
        54: "1"
        55: AAPL
        38: "100"
        40: "2"
        60: "{{now}}"
    onSuccess: n_pass
    onFailure: n_fail
  - id: n_pass
    type: END_PASS
    name: END_PASS
  - id: n_fail
    type: END_FAIL
    name: END_FAIL`;

const LOOP_RESP = id => `\
id: ${id}
name: Test LOOP Responder
version: '1.0'
nodes:
  - id: n_start
    type: START
    name: START
    onSuccess: n_loop
  - id: n_loop
    type: LOOP
    name: LOOP_3X
    config:
      iterations: 3
      targetNodeId: n_exp
    onSuccess: n_pass
    onFailure: n_fail
  - id: n_exp
    type: EXPECT_FIX
    name: EXPECT_ITER
    config:
      msgType: D
    timeout:
      value: 10
      unit: SECONDS
      onTimeout: FAIL
    onSuccess: n_pass
    onFailure: n_fail
  - id: n_pass
    type: END_PASS
    name: END_PASS
  - id: n_fail
    type: END_FAIL
    name: END_FAIL`;

const RETRY_MAIN = id => `\
id: ${id}
name: Test RETRY Main
version: '1.0'
nodes:
  - id: n_start
    type: START
    name: START
    onSuccess: n_send
  - id: n_send
    type: SEND_FIX
    name: SEND_ORDER
    config:
      msgType: D
      fields:
        11: "{{uuid}}"
        54: "1"
        55: AAPL
        38: "100"
        40: "2"
        60: "{{now}}"
    onSuccess: n_retry
    onFailure: n_fail
  - id: n_retry
    type: RETRY
    name: RETRY_3X
    retryPolicy:
      maxAttempts: 3
      delayMs: 0
    config:
      targetNodeId: n_exp_r
    onSuccess: n_pass
    onFailure: n_fail
  - id: n_exp_r
    type: EXPECT_FIX
    name: EXPECT_EXEC_RETRY
    config:
      msgType: "8"
    timeout:
      value: 400
      unit: MILLISECONDS
      onTimeout: FAIL
    onSuccess: n_pass
    onFailure: n_fail
  - id: n_pass
    type: END_PASS
    name: END_PASS
  - id: n_fail
    type: END_FAIL
    name: END_FAIL`;

const RETRY_RESP = id => `\
id: ${id}
name: Test RETRY Responder
version: '1.0'
nodes:
  - id: n_start
    type: START
    name: START
    onSuccess: n_delay
  - id: n_delay
    type: DELAY
    name: DELAY_1100MS
    config:
      delayMs: 1100
    onSuccess: n_resp
    onFailure: n_fail
  - id: n_resp
    type: SEND_FIX
    name: SEND_EXEC_LATE
    config:
      msgType: "8"
      fields:
        11: RETRY-ORDER-001
        37: ORD-RETRY-001
        17: EXEC-RETRY-001
        39: "0"
        150: "0"
        54: "1"
        55: AAPL
        14: "0"
        151: "100"
        6: "0"
    onSuccess: n_pass
    onFailure: n_fail
  - id: n_pass
    type: END_PASS
    name: END_PASS
  - id: n_fail
    type: END_FAIL
    name: END_FAIL`;

const PH_MAIN = id => `\
id: ${id}
name: Test PLACEHOLDERS Main
version: '1.0'
nodes:
  - id: n_start
    type: START
    name: START
    onSuccess: n_send1
  - id: n_send1
    type: SEND_FIX
    name: SEND_ALL_PLACEHOLDERS
    config:
      msgType: D
      fields:
        11: "{{uuid}}"
        54: "1"
        55: AAPL
        38: "{{seq:ph-qty}}"
        40: "2"
        60: "{{now}}"
        58: "{{now:offset:+5m}}"
        48: "{{nowdate}}"
        22: "{{seq:ph-ord}}"
    onSuccess: n_exp1
    onFailure: n_fail
  - id: n_exp1
    type: EXPECT_FIX
    name: EXPECT_ECHO
    config:
      msgType: "8"
      correlation:
        sourceTag: 11
        fromNode: n_send1
        targetTag: 11
    timeout:
      value: 10
      unit: SECONDS
      onTimeout: FAIL
    onSuccess: n_send2
    onFailure: n_fail
  - id: n_send2
    type: SEND_FIX
    name: SEND_WITH_NODE_REF
    config:
      msgType: D
      fields:
        11: "{{node:n_exp1:tag11}}"
        54: "2"
        55: MSFT
        38: "50"
        40: "2"
        60: "{{now}}"
    onSuccess: n_pass
    onFailure: n_fail
  - id: n_pass
    type: END_PASS
    name: END_PASS
  - id: n_fail
    type: END_FAIL
    name: END_FAIL`;

const PH_RESP = id => `\
id: ${id}
name: Test PLACEHOLDERS Responder
version: '1.0'
nodes:
  - id: n_start
    type: START
    name: START
    onSuccess: n_exp
  - id: n_exp
    type: EXPECT_FIX
    name: EXPECT_ORDER
    config:
      msgType: D
    timeout:
      value: 10
      unit: SECONDS
      onTimeout: FAIL
    onSuccess: n_resp
    onFailure: n_fail
  - id: n_resp
    type: SEND_FIX
    name: ECHO_CLORDID
    config:
      msgType: "8"
      fields:
        11: "{{node:n_exp:tag11}}"
        37: ORD-PH-001
        17: EXEC-PH-001
        39: "0"
        150: "0"
        54: "1"
        55: AAPL
        14: "0"
        151: "100"
        6: "0"
    onSuccess: n_pass
    onFailure: n_fail
  - id: n_pass
    type: END_PASS
    name: END_PASS
  - id: n_fail
    type: END_FAIL
    name: END_FAIL`;

// ── Main ──
(async () => {
  const headed = !process.argv.includes('--headless');
  console.log('\n\x1b[1m=== FIX Functional UAT ===\x1b[0m');
  console.log(`Mode: ${headed ? 'headed (Chromium visible)' : 'headless'}`);
  console.log('Using Chromium:', CHROMIUM, '\n');

  // ── Step 1: Clean up prior runs ──
  info('Cleaning up prior test scenarios...');
  for (const id of Object.values(ID)) await apiDelete(`/api/v1/scenarios/${id}`);

  info('Cleaning up prior test sessions...');
  const sessions = await apiGet('/api/v1/sessions').catch(() => []);
  for (const s of sessions) {
    if (s.name?.startsWith('UAT-FUNC-')) await apiDelete(`/api/v1/sessions/${s.id}`);
  }
  await sleep(500);

  // ── Step 2: Create loopback sessions ──
  let acceptorId, initiatorId;
  try {
    const a = await apiPost('/api/v1/sessions', {
      name: 'UAT-FUNC-ACCEPTOR', mode: 'ACCEPTOR', fixVersion: 'FIX_44',
      senderCompID: 'SERVER', targetCompID: 'CLIENT',
      host: 'localhost', port: 9001,
      heartbeatInterval: 30, resetOnLogon: true, resetOnLogout: false,
    });
    acceptorId = a.id;
    pass(`acceptor created (${acceptorId})`);
  } catch (e) { fail('create acceptor', e); process.exit(1); }

  try {
    const i = await apiPost('/api/v1/sessions', {
      name: 'UAT-FUNC-INITIATOR', mode: 'INITIATOR', fixVersion: 'FIX_44',
      senderCompID: 'CLIENT', targetCompID: 'SERVER',
      host: 'localhost', port: 9001,
      heartbeatInterval: 30, resetOnLogon: true, resetOnLogout: false,
    });
    initiatorId = i.id;
    pass(`initiator created (${initiatorId})`);
  } catch (e) { fail('create initiator', e); process.exit(1); }

  // Connect ACCEPTOR first, then INITIATOR
  await apiPut(`/api/v1/sessions/${acceptorId}/connect`).catch(e => { fail('connect acceptor', e); process.exit(1); });
  await sleep(500);
  await apiPut(`/api/v1/sessions/${initiatorId}/connect`).catch(e => { fail('connect initiator', e); process.exit(1); });
  info('Waiting for FIX logon...');
  await sleep(3000);

  const [accSt, iniSt] = await Promise.all([
    apiGet(`/api/v1/sessions/${acceptorId}/status`),
    apiGet(`/api/v1/sessions/${initiatorId}/status`),
  ]);
  accSt.connected ? pass('acceptor connected') : fail('acceptor connected', 'connected=false');
  iniSt.connected ? pass('initiator connected') : fail('initiator connected', 'connected=false');

  // ── Step 3: Create all scenarios ──
  info('Creating 12 test scenarios...');
  const create = async yaml => {
    const name = yaml.match(/^name: (.+)$/m)?.[1] ?? 'unknown';
    const res = await apiPost('/api/v1/scenarios', { name, yamlDsl: yaml });
    return res.id;
  };
  await create(ROUTE_MAIN(ID.routeMainA, 'Test ROUTE Main A (Quote)'));
  await create(ROUTE_RESP_QUOTE(ID.routeRespA, 'Test ROUTE Resp A (Quote)'));
  await create(ROUTE_MAIN(ID.routeMainB, 'Test ROUTE Main B (Exec)'));
  await create(ROUTE_RESP_EXEC(ID.routeRespB, 'Test ROUTE Resp B (Exec)'));
  await create(DECISION_MAIN(ID.decMain));
  await create(DECISION_RESP(ID.decResp));
  await create(LOOP_MAIN(ID.loopMain));
  await create(LOOP_RESP(ID.loopResp));
  await create(RETRY_MAIN(ID.retryMain));
  await create(RETRY_RESP(ID.retryResp));
  await create(PH_MAIN(ID.phMain));
  await create(PH_RESP(ID.phResp));
  pass('all 12 scenarios created');

  // ── Step 4: Open browser ──
  const browser = await puppeteer.launch({
    executablePath: CHROMIUM,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
    headless: !headed,
    slowMo: headed ? 150 : 0,
    defaultViewport: null,
  });

  let mainExecF;
  try {
    const page = await browser.newPage();
    await page.goto(BASE, { waitUntil: 'networkidle0' });
    pass('browser: UI loaded');

    // ── Test A: ROUTE_FIX → Quote (35=S) ──
    console.log('\n\x1b[1m--- Test A: ROUTE_FIX → Quote branch (35=S) ---\x1b[0m');
    const respA = await exec(ID.routeRespA, acceptorId);
    await sleep(600);
    const mainA = await exec(ID.routeMainA, initiatorId);
    await runPair(respA, mainA, '[A] ROUTE_FIX/Quote');

    // ── Test B: ROUTE_FIX → ExecReport (35=8) ──
    console.log('\n\x1b[1m--- Test B: ROUTE_FIX → ExecReport branch (35=8) ---\x1b[0m');
    const respB = await exec(ID.routeRespB, acceptorId);
    await sleep(600);
    const mainB = await exec(ID.routeMainB, initiatorId);
    await runPair(respB, mainB, '[B] ROUTE_FIX/ExecReport');

    // ── Test C: DECISION (tag39 == "2") ──
    console.log('\n\x1b[1m--- Test C: DECISION (condition: tag39 == 2) ---\x1b[0m');
    const respC = await exec(ID.decResp, acceptorId);
    await sleep(600);
    const mainC = await exec(ID.decMain, initiatorId);
    await runPair(respC, mainC, '[C] DECISION');

    // ── Test D: LOOP (3 iterations each side) ──
    console.log('\n\x1b[1m--- Test D: LOOP (3 iterations, seq counter) ---\x1b[0m');
    const respD = await exec(ID.loopResp, acceptorId);
    await sleep(600);
    const mainD = await exec(ID.loopMain, initiatorId);
    await runPair(respD, mainD, '[D] LOOP');

    // ── Test F: Placeholders + cross-message {{node:id:tagN}} ──
    // NOTE: run before E — test E leaves a stale 35=D in MessageBuffer (DELAY responder never reads it)
    console.log('\n\x1b[1m--- Test F: Placeholders + {{node:n_exp1:tag11}} cross-message read ---\x1b[0m');
    info('Placeholders tested: {{uuid}}, {{now}}, {{now:offset:+5m}}, {{nowdate}}, {{seq:...}}, {{node:id:tagN}}');
    const respF = await exec(ID.phResp, acceptorId);
    await sleep(600);
    mainExecF = await exec(ID.phMain, initiatorId);
    const { main: mF } = await runPair(respF, mainExecF, '[F] Placeholders');

    if (mF === 'PASSED') {
      // Verify placeholder expansion via messages API
      const msgs = await apiGet(`/api/v1/executions/${mainExecF}/messages`);

      // First OUTBOUND to AAPL (has all placeholder fields)
      const s1 = msgs.find(m => m.direction === 'OUTBOUND' && m.fields?.[55] === 'AAPL' && m.fields?.[40] === '2');
      // Second OUTBOUND to MSFT (uses {{node:n_exp1:tag11}})
      const s2 = msgs.find(m => m.direction === 'OUTBOUND' && m.fields?.[55] === 'MSFT');
      // INBOUND ExecReport (echoes tag11 from responder)
      const r1 = msgs.find(m => m.direction === 'INBOUND' && m.fields?.[39] === '0');

      if (!s1) { fail('F: OUTBOUND message (AAPL order) not found'); }
      else {
        // {{uuid}} — tag 11
        const uuid = s1.fields[11];
        /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(uuid)
          ? pass(`{{uuid}} → "${uuid}"`)
          : fail('{{uuid}} invalid format', uuid);

        // {{now}} — tag 60
        const ts60 = s1.fields[60];
        const ts60ok = ts60 && !isNaN(Date.parse(ts60));
        ts60ok ? pass(`{{now}} → "${ts60}"`) : fail('{{now}} invalid', ts60);

        // {{now:offset:+5m}} — tag 58 (should be ~5 min after tag60)
        const ts58 = s1.fields[58];
        if (ts58 && ts60ok) {
          const diff = Date.parse(ts58) - Date.parse(ts60);
          diff > 0
            ? pass(`{{now:offset:+5m}} → "${ts58}" (+${Math.round(diff/1000)}s from now)`)
            : fail('{{now:offset:+5m}} not in future relative to {{now}}', `diff=${diff}ms`);
        } else {
          fail('{{now:offset:+5m}} field missing or unparseable', ts58);
        }

        // {{nowdate}} — tag 48 (date string)
        const nd = s1.fields[48];
        nd ? pass(`{{nowdate}} → "${nd}"`) : fail('{{nowdate}} empty', nd);

        // {{seq:ph-qty}} — tag 38 (numeric string ≥ 1)
        const qty = s1.fields[38];
        qty && /^\d+$/.test(qty) && parseInt(qty) >= 1
          ? pass(`{{seq:ph-qty}} → "${qty}"`)
          : fail('{{seq:ph-qty}} invalid', qty);

        // {{seq:ph-ord}} — tag 22
        const seqOrd = s1.fields[22];
        seqOrd && /^\d+$/.test(seqOrd) && parseInt(seqOrd) >= 1
          ? pass(`{{seq:ph-ord}} → "${seqOrd}"`)
          : fail('{{seq:ph-ord}} invalid', seqOrd);
      }

      // {{node:n_exp:tag11}} in responder (s1.tag11 echoed to r1.tag11)
      if (s1 && r1) {
        const sentId  = s1.fields[11];
        const echoId  = r1.fields[11];
        sentId && echoId && sentId === echoId
          ? pass(`Responder {{node:n_exp:tag11}} echoed correctly: "${echoId}"`)
          : fail('Responder echo mismatch', `sent=${sentId}, received=${echoId}`);
      } else {
        fail('F: INBOUND ExecReport not found for echo check');
      }

      // {{node:n_exp1:tag11}} — s2.tag11 should match r1.tag11 (cross-message read)
      if (s2 && r1) {
        const nodeRef = s2.fields[11];
        const echoed  = r1.fields[11];
        nodeRef && echoed && nodeRef === echoed
          ? pass(`{{node:n_exp1:tag11}} cross-message read → "${nodeRef}" matches received tag11`)
          : fail('{{node:n_exp1:tag11}} cross-message read mismatch', `send2.tag11=${nodeRef}, recv.tag11=${echoed}`);
      } else {
        fail('F: second OUTBOUND (MSFT) not found for {{node:...}} check');
      }
    }

    // ── Test E: RETRY (3 attempts, message arrives after 2 timeouts) ──
    // Runs last: DELAY responder leaves stale 35=D in ACCEPTOR buffer that would pollute earlier tests
    console.log('\n\x1b[1m--- Test E: RETRY (3 attempts × 400ms, message arrives at 1100ms) ---\x1b[0m');
    const respE = await exec(ID.retryResp, acceptorId);
    await sleep(200);
    const mainE = await exec(ID.retryMain, initiatorId);
    await runPair(respE, mainE, '[E] RETRY');

  } finally {
    await browser.close();
  }

  // ── Cleanup ──
  console.log('\n--- Cleanup ---');
  for (const id of Object.values(ID)) await apiDelete(`/api/v1/scenarios/${id}`);
  await apiPut(`/api/v1/sessions/${initiatorId}/disconnect`).catch(() => null);
  await sleep(300);
  await apiPut(`/api/v1/sessions/${acceptorId}/disconnect`).catch(() => null);
  await sleep(300);
  await apiDelete(`/api/v1/sessions/${initiatorId}`);
  await apiDelete(`/api/v1/sessions/${acceptorId}`);
  pass('cleanup done');

  console.log('\n\x1b[1m=== FIX Functional UAT complete. Exit code:', process.exitCode ?? 0, '===\x1b[0m\n');
})();
