const puppeteer = require('puppeteer-core');
const sleep = ms => new Promise(r => setTimeout(r, ms));

(async () => {
  const browser = await puppeteer.launch({
    executablePath: '/usr/bin/chromium-browser',
    args: ['--no-sandbox', '--disable-gpu'],
    defaultViewport: { width: 1400, height: 900 }
  });
  const page = await browser.newPage();
  await page.goto('http://localhost:5174', { waitUntil: 'networkidle2', timeout: 15000 });

  // Switch to Italian
  await page.evaluate(() => {
    const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent && b.textContent.trim() === 'IT');
    if (btn) btn.click();
  });
  await sleep(500);

  // Load UAT-SendOrder
  await page.evaluate(() => {
    const items = Array.from(document.querySelectorAll('div'));
    const found = items.find(d => d.textContent && d.textContent.trim() === 'UAT-SendOrder');
    if (found) found.click();
  });
  await sleep(1000);

  // Click on SendFIX node
  await page.evaluate(() => {
    const nodes = Array.from(document.querySelectorAll('.react-flow__node'));
    const sfn = nodes.find(n => n.textContent && n.textContent.includes('SEND_FIX'));
    if (sfn) sfn.click();
  });
  await sleep(800);
  await page.screenshot({ path: '/tmp/i18n-it-sendfix.png' });

  // Check canvas Fit button and Scarica/Download button  
  const fitBtn = await page.evaluate(() => {
    const btns = Array.from(document.querySelectorAll('button'));
    return btns.find(b => b.textContent && (b.textContent.includes('Adatta') || b.textContent.includes('Fit')))?.textContent?.trim();
  });
  console.log('Fit button IT:', fitBtn);

  // Switch to French
  await page.evaluate(() => {
    const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent && b.textContent.trim() === 'FR');
    if (btn) btn.click();
  });
  await sleep(500);
  await page.screenshot({ path: '/tmp/i18n-fr-sendfix.png' });

  const fitBtnFR = await page.evaluate(() => {
    const btns = Array.from(document.querySelectorAll('button'));
    return btns.find(b => b.textContent && (b.textContent.includes('Ajuster') || b.textContent.includes('Fit')))?.textContent?.trim();
  });
  console.log('Fit button FR:', fitBtnFR);

  await browser.close();
  console.log('done');
})().catch(e => console.error(e.message));
