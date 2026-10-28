// Captures live mobile screenshots from the dashboard and renders them inside realistic Android phone mockups.
// Usage: node tools/build-screenshots.mjs

import { chromium } from '/mnt/share/repos/homelab-dashboard/node_modules/@playwright/test/index.mjs';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = path.resolve(__dirname, '..');
const SCREEN_DIR = path.join(ROOT_DIR, 'screenshots');
const URL = process.env.DASHBOARD_URL || 'http://192.168.0.2/';

const fontPath = '/mnt/share/repos/homelab-dashboard/fonts/google-sans-flex-latin-9ec7b22b.woff2';
const fontBase64 = fs.existsSync(fontPath) ? fs.readFileSync(fontPath).toString('base64') : '';

async function captureScreen(browser, hash, prepare = null) {
  const page = await browser.newPage({
    viewport: { width: 412, height: 915 },
    deviceScaleFactor: 2,
    isMobile: true,
    hasTouch: true,
    reducedMotion: 'reduce'
  });

  await page.route('**/*', r => r.request().method() === 'GET' ? r.continue() : r.abort());
  await page.addInitScript(() => {
    localStorage.setItem('homelab.theme.v2', JSON.stringify('dark'));
  });

  await page.goto(URL + hash);
  await page.waitForTimeout(3000);

  // Mask public WAN IP if present
  await page.evaluate(() => {
    for (const el of document.querySelectorAll('body *')) {
      if (el.children.length === 0 && /\b\d{1,3}(\.\d{1,3}){3}\b/.test(el.textContent) && !/^(192\.168|10\.|127\.)/.test(el.textContent.trim().match(/\d{1,3}(\.\d{1,3}){3}/)?.[0] || '')) {
        el.textContent = el.textContent.replace(/\b(?!192\.168)(?!10\.)\d{1,3}(\.\d{1,3}){3}\b/g, '•••.•••.•••.•••');
      }
    }
  });

  // Inject status bar top padding (40px) and bottom navigation bar safe area inset (28px)
  await page.addStyleTag({
    content: `
      .m3-app {
        padding-top: 40px !important;
      }
      .m3-nav-bar {
        height: calc(64px + 28px) !important;
        padding-bottom: 28px !important;
      }
      .m3-app__main {
        padding-bottom: calc(96px + 28px) !important;
      }
      .m3-nav-drawer.is-modal.is-sheet {
        padding-bottom: calc(20px + 28px) !important;
      }
    `
  });

  if (prepare) {
    await prepare(page);
  }

  const buf = await page.screenshot();
  await page.close();
  return `data:image/png;base64,${buf.toString('base64')}`;
}

function generateHTML(imgDataUri) {
  return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  ${fontBase64 ? `@font-face {
    font-family: 'Google Sans Flex';
    src: url('data:font/woff2;base64,${fontBase64}') format('woff2');
    font-weight: 400 700;
  }` : ''}

  * { box-sizing: border-box; margin: 0; padding: 0; }
  
  body {
    background: transparent;
    margin: 0;
    padding: 0;
    font-family: 'Google Sans Flex', -apple-system, BlinkMacSystemFont, Roboto, sans-serif;
  }

  .canvas {
    display: inline-block;
    padding: 36px 36px 48px 36px;
    background: transparent;
  }

  /* Outer Phone Chassis */
  .device {
    position: relative;
    width: 436px;
    height: 939px;
    background: linear-gradient(155deg, #2c3237 0%, #1b1f22 35%, #141719 100%);
    border-radius: 52px;
    padding: 12px;
    box-shadow: 
      0 32px 72px -10px rgba(0, 0, 0, 0.65),
      0 16px 32px -6px rgba(0, 0, 0, 0.4),
      0 4px 12px rgba(0, 0, 0, 0.3),
      inset 0 1px 1px rgba(255, 255, 255, 0.22),
      inset 0 -1px 1px rgba(0, 0, 0, 0.5);
    border: 1px solid rgba(255, 255, 255, 0.09);
  }

  /* Side Buttons */
  .device::before {
    content: '';
    position: absolute;
    top: 190px;
    right: -2.5px;
    width: 3px;
    height: 48px;
    background: #252b2f;
    border-radius: 0 2px 2px 0;
    box-shadow: 1px 0 2px rgba(0,0,0,0.4);
  }
  .device::after {
    content: '';
    position: absolute;
    top: 254px;
    right: -2.5px;
    width: 3px;
    height: 86px;
    background: #252b2f;
    border-radius: 0 2px 2px 0;
    box-shadow: 1px 0 2px rgba(0,0,0,0.4);
  }

  /* Earpiece slit */
  .earpiece {
    position: absolute;
    top: 5px;
    left: 50%;
    transform: translateX(-50%);
    width: 50px;
    height: 3px;
    background: #0c0f11;
    border-radius: 2px;
    z-index: 10;
  }

  /* Screen Display */
  .screen {
    position: relative;
    width: 412px;
    height: 915px;
    border-radius: 40px;
    overflow: hidden;
    background: #091613;
    box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.7);
  }

  /* Screenshot */
  .screen-img {
    position: absolute;
    top: 0;
    left: 0;
    width: 412px;
    height: 915px;
    display: block;
    user-select: none;
    -webkit-user-drag: none;
  }

  /* Android Status Bar (transparent, floating right over the top inset) */
  .status-bar {
    position: absolute;
    top: 0;
    left: 0;
    width: 412px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 22px;
    z-index: 20;
    background: transparent;
    pointer-events: none;
  }

  .status-time {
    font-size: 14.5px;
    font-weight: 500;
    color: #e2e3df;
    letter-spacing: -0.15px;
    line-height: 1;
    transform: translateY(1px);
  }

  /* Front Camera Hole Punch */
  .camera-cutout {
    position: absolute;
    top: 10px;
    left: 50%;
    transform: translateX(-50%);
    width: 13px;
    height: 13px;
    background: #040506;
    border-radius: 50%;
    box-shadow: 
      inset 0 0.5px 1px rgba(255, 255, 255, 0.28),
      0 0 0 1px rgba(0, 0, 0, 0.45);
    z-index: 25;
  }
  .camera-cutout::after {
    content: '';
    position: absolute;
    top: 3px;
    left: 3px;
    width: 4px;
    height: 4px;
    background: radial-gradient(circle, #223748 0%, #000 80%);
    border-radius: 50%;
  }

  /* Status Icons */
  .status-icons {
    display: flex;
    align-items: center;
    gap: 7px;
    color: #e2e3df;
  }
  .status-icons svg {
    display: block;
    fill: currentColor;
  }

  /* Android Bottom Gesture Bar */
  .nav-bar-container {
    position: absolute;
    bottom: 0;
    left: 0;
    width: 100%;
    height: 28px;
    display: flex;
    justify-content: center;
    align-items: flex-end;
    z-index: 30;
    pointer-events: none;
  }

  .gesture-pill {
    width: 130px;
    height: 4.5px;
    background: rgba(255, 255, 255, 0.72);
    border-radius: 3px;
    box-shadow: 0 0.5px 2px rgba(0, 0, 0, 0.4);
    margin-bottom: 9px;
  }
</style>
</head>
<body>

<div class="canvas">
  <div class="device">
    <div class="earpiece"></div>
    <div class="screen">
      <img class="screen-img" src="${imgDataUri}" />

      <div class="status-bar">
        <div class="status-time">9:41</div>
        <div class="camera-cutout"></div>
        <div class="status-icons">
          <!-- Wi-Fi Icon -->
          <svg width="16" height="13" viewBox="0 0 16 13">
            <path d="M8 12.5a1.8 1.8 0 1 1 0-3.6 1.8 1.8 0 0 1 0 3.6Zm-3.8-3.4a5.4 5.4 0 0 1 7.6 0 .8.8 0 0 0 1.1-1.1 7 7 0 0 0-9.8 0 .8.8 0 1 0 1.1 1.1Zm-3.4-3.4a10.2 10.2 0 0 1 14.4 0 .8.8 0 1 0 1.1-1.1 11.8 11.8 0 0 0-16.6 0 .8.8 0 0 0 1.1 1.1Z"/>
          </svg>

          <!-- Cellular Signal Icon -->
          <svg width="15" height="12" viewBox="0 0 15 12">
            <path d="M1 11h2V8H1v3Zm3.5 0h2V5.5h-2V11Zm3.5 0h2V3h-2v8Zm3.5 0h2V0.5h-2v10.5Z"/>
          </svg>

          <!-- Battery Icon -->
          <svg width="22" height="12" viewBox="0 0 22 12">
            <rect x="0.5" y="1" width="18" height="10" rx="2.5" fill="none" stroke="currentColor" stroke-width="1.2"/>
            <rect x="2.5" y="3" width="13.5" height="6" rx="1.5" fill="currentColor"/>
            <path d="M20 4.5c.6 0 1 .4 1 1v1c0 .6-.4 1-1 1v-3Z" fill="currentColor"/>
          </svg>
        </div>
      </div>

      <div class="nav-bar-container">
        <div class="gesture-pill"></div>
      </div>
    </div>
  </div>
</div>

</body>
</html>`;
}

async function run() {
  if (!fs.existsSync(SCREEN_DIR)) {
    fs.mkdirSync(SCREEN_DIR, { recursive: true });
  }

  const browser = await chromium.launch();

  console.log('Capturing live mobile views...');
  const overviewDataUri = await captureScreen(browser, '');
  const moreSheetDataUri = await captureScreen(browser, '#/storage', async page => {
    const moreBtn = page.getByRole('button', { name: 'More' });
    await moreBtn.click();
    await page.waitForTimeout(800);
  });
  const updatesDataUri = await captureScreen(browser, '#/updates');

  console.log('Rendering framed screenshots...');
  const shots = [
    { name: 'overview.png', dataUri: overviewDataUri },
    { name: 'more-sheet.png', dataUri: moreSheetDataUri },
    { name: 'updates.png', dataUri: updatesDataUri }
  ];

  for (const shot of shots) {
    const html = generateHTML(shot.dataUri);
    const page = await browser.newPage({
      viewport: { width: 550, height: 1080 },
      deviceScaleFactor: 2
    });
    
    await page.setContent(html);
    await page.evaluate(() => document.fonts.ready);
    
    const canvasElement = await page.$('.canvas');
    const outPath = path.join(SCREEN_DIR, shot.name);
    await canvasElement.screenshot({
      path: outPath,
      omitBackground: true
    });
    console.log(`Wrote ${outPath}`);
    await page.close();
  }

  await browser.close();
  console.log('All screenshots built successfully!');
}

run().catch(console.error);
