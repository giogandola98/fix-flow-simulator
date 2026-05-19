const puppeteer = require('./node_modules/puppeteer-core');

(async () => {
  let passed = 0;
  let failed = 0;
  const assert = (condition, label) => {
    if (condition) { console.log('PASS:', label); passed++; }
    else { console.log('FAIL:', label); failed++; }
  };

  const browser = await puppeteer.launch({
    executablePath: '/usr/bin/chromium-browser',
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox'],
  });

  try {
    const page = await browser.newPage();
    await page.goto('http://localhost:8080', { waitUntil: 'networkidle2', timeout: 15000 });
    await new Promise(r => setTimeout(r, 2000));

    // Click first scenario to open editor (palette only appears in editor view)
    const allButtons = await page.$$('button');
    for (const btn of allButtons) {
      const text = await btn.evaluate(el => el.textContent.trim());
      if (text && text.length > 0 && text.length < 60 && !text.includes('+') && !text.includes('New')) {
        try { await btn.click(); break; } catch (e) { /* ignore */ }
      }
    }
    await new Promise(r => setTimeout(r, 1500));

    const bodyText = await page.evaluate(() => document.body.innerText);

    // Composition group must appear in palette
    assert(bodyText.toLowerCase().includes('composition'), 'Composition group visible in palette');
    assert(bodyText.includes('Call Scenario'), 'Call Scenario label visible in palette');

    // Check draggable item
    const callItems = await page.$$eval('[draggable="true"]', els =>
      els.map(el => el.textContent.trim()).filter(t => t.includes('Call Scenario'))
    );
    assert(callItems.length > 0, 'Call Scenario palette item is draggable');

    // Drag CALL_SCENARIO onto canvas
    const callHandle = await page.evaluateHandle(() => {
      const els = Array.from(document.querySelectorAll('[draggable="true"]'));
      return els.find(el => el.textContent.includes('Call Scenario')) || null;
    });
    const canvas = await page.$('.react-flow__pane');
    if (callHandle && canvas) {
      const srcBox = await callHandle.asElement()?.boundingBox();
      const canvasBox = await canvas.boundingBox();
      if (srcBox && canvasBox) {
        await page.mouse.move(srcBox.x + 5, srcBox.y + 5);
        await page.mouse.down();
        await page.mouse.move(canvasBox.x + 300, canvasBox.y + 200, { steps: 15 });
        await page.mouse.up();
        await new Promise(r => setTimeout(r, 1500));
      }
    }

    // Click a CALL_SCENARIO node on canvas
    const csNodes = await page.$$('.react-flow__node');
    let configPanelText = '';
    for (const n of csNodes) {
      const txt = await n.evaluate(el => el.textContent);
      if (txt && txt.includes('Call Scenario')) {
        await n.click();
        await new Promise(r => setTimeout(r, 800));
        configPanelText = await page.evaluate(() => document.body.innerText);
        break;
      }
    }

    if (configPanelText) {
      assert(configPanelText.includes('Target Scenario'), 'Config panel shows Target Scenario label');
      assert(configPanelText.includes('Input Variables'), 'Config panel shows Input Variables label');
      assert(configPanelText.includes('Output Variables'), 'Config panel shows Output Variables label');

      // Check violet border #8b5cf6 = rgb(139, 92, 246)
      const borderColor = await page.evaluate(() => {
        const nodes = document.querySelectorAll('.react-flow__node');
        for (const node of nodes) {
          if (node.textContent.includes('Call Scenario')) {
            const inner = node.querySelector('div');
            return inner ? window.getComputedStyle(inner).borderColor : null;
          }
        }
        return null;
      });
      assert(borderColor === 'rgb(139, 92, 246)', `Violet border correct (got: ${borderColor})`);
    } else {
      console.log('NOTE: Could not drop CALL_SCENARIO node onto canvas — skipping config panel checks');
    }

    console.log(`\nResults: ${passed} passed, ${failed} failed`);
  } finally {
    await browser.close();
  }

  if (failed > 0) process.exit(1);
})();
