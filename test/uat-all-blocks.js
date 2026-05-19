/**
 * UAT: All Blocks test via GUI
 * Runs scenario "All Blocks UAT" and verifies all nodes pass.
 */
const puppeteer = require('puppeteer-core');
const https = require('https');
const http = require('http');

const BASE = 'http://localhost:8080';
const SCENARIO_ID = 'a5a54e6d-b6a4-4e66-8bb1-aefc11f793c3';

function get(url) {
  return new Promise((resolve, reject) => {
    http.get(url, res => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => {
        try { resolve(JSON.parse(data)); }
        catch(e) { resolve(data); }
      });
    }).on('error', reject);
  });
}

function post(url, body) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(body);
    const opts = { method: 'POST', headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) } };
    const u = new URL(url);
    opts.hostname = u.hostname; opts.port = u.port; opts.path = u.pathname;
    const req = http.request(opts, res => {
      let d = '';
      res.on('data', c => d += c);
      res.on('end', () => {
        try { resolve(JSON.parse(d)); }
        catch(e) { resolve(d); }
      });
    });
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

async function waitForExecution(execId, maxMs = 30000) {
  const start = Date.now();
  while (Date.now() - start < maxMs) {
    const exec = await get(`${BASE}/api/v1/executions/${execId}`);
    if (exec.status === 'PASSED' || exec.status === 'FAILED') return exec;
    await sleep(500);
  }
  throw new Error(`Execution ${execId} did not finish within ${maxMs}ms`);
}

async function main() {
  console.log('=== UAT: All Blocks ===\n');

  // 1. Launch browser and screenshot initial state
  const browser = await puppeteer.launch({
    executablePath: '/usr/bin/chromium-browser',
    args: ['--no-sandbox', '--disable-setuid-sandbox'],
    headless: true,
    defaultViewport: { width: 1600, height: 900 }
  });

  try {
    const page = await browser.newPage();
    await page.goto(`${BASE}`, { waitUntil: 'networkidle2', timeout: 15000 });
    await page.screenshot({ path: '/tmp/uat-01-initial.png' });
    console.log('[1] Initial UI loaded → /tmp/uat-01-initial.png');

    // 2. Trigger execution via API
    console.log('[2] Executing scenario via REST...');
    const execResp = await post(`${BASE}/api/v1/scenarios/${SCENARIO_ID}/execute`, {});
    const execId = execResp.executionId;
    if (!execId) throw new Error('No executionId in response: ' + JSON.stringify(execResp));
    console.log('    executionId:', execId);

    // 3. Navigate to scenario in UI and screenshot running state
    // Click on scenario in list
    await sleep(500);
    await page.screenshot({ path: '/tmp/uat-02-running.png' });
    console.log('[3] Screenshot during execution → /tmp/uat-02-running.png');

    // 4. Wait for completion
    console.log('[4] Waiting for execution to complete (max 30s)...');
    const exec = await waitForExecution(execId, 35000);
    console.log('    Status:', exec.status);

    // 5. Screenshot final UI
    await page.goto(`${BASE}`, { waitUntil: 'networkidle2', timeout: 10000 });
    await sleep(1000);
    await page.screenshot({ path: '/tmp/uat-03-final.png' });
    console.log('[5] Final UI screenshot → /tmp/uat-03-final.png');

    // 6. Get detailed results
    const nodeResults = await get(`${BASE}/api/v1/executions/${execId}/nodes`);
    const messages = await get(`${BASE}/api/v1/executions/${execId}/messages`);
    const events = await get(`${BASE}/api/v1/executions/${execId}/events`);

    console.log('\n=== RESULTS ===');
    console.log('Overall status:', exec.status);
    console.log('Duration:', exec.durationMs, 'ms');

    if (Array.isArray(nodeResults)) {
      console.log('\nNode results:');
      const nodeOrder = ['start','delay-1','loop-1','delay-inner','wait-1','timeout-1',
        'http-1','decision-1','retry-1','branch-1','send-nos','expect-er','validate-er',
        'route-fix','end-pass','end-fail'];
      // Sort by execution order if possible
      const resultMap = {};
      nodeResults.forEach(n => resultMap[n.nodeId] = n);
      nodeOrder.forEach(id => {
        const n = resultMap[id];
        if (n) {
          const icon = n.status === 'PASSED' ? '✓' : n.status === 'FAILED' ? '✗' : n.status === 'SKIPPED' ? '-' : '?';
          console.log(`  ${icon} ${id.padEnd(20)} ${n.status} (${n.durationMs || 0}ms)${n.errorMessage ? ' ERR: ' + n.errorMessage : ''}`);
        }
      });
      // Catch any not in order list
      nodeResults.forEach(n => {
        if (!nodeOrder.includes(n.nodeId)) {
          const icon = n.status === 'PASSED' ? '✓' : '✗';
          console.log(`  ${icon} ${n.nodeId.padEnd(20)} ${n.status}`);
        }
      });
    }

    if (Array.isArray(messages) && messages.length > 0) {
      console.log('\nFIX Messages:', messages.length);
      messages.forEach((m, i) => {
        console.log(`  [${i+1}] dir=${m.direction} msgType=${m.msgType} seq=${m.seqNum}`);
      });
    }

    // 7. Assert
    console.log('\n=== ASSERTIONS ===');
    const passed = exec.status === 'PASSED';
    console.log(passed ? '✓ OVERALL: PASSED' : '✗ OVERALL: FAILED');

    if (Array.isArray(nodeResults)) {
      const failed = nodeResults.filter(n => n.status === 'FAILED' && n.nodeId !== 'end-fail');
      if (failed.length > 0) {
        console.log('✗ Failed nodes:');
        failed.forEach(n => console.log('    -', n.nodeId, ':', n.errorMessage));
      }
    }

    // Check all expected node types executed
    const expectedNodes = ['start','delay-1','loop-1','wait-1','timeout-1',
      'http-1','decision-1','retry-1','branch-1','send-nos','expect-er',
      'validate-er','route-fix','end-pass'];
    if (Array.isArray(nodeResults)) {
      const executedIds = new Set(nodeResults.map(n => n.nodeId));
      const missing = expectedNodes.filter(id => !executedIds.has(id));
      if (missing.length > 0) {
        console.log('✗ Expected nodes NOT executed:', missing.join(', '));
      } else {
        console.log('✓ All expected node types executed');
      }
    }

    return passed;
  } finally {
    await browser.close();
  }
}

main().then(passed => {
  process.exit(passed ? 0 : 1);
}).catch(err => {
  console.error('UAT error:', err.message);
  process.exit(1);
});
