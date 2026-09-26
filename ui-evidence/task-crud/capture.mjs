/**
 * 活动任务通知（/meeting/task）界面验证截图脚本。
 *
 * 前置条件（三条都要满足，脚本不负责启动服务）：
 *   1. 后端已启动，默认 http://localhost:8080/api
 *   2. 前端 dev server 已启动，默认 http://localhost:5173
 *   3. Redis 在 127.0.0.1:6379，且与后端同一个实例
 *
 * 运行：
 *   npx -y playwright@1.48.2 install chromium   # 首次
 *   node ui-evidence/task-crud/capture.mjs
 *
 * 为什么走 API 登录而不是在登录页填表单：后端 hparty.captcha.enabled=true，
 * 图形验证码无法自动识别；而验证码原文就存在 Redis（hparty:captcha:<uuid>，2 分钟过期），
 * 这里直接读出来。另一个原因是登录失败 5 次会锁定账号 10 分钟（Constants.LOGIN_FAIL_LIMIT），
 * 必须一次成功，不适合在界面上试错。
 *
 * ⚠️ 本脚本会往当前数据库写入真实数据：新建 1 个任务、编辑它、上传 1 份材料、最后删除它。
 *    删除是逻辑删除（del_flag=1），提交记录与 sys_file 行、磁盘文件不会随之清除，
 *    残留清单见同目录 README.md。
 */
import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';
import net from 'node:net';
import os from 'node:os';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * playwright 装在**全局**（`npm i -g playwright`），不进仓库 package.json ——
 * 它是截图工具，不该给应用引入构建期依赖，装了再卸也容易污染 lock 文件。
 * 浏览器二进制由 `npx playwright install chromium` 下到用户目录。
 * 装在别处时用 PLAYWRIGHT_ROOT 指向其 node_modules 目录。
 */
const playwrightRoot = process.env.PLAYWRIGHT_ROOT
  ?? execSync('npm root -g', { encoding: 'utf8' }).trim();
const { chromium } = createRequire(import.meta.url)(path.join(playwrightRoot, 'playwright'));

const OUT_DIR = path.dirname(fileURLToPath(import.meta.url));
const WEB = process.env.WEB_BASE ?? 'http://localhost:5173';
const API = process.env.API_BASE ?? 'http://localhost:8080/api';
const REDIS = { host: process.env.REDIS_HOST ?? '127.0.0.1', port: Number(process.env.REDIS_PORT ?? 6379) };

const PASSWORD = '123456';
/** 有 task:add/edit/remove/submit 的角色：支部书记 */
const OWNER = 'zsf';
/** 只有 task:list 的角色：组织委员（用于验证按钮门控） */
const VIEWER = 'liming';

/** 本次新建任务的标题，删除用例只认这一条，避免误删演示数据 */
const TITLE = `UI验证-主题党日材料报送-${Date.now().toString().slice(-6)}`;

// ------------------------------------------------------------------ Redis

/** 用 RESP 协议读一个 key，避免为了取验证码再装一个 redis 客户端 */
function redisGet(key) {
  return new Promise((resolve, reject) => {
    const sock = net.createConnection(REDIS);
    let buf = Buffer.alloc(0);
    sock.on('connect', () => sock.write(`*2\r\n$3\r\nGET\r\n$${Buffer.byteLength(key)}\r\n${key}\r\n`));
    sock.on('data', (d) => { buf = Buffer.concat([buf, d]); });
    sock.on('error', reject);
    sock.on('close', () => {
      const text = buf.toString('utf8');
      if (text.startsWith('$-1')) return resolve(null);
      const matched = text.match(/^\$\d+\r\n([\s\S]*)\r\n$/);
      resolve(matched ? matched[1] : null);
    });
    setTimeout(() => sock.end(), 1200);
  });
}

// ------------------------------------------------------------------ 登录

async function login(username) {
  const captcha = await (await fetch(`${API}/auth/captcha`)).json();
  if (captcha.code !== 200) throw new Error(`取验证码失败：${captcha.msg}`);
  const { uuid } = captcha.data;

  const code = await redisGet(`hparty:captcha:${uuid}`);
  if (!code) throw new Error(`Redis 里取不到验证码 hparty:captcha:${uuid}（确认 Redis 与后端同一个实例）`);

  const res = await (await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: PASSWORD, uuid, code }),
  })).json();
  if (res.code !== 200) throw new Error(`${username} 登录失败：${res.msg}`);
  return res.data.token;
}

// ------------------------------------------------------------------ 截图

let seq = 0;
async function shot(page, name) {
  seq += 1;
  const file = path.join(OUT_DIR, `${String(seq).padStart(2, '0')}-${name}.png`);
  await page.screenshot({ path: file });
  console.log(`  ✓ ${path.basename(file)}`);
}

/**
 * @param expect 'list' 时等表格渲染出来；'any' 时只等首屏 —— 无权限的账号
 *               会被路由拦到 NotFound，等表格必然是 20 秒超时。
 */
async function openPage(browser, token, expect = 'list', url = `${WEB}/meeting/task`) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 });
  await ctx.addInitScript(([k, v]) => localStorage.setItem(k, v), ['hparty_token', token]);
  const page = await ctx.newPage();
  await page.goto(url, { waitUntil: 'domcontentloaded' });
  if (expect === 'list') {
    await page.waitForSelector('.ant-table-row, .ant-empty', { timeout: 20000 });
  }
  await page.waitForTimeout(expect === 'list' ? 600 : 1500);
  return { ctx, page };
}

/** 等 antd 的全局提示出现（成功/失败都要抓得到） */
async function shotWithToast(page, name) {
  await page.waitForSelector('.ant-message-notice', { timeout: 8000 });
  await page.waitForTimeout(300);
  await shot(page, name);
}

/** antd 的 message 停留约 3 秒，等它自然消失，避免污染后续截图 */
const settle = (page) => page.waitForTimeout(3200);

// ------------------------------------------------------------------ 主流程

async function main() {
  console.log(`登录 ${OWNER} …`);
  const ownerToken = await login(OWNER);
  console.log(`登录 ${VIEWER} …`);
  const viewerToken = await login(VIEWER);

  const browser = await chromium.launch();

  // ---------- 1. 列表与工具栏 ----------
  console.log('— 列表 / 工具栏');
  let { ctx, page } = await openPage(browser, ownerToken);
  await shot(page, 'list-toolbar');

  // ---------- 2. 发布任务：表单校验 ----------
  console.log('— 发布任务');
  await page.getByRole('button', { name: '发布任务' }).click();
  await page.waitForSelector('.ant-modal-content');
  await page.waitForTimeout(400);
  await shot(page, 'add-modal');

  // 结束日期早于开始日期 → 跨字段校验
  await page.fill('#title', TITLE);
  await page.click('#taskType');
  await page.click('.ant-select-item-option:has-text("主题党日")');
  await page.fill('#startDate', '2026-10-20');
  await page.keyboard.press('Enter');
  await page.fill('#endDate', '2026-10-01');
  await page.keyboard.press('Enter');
  await page.waitForTimeout(500);
  await shot(page, 'add-date-validation');

  // 改成合法值后提交
  await page.fill('#endDate', '2026-10-31');
  await page.keyboard.press('Enter');
  await page.fill('#activityName', '学党纪强党性主题党日');
  await page.fill('#deadline', '2026-10-28');
  await page.keyboard.press('Enter');
  await page.fill('#content', '请各支部于截止日期前报送主题党日开展情况与影像资料。');
  await page.waitForTimeout(400);
  await shot(page, 'add-filled');

  await page.getByRole('button', { name: '确 定' }).click();
  await shotWithToast(page, 'add-success');
  await settle(page);
  await page.waitForSelector(`.ant-table-row:has-text("${TITLE}")`, { timeout: 10000 });

  // ---------- 3. 编辑 ----------
  console.log('— 编辑');
  await page.click(`.ant-table-row:has-text("${TITLE}")`);
  await page.waitForTimeout(300);
  await page.getByRole('button', { name: '编辑' }).click();
  await page.waitForSelector('.ant-modal-content');
  await page.waitForTimeout(400);
  await shot(page, 'edit-modal');
  await page.fill('#title', `${TITLE}（已修改）`);
  await page.getByRole('button', { name: '确 定' }).click();
  await shotWithToast(page, 'edit-success');
  await settle(page);
  await page.waitForSelector(`.ant-table-row:has-text("（已修改）")`, { timeout: 10000 });

  // ---------- 4. 详情抽屉（无提交记录） ----------
  console.log('— 详情');
  await page.click(`.ant-table-row:has-text("${TITLE}")`);
  await page.waitForTimeout(300);
  await page.getByRole('button', { name: '详情' }).click();
  await page.waitForSelector('.ant-drawer-content');
  await page.waitForTimeout(800);
  await shot(page, 'detail-drawer-empty');
  await page.click('.ant-drawer-close');
  await page.waitForTimeout(600);

  // ---------- 5. 上传资料 ----------
  console.log('— 上传资料');
  const material = path.join(os.tmpdir(), `task-ui-evidence-${Date.now()}.txt`);
  fs.writeFileSync(material, 'UI 验证用材料，可删除。\n');

  await page.click(`.ant-table-row:has-text("${TITLE}")`);
  await page.waitForTimeout(300);
  await page.setInputFiles(`.ant-table-row:has-text("${TITLE}") input[type="file"]`, material);
  await shotWithToast(page, 'submit-success');
  await settle(page);

  // 上传后状态由「已发布」推进为「已截止」，详情里能看到提交记录
  await page.click(`.ant-table-row:has-text("${TITLE}")`);
  await page.getByRole('button', { name: '详情' }).click();
  await page.waitForSelector('.ant-drawer-content');
  await page.waitForTimeout(900);
  await shot(page, 'detail-drawer-with-submit');
  await page.click('.ant-drawer-close');
  await page.waitForTimeout(600);

  // ---------- 6. 删除 ----------
  console.log('— 删除');
  await page.click(`.ant-table-row:has-text("${TITLE}")`);
  await page.waitForTimeout(300);
  await page.getByRole('button', { name: '删除' }).click();
  await page.waitForSelector('.ant-modal-confirm');
  await page.waitForTimeout(400);
  await shot(page, 'delete-confirm');
  await page.getByRole('button', { name: '确认删除' }).click();
  await shotWithToast(page, 'delete-success');
  await settle(page);
  await ctx.close();
  fs.rmSync(material, { force: true });

  // ---------- 7. 只读视角（组织委员：仅 task:list） ----------
  console.log('— 只读视角');
  ({ ctx, page } = await openPage(browser, viewerToken));
  await shot(page, 'list-readonly-no-write-buttons');
  await ctx.close();

  // ---------- 8. 未授权账号直接敲 URL ----------
  console.log('— 未授权账号访问（普通党员无 task 菜单）');
  const memberToken = await login('zhaoxue');
  ({ ctx, page } = await openPage(browser, memberToken, 'any'));
  await shot(page, 'member-cannot-open-page');
  await ctx.close();

  await browser.close();
  console.log(`\n完成，共 ${seq} 张，输出目录：${OUT_DIR}`);
}

main().catch((err) => {
  console.error('\n失败：', err.message);
  process.exit(1);
});
