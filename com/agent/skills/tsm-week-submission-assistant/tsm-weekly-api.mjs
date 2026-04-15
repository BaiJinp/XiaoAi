#!/usr/bin/env node
/**
 * 本地调试 TSM「按周提报」接口（与 `src/api/app-center/tsm-week.js` 路径一致）。
 *
 * 用途：在已登录 ipm-ui 的前提下，用浏览器里拿到的鉴权头调用 `weeklyQuery` / `weeklySave` / `weeklyReport`。
 * 与 SKILL 同目录执行，或任意目录 `node <本脚本绝对路径> <子命令>`（勿依赖 package.json 脚本）。
 * 全局安装（如 `~/.claude/skills/...`）时须设 `TSM_REPO_ROOT` 指向 ipm-ui 仓库根，或在与 `list.json` 相对路径匹配的工作目录下执行；见 `TSM_REPO_ROOT` / `TSM_SKILL_DIR` 说明。
 *
 * 安全：勿把 token 写入仓库或贴到聊天；默认只读/写 **本技能目录** 下 `.env.tsm.local`（gitignore 已匹配 `.env.*.local`）。
 */

import { existsSync, readFileSync, writeFileSync } from 'fs'
import http from 'http'
import https from 'https'
import { dirname, isAbsolute, resolve } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))

/**
 * 技能根目录（`.env.tsm.local` 默认所在目录）。可用 `TSM_SKILL_DIR` 覆盖（Claude Code 等与仓库分离安装时使用）。
 */
function getSkillDir() {
  const d = (process.env.TSM_SKILL_DIR || '').trim()
  return d ? resolve(d) : __dirname
}

/**
 * 业务仓库根：解析 `save <list.json>` 等相对路径的第二候选；`TSM_USE_REPO_ENV=1` 时参与 `.env.tsm.local` 查找。
 *
 * 优先级：`TSM_REPO_ROOT` / `TSM_WORKSPACE_ROOT`（显式配置，**Claude 全局技能目录下推荐必设**）→ 若脚本位于 `~/.claude/skills/` 等全局技能树则用 `process.cwd()` → 否则「脚本上溯三级」（兼容仓库内 `.cursor/.../技能名` 布局）。
 */
function resolveWorkspaceRoot() {
  const explicit = (
    process.env.TSM_REPO_ROOT ||
    process.env.TSM_WORKSPACE_ROOT ||
    ''
  ).trim()
  if (explicit) return resolve(explicit)

  const norm = __dirname.replace(/\\/g, '/')
  const globalSkillTree =
    /[/\\].claude[/\\]skills[/\\]/i.test(norm) ||
    /[/\\].codex[/\\]skills[/\\]/i.test(norm)

  if (globalSkillTree) {
    return resolve(process.cwd())
  }

  return resolve(__dirname, '..', '..', '..')
}

function resolveAgainstConfigDir(configDir, relPath) {
  if (relPath === undefined || relPath === null) return ''
  const p = String(relPath).trim()
  if (!p) return ''
  if (isAbsolute(p)) return resolve(p)
  return resolve(configDir, p)
}

/**
 * 读取业务配置 `tsm.config.json`（或 `TSM_CONFIG` / `from-config` 第三参）。
 * @returns {{ cfg: Record<string, unknown>, configDir: string, configPath: string }}
 */
function loadTsmUserConfig(cliPath) {
  let configPath
  const arg = cliPath && String(cliPath).trim()
  if (arg) {
    configPath = isAbsolute(arg) ? resolve(arg) : resolve(process.cwd(), arg)
  } else {
    const e = (process.env.TSM_CONFIG || '').trim()
    if (e) {
      configPath = isAbsolute(e) ? resolve(e) : resolve(process.cwd(), e)
    } else {
      configPath = resolve(getSkillDir(), 'tsm.config.json')
    }
  }
  if (!existsSync(configPath)) {
    throw new Error(
      `未找到业务配置文件: ${configPath}\n请创建 tsm.config.json（参考 tsm.config.example.json），或设置 TSM_CONFIG，或: node tsm-weekly-api.mjs from-config <路径>`
    )
  }
  let cfg
  try {
    cfg = JSON.parse(readFileSync(configPath, 'utf8'))
  } catch (err) {
    throw new Error(`业务配置 JSON 无效: ${configPath}\n${err.message || err}`)
  }
  if (!cfg || typeof cfg !== 'object' || Array.isArray(cfg)) {
    throw new Error(`业务配置须为 JSON 对象: ${configPath}`)
  }
  return { cfg, configDir: dirname(configPath), configPath }
}

/**
 * 将 `tsm.config.json` 写入 process.env，供各子命令读取（鉴权仍在 `.env.tsm.local`）。
 * @param {Record<string, unknown>} cfg
 * @param {string} configDir
 */
function applyTsmConfigToEnv(cfg, configDir) {
  delete process.env.TSM_MULTI_SPEC_FILE

  if (cfg.env && typeof cfg.env === 'object' && !Array.isArray(cfg.env)) {
    for (const [k, v] of Object.entries(cfg.env)) {
      if (v === null || v === undefined) continue
      process.env[k] = typeof v === 'string' ? v : String(v)
    }
  }

  const w = cfg.week
  if (w && typeof w === 'object') {
    if (w.start != null && String(w.start).trim()) {
      process.env.TSM_WEEK_START = String(w.start).trim()
    }
    if (w.weekdaysOnly !== undefined) {
      process.env.TSM_WEEKDAYS_ONLY = w.weekdaysOnly ? '1' : '0'
    }
    if (w.times != null && w.times !== '') {
      const t = w.times
      process.env.TSM_TIMES = Array.isArray(t)
        ? t
            .map((x) => String(x).trim())
            .filter(Boolean)
            .join(',')
        : String(t).trim()
    }
  }

  if (cfg.reportRange != null && String(cfg.reportRange).trim()) {
    process.env.TSM_REPORT_RANGE = String(cfg.reportRange).trim()
  }

  const flags = cfg.flags
  if (flags && typeof flags === 'object') {
    if (flags.skipCalendar === true) process.env.TSM_SKIP_CALENDAR = '1'
    if (flags.useNaiveWeekdays === true) process.env.TSM_USE_NAIVE_WEEKDAYS = '1'
    if (flags.skipPersist === true) process.env.TSM_SKIP_PERSIST = '1'
    if (flags.skipReportAfterSave === true) {
      process.env.TSM_SKIP_REPORT_AFTER_SAVE = '1'
    }
    if (flags.useRepoEnv === true) process.env.TSM_USE_REPO_ENV = '1'
  }

  const sd = cfg.saveDraft
  if (sd && typeof sd === 'object') {
    if (sd.projectName != null && String(sd.projectName).trim()) {
      process.env.TSM_PROJECT_NAME = String(sd.projectName).trim()
    }
    if (sd.jobContent != null && String(sd.jobContent).trim()) {
      process.env.TSM_JOB_CONTENT = String(sd.jobContent).trim()
    }
  }

  const sm = cfg.saveMulti
  const multiPath = cfg.paths?.multiSpecJson
  if (multiPath != null && String(multiPath).trim()) {
    const absMulti = resolveAgainstConfigDir(
      configDir,
      String(multiPath).trim()
    )
    if (!existsSync(absMulti)) {
      throw new Error(`paths.multiSpecJson 文件不存在: ${absMulti}`)
    }
    delete process.env.TSM_MULTI_JSON
    process.env.TSM_MULTI_SPEC_FILE = absMulti
  } else if (sm && typeof sm === 'object' && Array.isArray(sm.rows)) {
    const weekStart =
      (cfg.week &&
        cfg.week.start != null &&
        String(cfg.week.start).trim()) ||
      process.env.TSM_WEEK_START ||
      ''
    if (!weekStart) {
      throw new Error('save-multi 需要 week.start 或 env TSM_WEEK_START')
    }
    const spec = {
      weekStart,
      rows: sm.rows
    }
    if (cfg.reportRange != null && String(cfg.reportRange).trim()) {
      spec.reportRange = String(cfg.reportRange).trim()
    }
    process.env.TSM_MULTI_JSON = JSON.stringify(spec)
    if (sm.skipReport === true) {
      process.env.TSM_SKIP_REPORT_AFTER_SAVE = '1'
    }
  }

  const paths = cfg.paths
  if (paths && typeof paths === 'object') {
    if (paths.listJson != null && String(paths.listJson).trim()) {
      const abs = resolveAgainstConfigDir(
        configDir,
        String(paths.listJson).trim()
      )
      if (!existsSync(abs)) {
        throw new Error(`paths.listJson 文件不存在: ${abs}`)
      }
      process.env.TSM_LIST_JSON = abs
    }
  }
}

/**
 * `.env.tsm.local` 候选路径。
 * 默认 **仅技能目录**（`getSkillDir()`）；设 `TSM_USE_REPO_ENV=1` 时按技能 → cwd → `resolveWorkspaceRoot()` 尝试（兼容旧行为）。
 */
function getEnvLocalCandidates() {
  const skill = resolve(getSkillDir(), '.env.tsm.local')
  if (process.env.TSM_USE_REPO_ENV === '1') {
    return [
      skill,
      resolve(process.cwd(), '.env.tsm.local'),
      resolve(resolveWorkspaceRoot(), '.env.tsm.local')
    ]
  }
  return [skill]
}

/** 从 `.env` 风格文件注入 `process.env`（仅当对应 key 尚未设置时）。 */
function loadEnvLocal() {
  for (const file of getEnvLocalCandidates()) {
    if (!existsSync(file)) continue
    const text = readFileSync(file, 'utf8')
    for (const line of text.split('\n')) {
      const trimmed = line.trim()
      if (!trimmed || trimmed.startsWith('#')) continue
      const eq = trimmed.indexOf('=')
      if (eq <= 0) continue
      const key = trimmed.slice(0, eq).trim()
      let val = trimmed.slice(eq + 1).trim()
      if (
        (val.startsWith('"') && val.endsWith('"')) ||
        (val.startsWith("'") && val.endsWith("'"))
      ) {
        val = val.slice(1, -1)
      }
      if (process.env[key] === undefined) process.env[key] = val
    }
    break
  }
}

/**
 * 以周一为周起始，生成本周 7 天的本地 `YYYY-MM-DD`。
 * @param {Date} [ref=new Date()]
 * @returns {string[]}
 */
function getWeekDatesMondayStart(ref = new Date()) {
  const d = new Date(ref.getFullYear(), ref.getMonth(), ref.getDate())
  const day = d.getDay()
  const mondayOffset = day === 0 ? -6 : 1 - day
  d.setDate(d.getDate() + mondayOffset)
  const out = []
  for (let i = 0; i < 7; i++) {
    const x = new Date(d.getFullYear(), d.getMonth(), d.getDate() + i)
    const y = x.getFullYear()
    const m = String(x.getMonth() + 1).padStart(2, '0')
    const dayNum = String(x.getDate()).padStart(2, '0')
    out.push(`${y}-${m}-${dayNum}`)
  }
  return out
}

/**
 * 是否与配套 SKILL 一致：默认 **只提报周一至周五**。
 * 设为 `0` / `7` / `full` 时使用自然周 **周一～周日** 共 7 天（兼容旧行为）。
 * @returns {boolean}
 */
function isWeekdaysOnly() {
  const v = (process.env.TSM_WEEKDAYS_ONLY || '').trim().toLowerCase()
  if (v === '0' || v === 'false' || v === '7' || v === 'full' || v === 'no') {
    return false
  }
  return true
}

/**
 * 生成「未过日历过滤」的候选日期：默认周一至周五，否则自然周 7 天。
 * 用途：与旧行为及 `TSM_SKIP_CALENDAR` / `TSM_USE_NAIVE_WEEKDAYS` 对齐；**正式提报须用** `resolveTsmTimesForWeek`。
 * @param {Date} ref 该周任意一天（内部先归一到周一）
 * @returns {string[]}
 */
function getTsmTimesForWeekNaive(ref) {
  const monToSun = getWeekDatesMondayStart(ref)
  return isWeekdaysOnly() ? monToSun.slice(0, 5) : monToSun
}

/**
 * 从 `workHourQuery/count` 返回的 `days` 中解析某日 type（与 `calendar.vue` 中 `daysData` 一致）。
 * @param {{ day: unknown, type?: unknown }[]} allDays
 * @param {string} dateKey YYYY-MM-DD
 * @returns {string|undefined} 未找到则 undefined
 */
function calendarTypeForDay(allDays, dateKey) {
  for (const item of allDays) {
    const key = normalizeCalendarDayKey(item.day)
    if (key === dateKey) return String(item.type ?? '')
  }
  return undefined
}

/**
 * 生成 `weeklyQuery` / `dateList` 使用的 `tsmTimes`：**默认**在 naive 候选日上按日历接口排除 type3（提报例外）、type5（假期），与 `submissionWeek/components/calendar.vue` 中 `cloumnData` 过滤逻辑一致。
 * 为何：`getTsmTimesForWeekNaive` 仅按周一～周五机械截取，遇调休/法定假与页面不一致；须以 `workHourQuery/count` 为准。
 *
 * - 设 `TSM_SKIP_CALENDAR=1` 或 `TSM_USE_NAIVE_WEEKDAYS=1` 时回退为 naive（离线/对齐旧脚本）。
 * - 候选日无日历项时**保留**该日（与接口未覆盖该日时的保守策略）。
 *
 * @param {Date} ref 该周任意一天
 * @returns {Promise<string[]>}
 */
async function resolveTsmTimesForWeek(ref) {
  const naive = getTsmTimesForWeekNaive(ref)
  if (
    process.env.TSM_SKIP_CALENDAR === '1' ||
    process.env.TSM_USE_NAIVE_WEEKDAYS === '1'
  ) {
    return naive
  }
  if (!naive.length) return naive
  const allDays = await fetchCalendarDaysMergedForTsmTimes(naive)
  const excluded = []
  const out = []
  for (const d of naive) {
    const t = calendarTypeForDay(allDays, d)
    if (t === '3' || t === '5') {
      excluded.push({ date: d, type: t })
      continue
    }
    out.push(d)
  }
  if (excluded.length) {
    console.log(
      '日历 workHourQuery/count：已排除 type3(例外)/type5(假期) 日期:',
      excluded.map((x) => `${x.date}(type=${x.type})`).join('、')
    )
  }
  if (!out.length) {
    throw new Error(
      '该周在日历接口中无可填报日期（候选日均为例外或假期）。可检查 TSM_WEEK_START，或临时设 TSM_USE_NAIVE_WEEKDAYS=1 查看 naive 列表。'
    )
  }
  return out
}

/** @param {string} base */
function normalizeBaseUrl(base) {
  if (!base) return ''
  return base.endsWith('/') ? base : `${base}/`
}

/**
 * 与 `src/app/request.js` 拦截器对齐的请求头。
 * @param {{ allowEmptyEmpNo?: boolean }} [opts] allowEmptyEmpNo：与前端一致，getUserInfo 前可无工号（仅用 token），解析后再带 P-EmpNo 调 TSM。
 */
function buildHeaders(opts = {}) {
  const { allowEmptyEmpNo = false } = opts
  const pAuth = process.env.P_AUTH || process.env.VUE_APP_TOKEN
  const pRtoken = process.env.P_RTOKEN || process.env.VUE_APP_RTOKEN
  if (!pAuth || !pRtoken) {
    throw new Error(
      '缺少 P_AUTH / P_RTOKEN。请从 Cookie `ipm-token`、`ipm-rtoken` 复制到环境变量或 `.env.tsm.local`。'
    )
  }
  const empNo = process.env.P_EMP_NO || process.env.USER_ID || ''
  if (!allowEmptyEmpNo && !empNo) {
    throw new Error('缺少 P_EMP_NO（工号）。')
  }
  const appId = process.env.P_APP_ID || process.env.VUE_APP_CENTER_BASE_APP_ID || '220928001'
  const companyId = process.env.P_COMPANY_ID || process.env.VUE_APP_COMPANY_ID || ''
  const lang = process.env.P_LANG_ID || 'zh-CN'

  return {
    'Content-Type': 'application/json;charset=utf-8',
    Authorization: pAuth,
    'P-Auth': pAuth,
    'P-Rtoken': pRtoken,
    'P-EmpNo': empNo,
    'P-AppId': appId,
    'P-LangId': lang,
    'P-UA': 'web',
    ...(companyId ? { 'P-CompanyId': companyId } : {})
  }
}

/**
 * 通用网关请求。为何不用 fetch：Node 16 无全局 fetch。
 * @param {string} base 已 normalize 的网关根
 * @param {string} pathSuffix
 * @param {'GET'|'POST'} method
 * @param {unknown} [body] POST 时 JSON body
 * @param {{ allowEmptyEmpNo?: boolean }} [headerOpts] 见 buildHeaders
 */
function requestGateway(base, pathSuffix, method, body, headerOpts) {
  const url = new URL(pathSuffix.replace(/^\//, ''), base)
  const bodyStr =
    body !== undefined && method === 'POST' ? JSON.stringify(body) : ''
  const headers = {
    ...buildHeaders(headerOpts || {}),
    ...(method === 'POST'
      ? { 'Content-Length': Buffer.byteLength(bodyStr || '{}', 'utf8') }
      : {})
  }
  const lib = url.protocol === 'https:' ? https : http

  return new Promise((resolve, reject) => {
    const req = lib.request(
      url,
      { method, headers },
      (res) => {
        const chunks = []
        res.on('data', (c) => chunks.push(c))
        res.on('end', () => {
          const text = Buffer.concat(chunks).toString('utf8')
          let json
          try {
            json = JSON.parse(text)
          } catch {
            json = { raw: text }
          }
          const status = res.statusCode || 0
          resolve({
            ok: status >= 200 && status < 300,
            status,
            json
          })
        })
      }
    )
    req.on('error', reject)
    if (method === 'POST' && bodyStr) req.write(bodyStr)
    req.end()
  })
}

/** 取业务体：与 axios 封装一致时多为 `json.data`。 */
function unwrapData(json) {
  if (json && Object.prototype.hasOwnProperty.call(json, 'data')) return json.data
  return json
}

/**
 * 是否与 `src/app/request.js` isTokenExpired 一致：业务 code 30003 表示访问令牌过期，可尝试用 P_RTOKEN 调 `rtoken/get`。
 */
function isBizTokenExpired(json) {
  if (!json || typeof json !== 'object') return false
  const c = json.code
  if (String(c) === '30003' || Number(c) === 30003) return true
  const msg = typeof json.message === 'string' ? json.message.toLowerCase() : ''
  return msg.includes('token expired')
}

/**
 * 调用 SSO `rtoken/get`（与 `src/api/user.js` getRefreshToken 一致），用当前 `P_RTOKEN` 换新 `P_AUTH` / `P_RTOKEN` 写入 `process.env`。
 * 为何：终端脚本无 Vuex，需在内存中更新头后重试 TSM/工程/SSO 请求。
 */
async function refreshTokensFromRtoken() {
  const rtoken = process.env.P_RTOKEN || process.env.VUE_APP_RTOKEN
  if (!rtoken) {
    throw new Error(
      '无法刷新：缺少 P_RTOKEN（与 Cookie ipm-rtoken 一致）。请重新登录并写入 .env.tsm.local。'
    )
  }
  const sso = process.env.VUE_APP_SSO
  if (!sso) {
    throw new Error('缺少 VUE_APP_SSO，无法调用 rtoken/get。')
  }
  const base = normalizeBaseUrl(sso)
  const pathSuffix = 'uac-auth-service/v2/api/uac-auth/rtoken/get'
  const { json } = await requestGateway(
    base,
    pathSuffix,
    'POST',
    { utoken: rtoken },
    { allowEmptyEmpNo: true }
  )
  const c = json?.code
  const ok =
    json?.success === true ||
    c === undefined ||
    String(c) === '200' ||
    Number(c) === 200 ||
    Number(c) === 0
  if (!ok) {
    const msg = json?.message || ''
    if (Number(c) === 30008 || String(c) === '30008') {
      throw new Error(
        'Refresh Token 已过期，请重新登录并更新 .env.tsm.local 中的 P_AUTH / P_RTOKEN。'
      )
    }
    throw new Error(`刷新 token 失败: code=${c} message=${msg}`)
  }
  const data = unwrapData(json)
  const newAccess = data?.rtoken || data?.token
  const newRefresh = data?.utoken
  if (!newAccess || !newRefresh) {
    throw new Error(`刷新 token 响应缺少 rtoken/utoken: ${JSON.stringify(json).slice(0, 500)}`)
  }
  process.env.P_AUTH = newAccess
  process.env.P_RTOKEN = newRefresh
  if (process.env.VUE_APP_TOKEN !== undefined) process.env.VUE_APP_TOKEN = newAccess
  if (process.env.VUE_APP_RTOKEN !== undefined) process.env.VUE_APP_RTOKEN = newRefresh
  console.warn(
    '已用 SSO uac-auth/rtoken/get 刷新 P_AUTH / P_RTOKEN（与 store user/refreshToken → setToken 一致）。'
  )
  if (process.env.TSM_SKIP_PERSIST !== '1') {
    try {
      persistTokensToEnvLocal()
    } catch (e) {
      console.warn('写入 .env.tsm.local 失败（可忽略）:', e.message || e)
    }
  }
}

/**
 * 将当前内存中的 token 写回 `.env.tsm.local`：默认 **getSkillDir()**（不存在则创建）；`TSM_USE_REPO_ENV=1` 时写回首个已存在的候选文件（技能 → cwd → resolveWorkspaceRoot()）。
 */
function persistTokensToEnvLocal() {
  const pAuth = process.env.P_AUTH
  const pRtoken = process.env.P_RTOKEN
  if (!pAuth || !pRtoken) return
  const skillEnv = resolve(getSkillDir(), '.env.tsm.local')
  if (process.env.TSM_USE_REPO_ENV !== '1') {
    const text = existsSync(skillEnv) ? readFileSync(skillEnv, 'utf8') : ''
    const lines = text ? text.split('\n') : []
    const setLine = (key, val) => {
      const re = new RegExp(`^\\s*${key}\\s*=`)
      const line = `${key} = ${val}`
      const idx = lines.findIndex((l) => re.test(l))
      if (idx >= 0) lines[idx] = line
      else lines.push(line)
    }
    setLine('P_AUTH', pAuth)
    setLine('P_RTOKEN', pRtoken)
    writeFileSync(skillEnv, lines.join('\n'), 'utf8')
    console.warn(`已更新 ${skillEnv} 中的 P_AUTH / P_RTOKEN（请勿提交）。`)
    return
  }
  const candidates = [
    skillEnv,
    resolve(process.cwd(), '.env.tsm.local'),
    resolve(resolveWorkspaceRoot(), '.env.tsm.local')
  ]
  for (const file of candidates) {
    if (!existsSync(file)) continue
    const text = readFileSync(file, 'utf8')
    const lines = text.split('\n')
    const setLine = (key, val) => {
      const re = new RegExp(`^\\s*${key}\\s*=`)
      const line = `${key} = ${val}`
      const idx = lines.findIndex((l) => re.test(l))
      if (idx >= 0) lines[idx] = line
      else lines.push(line)
    }
    setLine('P_AUTH', pAuth)
    setLine('P_RTOKEN', pRtoken)
    writeFileSync(file, lines.join('\n'), 'utf8')
    console.warn(`已更新 ${file} 中的 P_AUTH / P_RTOKEN（请勿提交）。`)
    break
  }
}

/**
 * 业务返回 Token expired（30003）时自动刷新并重试一次；`rtoken/get` 自身不重试以免死循环。
 */
async function requestGatewayWithRetry(base, pathSuffix, method, body, headerOpts) {
  const normalized = pathSuffix.replace(/^\//, '')
  const first = await requestGateway(base, pathSuffix, method, body, headerOpts)
  if (isBizTokenExpired(first.json) && !normalized.includes('rtoken/get')) {
    await refreshTokensFromRtoken()
    return requestGateway(base, pathSuffix, method, body, headerOpts)
  }
  return first
}

async function postTsm(pathSuffix, body) {
  const base = normalizeBaseUrl(
    process.env.TSM_API_BASE || process.env.VUE_APP_TSM_API
  )
  if (!base) {
    return Promise.reject(new Error('缺少 TSM_API_BASE 或 VUE_APP_TSM_API。'))
  }
  return requestGatewayWithRetry(base, pathSuffix, 'POST', body)
}

/** TSM GET，用于 `findProjectStage` 等。 */
async function getTsm(pathSuffix) {
  const base = normalizeBaseUrl(
    process.env.TSM_API_BASE || process.env.VUE_APP_TSM_API
  )
  if (!base) {
    return Promise.reject(new Error('缺少 TSM_API_BASE 或 VUE_APP_TSM_API。'))
  }
  return requestGatewayWithRetry(base, pathSuffix, 'GET')
}

/** 与 `src/api/user.js` getUsers 调用的工程服务前缀一致。 */
async function postProjectApi(pathSuffix, body) {
  const base = normalizeBaseUrl(
    process.env.VUE_APP_BASE_API_2 || process.env.PROJECT_API_BASE
  )
  if (!base) {
    return Promise.reject(
      new Error(
        'save-draft 需要 VUE_APP_BASE_API_2（与 .env 中上层业务服务一致，见 user.js batchFindQuitUserDetailByNo）。'
      )
    )
  }
  return requestGatewayWithRetry(base, pathSuffix, 'POST', body)
}

/**
 * SSO 网关 POST，与 `src/api/user.js` getCurrentUser 同源。
 * @param {string} pathSuffix 如 `uac-auth-service/v2/api/uac-auth/utoken/getUserInfo`
 */
async function postSso(pathSuffix, body = {}) {
  const sso = process.env.VUE_APP_SSO
  if (!sso) {
    return Promise.reject(
      new Error(
        '缺少 VUE_APP_SSO（与 .env 一致，用于 utoken/getUserInfo 解析当前用户）。'
      )
    )
  }
  const base = normalizeBaseUrl(sso)
  return requestGatewayWithRetry(
    base,
    pathSuffix.replace(/^\//, ''),
    'POST',
    body,
    { allowEmptyEmpNo: true }
  )
}

/**
 * 将日历项中的 `day` 规范为本地 `YYYY-MM-DD`，与 `calendar.vue` 中 `new Date(item.day)` 一致。
 */
function normalizeCalendarDayKey(day) {
  const d = day instanceof Date ? day : new Date(day)
  if (Number.isNaN(d.getTime())) return ''
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${dd}`
}

/**
 * `tsmTimes` 可能跨自然月，需分别拉取各月日历数据；与页面 `getCountData({ month })` 一致。
 * @param {string[]} tsmTimes
 * @returns {string[]} 每月任意一天的 `YYYY-MM-DD`（用于请求体 `month`）
 */
function monthAnchorsForTsmTimes(tsmTimes) {
  const seen = new Set()
  const out = []
  for (const t of tsmTimes) {
    const d = new Date(String(t) + 'T12:00:00')
    if (Number.isNaN(d.getTime())) continue
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const key = `${y}-${m}`
    if (seen.has(key)) continue
    seen.add(key)
    out.push(`${y}-${m}-15`)
  }
  return out
}

/**
 * 与 `src/api/app-center/tsm.js` getCountData → `workHourQuery/count` 一致，返回当月 `days`（含 type：4 请假、5 假期等）。
 */
async function fetchWorkHourQueryCountDays(monthYmd) {
  const { json } = await postTsm('workHourQuery/count', { month: monthYmd })
  const c = json?.code
  const ok =
    json?.success === true ||
    c === undefined ||
    String(c) === '200' ||
    Number(c) === 200 ||
    Number(c) === 0
  if (!ok) {
    throw new Error(
      `workHourQuery/count 失败: code=${c} message=${json?.message || ''}`
    )
  }
  const data = unwrapData(json)
  return Array.isArray(data?.days) ? data.days : []
}

/**
 * 合并跨月周的多月 `count` 结果；同日去重保留先出现的一条即可。
 */
async function fetchCalendarDaysMergedForTsmTimes(tsmTimes) {
  const anchors = monthAnchorsForTsmTimes(tsmTimes)
  const merged = []
  const seenDay = new Set()
  for (const month of anchors) {
    const days = await fetchWorkHourQueryCountDays(month)
    for (const item of days) {
      const key = normalizeCalendarDayKey(item.day)
      if (!key || seenDay.has(key)) continue
      seenDay.add(key)
      merged.push(item)
    }
  }
  return merged
}

/**
 * 在目标周 `tsmTimes` 内筛选指定 `type` 的日期（页面：4=请假，5=假期）。
 */
function filterCalendarTypedDatesInWeek(allDays, tsmTimes, typeStr) {
  const inWeek = new Set(tsmTimes)
  const out = []
  for (const item of allDays) {
    if (String(item.type) !== typeStr) continue
    const key = normalizeCalendarDayKey(item.day)
    if (key && inWeek.has(key)) out.push(key)
  }
  return [...new Set(out)].sort()
}

/**
 * 调日历接口 `workHourQuery/count`，若目标周内存在请假/法定假标记则打印提示（不中断命令）。
 * 为何：与 `submissionWeek/components/calendar.vue` 同源，便于终端提报前与页面规则对齐。
 */
async function logCalendarLeaveNoticeForWeek(tsmTimes) {
  if (process.env.TSM_SKIP_CALENDAR === '1' || !tsmTimes?.length) return
  try {
    const allDays = await fetchCalendarDaysMergedForTsmTimes(tsmTimes)
    const leaveDates = filterCalendarTypedDatesInWeek(allDays, tsmTimes, '4')
    const holidayDates = filterCalendarTypedDatesInWeek(allDays, tsmTimes, '5')
    if (leaveDates.length) {
      console.warn(
        '【请假提示】以下日期在日历中标记为请假：',
        leaveDates.join('、'),
        '。与页面一致：非全天请假仍须按可填日完成人天%合计 100% 等规则，请核对。'
      )
    }
    if (holidayDates.length) {
      console.warn(
        '【假期提示】以下日期在日历中标记为假期：',
        holidayDates.join('、'),
        '。若该日不可填报，请以日历与 weeklyQuery 为准调整。'
      )
    }
  } catch (e) {
    console.warn('日历接口 workHourQuery/count 跳过:', e.message || e)
  }
}

/**
 * 用当前 Cookie 对应 token（P-Auth）调 getUserInfo，得到工号与姓名；与 store `user/getCurrentUser` 一致。
 * 为何：提报人不得写死，须与登录态一致。
 * @returns {Promise<{ employeeNo: string, name: string }>}
 */
async function fetchCurrentUserFromToken() {
  const { json } = await postSso(
    'uac-auth-service/v2/api/uac-auth/utoken/getUserInfo',
    {}
  )
  const c = json?.code
  const ok =
    json?.success === true ||
    c === undefined ||
    String(c) === '200' ||
    Number(c) === 200 ||
    Number(c) === 0
  if (!ok) {
    throw new Error(`getUserInfo 失败: ${json?.message || ''} code=${c}`)
  }
  const raw = unwrapData(json)
  const data =
    raw && typeof raw === 'object' && !Array.isArray(raw) ? raw : json
  const employeeNo = data?.employeeNo || data?.jobNumber || ''
  const name = data?.name || data?.realName || ''
  if (!String(employeeNo).trim()) {
    throw new Error(
      `getUserInfo 未返回 employeeNo，原始响应: ${JSON.stringify(json).slice(0, 500)}`
    )
  }
  return { employeeNo: String(employeeNo).trim(), name: String(name || '').trim() }
}

/**
 * 将当前用户写入 `process.env.P_EMP_NO` / `TSM_REPORT_PROPOSER_NAME` 供 `buildHeaders` 与 weekly 载荷一致。
 * 若 `TSM_USE_ENV_PROPOSER=1` 则仅用环境变量（调试）；否则优先调 getUserInfo，失败时可回退 `P_EMP_NO`。
 */
async function ensureReportProposerFromToken() {
  if (process.env.TSM_USE_ENV_PROPOSER === '1') {
    const employeeNo =
      process.env.REPORT_PROPOSER ||
      process.env.P_EMP_NO ||
      process.env.USER_ID ||
      ''
    const name =
      process.env.TSM_REPORT_PROPOSER_NAME ||
      process.env.REPORT_PROPOSER_NAME ||
      ''
    if (!employeeNo) {
      throw new Error(
        'TSM_USE_ENV_PROPOSER=1 时需设置 P_EMP_NO（或 REPORT_PROPOSER）。'
      )
    }
    process.env.P_EMP_NO = employeeNo
    process.env.TSM_REPORT_PROPOSER_NAME = name
    console.log('0) 使用环境变量提报人（TSM_USE_ENV_PROPOSER=1）')
    return { employeeNo, name }
  }

  try {
    const u = await fetchCurrentUserFromToken()
    process.env.P_EMP_NO = u.employeeNo
    process.env.TSM_REPORT_PROPOSER_NAME = u.name
    console.log('0) getUserInfo → 提报人工号/姓名已解析')
    return u
  } catch (e) {
    const fallback =
      process.env.REPORT_PROPOSER ||
      process.env.P_EMP_NO ||
      process.env.USER_ID ||
      ''
    if (fallback) {
      console.warn(
        'getUserInfo 失败，回退 P_EMP_NO:',
        e.message || e
      )
      process.env.P_EMP_NO = fallback
      return {
        employeeNo: fallback,
        name:
          process.env.TSM_REPORT_PROPOSER_NAME ||
          process.env.REPORT_PROPOSER_NAME ||
          ''
      }
    }
    throw e
  }
}

/** 与 `calendar.vue` `getWeekOfMonth` + `index.vue` `reportRange` 一致。 */
function computeReportRange(dateInWeek) {
  const d = new Date(
    dateInWeek.getFullYear(),
    dateInWeek.getMonth(),
    dateInWeek.getDate()
  )
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const firstDay = new Date(y, d.getMonth(), 1)
  const firstDayWeek = firstDay.getDay() || 7
  const currentWeek = Math.ceil((d.getDate() + firstDayWeek - 1) / 7)

  return `${y}年${m}月W${currentWeek}`
}

function assertBizOk(json, step) {
  const c = json?.code
  if (c !== undefined && String(c) !== '200' && Number(c) !== 200) {
    throw new Error(
      `${step} 失败: code=${c} message=${json?.message || ''}`
    )
  }
}

/**
 * 将 `hoursMap` 与 `tsmTimes` 对齐为数值人天%（缺省日期视为 0）；与页面「空」用 0 一致便于列合计 100。
 * @param {string[]} tsmTimes
 * @param {Record<string, number|string|''|undefined>} hoursMap
 */
function normalizeHoursMap(tsmTimes, hoursMap) {
  const out = {}
  for (const t of tsmTimes) {
    const v = hoursMap[t]
    out[t] =
      v === undefined || v === null || v === ''
        ? 0
        : Number(v)
  }
  return out
}

/**
 * 按 `tr-table.vue` 解析单行项目与审批人；`hoursMap` 指定每个 `tsmTime` 的人天%（0–100）。
 * 为何抽成函数：`save-draft` 与 `save-multi` 共用同一套 findProjectByName → findChecker → getUsers。
 */
async function buildRowForProject({
  projectName,
  jobContent,
  tsmTimes,
  hoursMap,
  reportProposer,
  reportProposerName,
  reportRange,
  logPrefix = ''
}) {
  console.log(`${logPrefix}findProjectByName:`, projectName)
  const { json: pjJson } = await postTsm('workHour/findProjectByName', {
    projectName
  })
  assertBizOk(pjJson, 'findProjectByName')
  const projects = unwrapData(pjJson)
  const plist = Array.isArray(projects) ? projects : []
  const target = plist.find((p) => p.projectName === projectName)
  if (!target || !target.projectBid) {
    throw new Error(
      `未找到与名称完全匹配的项目或缺少 projectBid: ${projectName}`
    )
  }

  const projectBid = target.projectBid
  const objectBid = target.objectBid || ''
  const projectType = target.type || target.projectType || ''

  console.log(`${logPrefix}findProjectStage`)
  let projectStage = ''
  try {
    const { json: stJson } = await getTsm(
      `workHour/findProjectStage?projectBid=${encodeURIComponent(projectBid)}`
    )
    const c = stJson?.code
    if (c === undefined || String(c) === '200' || Number(c) === 200) {
      const sd = unwrapData(stJson)
      const arr = Array.isArray(sd) ? sd : []
      if (arr.length) projectStage = arr[0]
    }
  } catch (e) {
    console.warn('findProjectStage 跳过:', e.message || e)
  }

  console.log(`${logPrefix}findChecker`)
  const { json: fcJson } = await postTsm('workHourQuery/findChecker', {
    projectBid,
    projectType,
    reportProposer,
    objectBid
  })
  assertBizOk(fcJson, 'findChecker')
  const checkerPayload = unwrapData(fcJson)

  console.log(`${logPrefix}batchFindQuitUserDetailByNo (getUsers)`)
  let empNoList
  if (checkerPayload == null || checkerPayload === '') {
    throw new Error('findChecker 未返回审批人标识')
  }
  if (typeof checkerPayload === 'string') {
    empNoList = checkerPayload
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
  } else if (Array.isArray(checkerPayload)) {
    empNoList = checkerPayload.map((item) =>
      typeof item === 'object' && item != null
        ? item.employeeNo || item.jobNumber || item
        : item
    )
  } else {
    empNoList = [checkerPayload]
  }
  if (!empNoList.length) {
    throw new Error('无法解析审批人工号列表')
  }

  const userRes = await postProjectApi(
    'plm/user/batchFindQuitUserDetailByNo',
    empNoList
  )
  const uJson = userRes.json
  const c = uJson?.code
  if (c !== undefined && String(c) !== '200' && Number(c) !== 200) {
    console.error(JSON.stringify(uJson, null, 2))
    throw new Error(`getUsers 业务失败: code=${c}`)
  }
  const rawUsers = unwrapData(uJson)
  const ulist = Array.isArray(rawUsers) ? rawUsers : []
  const u0 = ulist[0]
  if (!u0) {
    console.error(JSON.stringify(uJson, null, 2))
    throw new Error('getUsers 未返回用户详情')
  }
  const checker = u0.jobNumber || ''
  const checkerName = u0.name || u0.realName || ''

  const normalized = normalizeHoursMap(tsmTimes, hoursMap)
  const row = {
    flowStatus: '未保存',
    firstReportQuota: '',
    reportQuota: '',
    projectBid,
    projectName: target.projectName || projectName,
    isAdd: 'add',
    projectStage,
    productValue: target.productValue || '',
    jobContent,
    checker,
    checkerName,
    projectType,
    projectStatus: target.statusCode || '',
    projectTypeCode: target.typeCode || target.projectTypeCode || '',
    valueInputType: target.valueInputType || '',
    permissionBid: target.permissionBid || '',
    objectBid,
    reportType: 1,
    reportProposer,
    reportProposerName,
    reportRange,
    dateList: tsmTimes.map((tsmTime) => ({
      bid: '',
      tsmTime,
      reportHours: normalized[tsmTime]
    }))
  }

  for (const t of tsmTimes) {
    row[t] = normalized[t]
  }

  return row
}

/**
 * 按 `tr-table.vue` choiseProject / findCheckerData 拉项目与审批人，再 `weeklySave`。
 * 提报人工号/姓名默认由 `getUserInfo`（需 VUE_APP_SSO）解析，勿在配置中写死。
 */
async function cmdSaveDraft() {
  const projectName = process.env.TSM_PROJECT_NAME || process.env.TSM_PROJECT
  const jobContent = process.env.TSM_JOB_CONTENT
  if (!projectName || !jobContent) {
    throw new Error(
      '请设置 TSM_PROJECT_NAME（项目名称）与 TSM_JOB_CONTENT（工作内容）。'
    )
  }

  const u = await ensureReportProposerFromToken()
  const reportProposer = u.employeeNo
  const reportProposerName = u.name
  if (!String(reportProposerName).trim()) {
    throw new Error(
      '提报人姓名为空：请确认 getUserInfo 返回 name，或临时设置 TSM_USE_ENV_PROPOSER=1 并配置 TSM_REPORT_PROPOSER_NAME。'
    )
  }

  const weekStart = process.env.TSM_WEEK_START || '2026-03-09'
  const ref = new Date(weekStart + 'T12:00:00')
  const tsmTimes = await resolveTsmTimesForWeek(ref)
  const reportRange =
    process.env.TSM_REPORT_RANGE || computeReportRange(ref)

  const hoursMap = Object.fromEntries(tsmTimes.map((t) => [t, 100]))

  console.log(
    `tsmTimes (${isWeekdaysOnly() ? '工作日候选经日历过滤' : '自然周 7 天经日历过滤'}):`,
    tsmTimes.join(', ')
  )
  await logCalendarLeaveNoticeForWeek(tsmTimes)
  const row = await buildRowForProject({
    projectName,
    jobContent,
    tsmTimes,
    hoursMap,
    reportProposer,
    reportProposerName,
    reportRange,
    logPrefix: '1) '
  })

  console.log('5) weeklySave list[0].reportRange=', reportRange)
  const saveRes = await postTsm('workHour/weeklySave', {
    list: [row]
  })
  printResponse(saveRes)
}

/**
 * 多行、按日不同人天%：环境变量 `TSM_MULTI_JSON` 或命令行第三个参数为 JSON 文件路径。
 * 结构：`{ "weekStart": "YYYY-MM-DD", "reportRange"?: "...", "rows": [ { "projectName", "jobContent", "hours": { "YYYY-MM-DD": number } } ] }`
 * 为何：同一周多项目分行时须一次 `weeklySave`/`weeklyReport` 传入完整 `list`，与页面一致。
 */
async function cmdSaveMulti() {
  let spec
  const specPath = process.argv[3] || process.env.TSM_MULTI_SPEC_FILE
  if (specPath) {
    let abs = isAbsolute(specPath)
      ? resolve(specPath)
      : resolve(process.cwd(), specPath)
    if (!existsSync(abs)) {
      const alt = resolve(resolveWorkspaceRoot(), specPath)
      if (existsSync(alt)) abs = alt
    }
    if (!existsSync(abs)) {
      throw new Error(`找不到 save-multi 规格文件: ${specPath}`)
    }
    spec = JSON.parse(readFileSync(abs, 'utf8'))
  } else {
    const raw = process.env.TSM_MULTI_JSON
    if (!raw) {
      throw new Error(
        '请设置 TSM_MULTI_JSON，或执行: node tsm-weekly-api.mjs save-multi <path/to/spec.json>'
      )
    }
    spec = JSON.parse(raw)
  }

  const u = await ensureReportProposerFromToken()
  const reportProposer = u.employeeNo
  const reportProposerName = u.name
  if (!String(reportProposerName).trim()) {
    throw new Error(
      '提报人姓名为空：请确认 getUserInfo 返回 name，或临时设置 TSM_USE_ENV_PROPOSER=1 并配置 TSM_REPORT_PROPOSER_NAME。'
    )
  }

  const weekStart = spec.weekStart || process.env.TSM_WEEK_START || '2026-03-09'
  const ref = new Date(String(weekStart) + 'T12:00:00')
  const tsmTimes = await resolveTsmTimesForWeek(ref)
  const reportRange =
    spec.reportRange || process.env.TSM_REPORT_RANGE || computeReportRange(ref)

  const rowsSpec = spec.rows
  if (!Array.isArray(rowsSpec) || rowsSpec.length === 0) {
    throw new Error('TSM_MULTI_JSON.rows 须为非空数组')
  }

  console.log(
    `save-multi tsmTimes (${isWeekdaysOnly() ? '工作日+日历' : '整周+日历'}):`,
    tsmTimes.join(', ')
  )
  await logCalendarLeaveNoticeForWeek(tsmTimes)

  const list = []
  let idx = 0
  for (const r of rowsSpec) {
    idx += 1
    const projectName = r.projectName
    const jobContent = r.jobContent
    if (!projectName || !jobContent) {
      throw new Error(`第 ${idx} 行缺少 projectName 或 jobContent`)
    }
    const hoursMap = r.hours && typeof r.hours === 'object' ? r.hours : {}
    const row = await buildRowForProject({
      projectName,
      jobContent,
      tsmTimes,
      hoursMap,
      reportProposer,
      reportProposerName,
      reportRange,
      logPrefix: `[行${idx}/${rowsSpec.length}] `
    })
    list.push(row)
  }

  for (const t of tsmTimes) {
    const sum = list.reduce((s, row) => {
      const cell = row.dateList.find((d) => d.tsmTime === t)
      return s + Number(cell?.reportHours || 0)
    }, 0)
    if (Math.abs(sum - 100) > 0.001) {
      console.warn(
        `【校验】${t} 各行人天% 合计为 ${sum}（期望 100），若后端拒绝请调整 hours。`
      )
    }
  }

  console.log('weeklySave reportRange=', reportRange, '共', list.length, '行')
  const saveRes = await postTsm('workHour/weeklySave', { list })
  printResponse(saveRes)
  if (
    saveRes.json?.code !== undefined &&
    String(saveRes.json.code) !== '200' &&
    Number(saveRes.json.code) !== 200
  ) {
    return
  }

  if (process.env.TSM_SKIP_REPORT_AFTER_SAVE === '1') {
    console.log(
      'TSM_SKIP_REPORT_AFTER_SAVE=1：已跳过 weeklyReport（仅 weeklySave 草稿）。需提交时请用 report-saved-week 或 report <list.json>。'
    )
    return
  }

  /**
   * 与 `tr-table.vue` httpRequest + `index.vue` onSubmit 一致：已落库行须 `isAdd: 'update'`，`dateList` 带各日 `bid`，否则 weeklyReport 可能误按新增校验导致「某日超过 100%」等业务错误。
   */
  console.log('weeklyQuery → 拉取已保存行后 weeklyReport…')
  const qBody = { reportProposer, tsmTimes }
  const { json: qJson } = await postTsm('workHour/weeklyQuery', qBody)
  assertBizOk(qJson, 'weeklyQuery')
  const savedData = unwrapData(qJson)
  if (!Array.isArray(savedData) || !savedData.length) {
    throw new Error('weeklySave 成功但 weeklyQuery 无数据，无法提交')
  }
  const reportList = mapWeeklyQueryRowsToReportPayload(
    savedData,
    reportRange,
    reportProposer,
    reportProposerName
  )
  const reportRes = await postTsm('workHour/weeklyReport', { list: reportList })
  printResponse(reportRes)
}

/**
 * 将 `weeklyQuery` 返回行转为与页面「提交」一致的载荷（含 `isAdd`、`dateList.bid`、列字段）。
 * @param {unknown[]} data weeklyQuery 的 data 数组
 */
/**
 * 与 `index.vue` onSubmit 中 `item[list.prop] || ''` 一致：0 会落成空串，避免 weeklyReport 报「工时最小为 1」类校验误伤占位 0。
 * @param {unknown} v
 */
function reportHoursLikeFrontend(v) {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  if (Number.isNaN(n) || n === 0) return ''
  return n
}

function mapWeeklyQueryRowsToReportPayload(
  data,
  reportRange,
  reportProposer,
  reportProposerName
) {
  return data.map((item) => {
    const row = { ...item }
    // weeklyQuery 根级 `tsmTime`/`reportHours` 多为首日快照，与 `dateList` 并存时易导致服务端误判「工时必填」
    delete row.tsmTime
    delete row.reportHours
    row.isAdd =
      item.flowStatus === '草稿' || item.flowStatus === '拒绝' ? 'update' : row.isAdd || ''
    row.reportType = 1
    row.reportRange = reportRange
    row.reportProposer = reportProposer
    row.reportProposerName = reportProposerName
    row.dateList = (item.dateList || []).map((list) => {
      const rh = reportHoursLikeFrontend(list.reportHours)
      return {
        bid: list.bid || '',
        tsmTime: list.tsmTime,
        reportHours: rh
      }
    })
    for (const list of row.dateList) {
      const rh = reportHoursLikeFrontend(list.reportHours)
      row[list.tsmTime] = rh
      row[list.tsmTime + '_bid'] = list.bid
    }
    return row
  })
}

/**
 * 对已保存草稿的周数据执行 `weeklyQuery` → `weeklyReport`（用于 save-multi 失败后单独补提交，或脚本修复后重试）。
 */
async function cmdReportSavedWeek() {
  const u = await ensureReportProposerFromToken()
  const reportProposer = u.employeeNo
  const reportProposerName = u.name
  const weekStart = process.env.TSM_WEEK_START || '2026-03-09'
  const ref = new Date(String(weekStart) + 'T12:00:00')
  const tsmTimes = await resolveTsmTimesForWeek(ref)
  const reportRange =
    process.env.TSM_REPORT_RANGE || computeReportRange(ref)

  await logCalendarLeaveNoticeForWeek(tsmTimes)
  const { json: qJson } = await postTsm('workHour/weeklyQuery', {
    reportProposer,
    tsmTimes
  })
  assertBizOk(qJson, 'weeklyQuery')
  const savedData = unwrapData(qJson)
  if (!Array.isArray(savedData) || !savedData.length) {
    throw new Error('该周无已保存数据可提交')
  }
  const reportList = mapWeeklyQueryRowsToReportPayload(
    savedData,
    reportRange,
    reportProposer,
    reportProposerName
  )
  console.log('weeklyReport 共', reportList.length, '行 reportRange=', reportRange)
  const reportRes = await postTsm('workHour/weeklyReport', { list: reportList })
  printResponse(reportRes)
}

function printResponse({ ok, status, json }) {
  console.log('HTTP', status, ok ? 'OK' : '')
  console.log(JSON.stringify(json, null, 2))
  const code = json?.code
  if (code !== undefined && String(code) !== '200' && Number(code) !== 200) {
    process.exitCode = 1
  }
}

/** `workHour/weeklyQuery` */
async function cmdQuery() {
  const { employeeNo: reportProposer } = await ensureReportProposerFromToken()
  if (!reportProposer) {
    throw new Error('weeklyQuery 需要 reportProposer（工号）。')
  }

  let tsmTimes
  const custom = process.env.TSM_TIMES
  if (custom) {
    tsmTimes = custom.split(/[,;\s]+/).filter(Boolean)
  } else {
    const weekStart = process.env.TSM_WEEK_START
    const ref = weekStart ? new Date(weekStart + 'T12:00:00') : new Date()
    tsmTimes = await resolveTsmTimesForWeek(ref)
  }

  await logCalendarLeaveNoticeForWeek(tsmTimes)

  const body = { reportProposer, tsmTimes }
  console.log(
    `weeklyQuery tsmTimes (${custom ? 'TSM_TIMES 自定义' : isWeekdaysOnly() ? '工作日+日历' : '整周+日历'}):`,
    tsmTimes.join(', ')
  )
  console.log('POST workHour/weeklyQuery body:', JSON.stringify(body, null, 2))
  const result = await postTsm('workHour/weeklyQuery', body)
  printResponse(result)
}

/**
 * 删除指定周已保存的工时行：与 `tr-table.vue` 一致先 `weeklyQuery` 再 `batchDeleteByBids`（按 `dateList[].bid` 收集）。
 * 为何单独成命令：终端侧此前无删除入口，避免用户误周数据只能手点页面；入参与 `query` 共用 `TSM_WEEK_START` / `TSM_TIMES` 以保证 `tsmTimes` 与当初保存一致。
 */
async function cmdDeleteWeek() {
  const { employeeNo: reportProposer } = await ensureReportProposerFromToken()
  if (!reportProposer) {
    throw new Error('batchDeleteByBids 需要 reportProposer（工号）。')
  }

  let tsmTimes
  const custom = process.env.TSM_TIMES
  if (custom) {
    tsmTimes = custom.split(/[,;\s]+/).filter(Boolean)
  } else {
    const weekStart = process.env.TSM_WEEK_START
    if (!weekStart) {
      throw new Error('delete-week 需要 TSM_WEEK_START=该周周一 或 TSM_TIMES=逗号分隔日期。')
    }
    const ref = new Date(String(weekStart) + 'T12:00:00')
    tsmTimes = await resolveTsmTimesForWeek(ref)
  }

  await logCalendarLeaveNoticeForWeek(tsmTimes)

  const body = { reportProposer, tsmTimes }
  console.log(
    `delete-week: weeklyQuery tsmTimes:`,
    tsmTimes.join(', ')
  )
  const { json: qJson } = await postTsm('workHour/weeklyQuery', body)
  assertBizOk(qJson, 'weeklyQuery')
  const savedData = unwrapData(qJson)
  const rows = Array.isArray(savedData) ? savedData : []
  if (!rows.length) {
    console.log('该周 weeklyQuery 无数据，跳过删除。')
    return
  }

  const bids = []
  for (const row of rows) {
    const dl = row?.dateList
    if (!Array.isArray(dl)) continue
    for (const cell of dl) {
      const b = cell?.bid
      if (b !== undefined && b !== null && String(b).trim() !== '') {
        bids.push(String(b).trim())
      }
    }
  }
  if (!bids.length) {
    throw new Error('该周有行数据但 dateList 中无 bid，无法删除（请核对接口返回）。')
  }

  console.log(
    `delete-week: 将删除 ${rows.length} 行共 ${bids.length} 个单元格 bid`
  )
  const result = await postTsm('workHour/batchDeleteByBids', bids)
  printResponse(result)
}

/**
 * 从 JSON 文件读取 `{ list: [...] }`，调用 `weeklySave` 或 `weeklyReport`。
 * 结构须与 `submissionWeek/components/table/index.vue` 中 `save`/`onSubmit` 一致。
 * @param {'save'|'report'} kind
 * @param {string|null} [explicitFile] from-config 传入的绝对路径，优先于 argv / TSM_LIST_JSON
 */
async function cmdFromJson(kind, explicitFile = null) {
  await ensureReportProposerFromToken()

  const argPath = process.argv[3]
  const file =
    (explicitFile && String(explicitFile).trim()) ||
    argPath ||
    process.env.TSM_LIST_JSON
  if (!file) {
    throw new Error(
      `请指定 JSON 文件路径：node tsm-weekly-api.mjs ${kind} <path/to/list.json>`
    )
  }
  let abs = isAbsolute(file) ? resolve(file) : resolve(process.cwd(), file)
  if (!existsSync(abs)) {
    const alt = resolve(resolveWorkspaceRoot(), file)
    if (existsSync(alt)) abs = alt
  }
  if (!existsSync(abs)) {
    throw new Error(`文件不存在: ${file}（已尝试 cwd 与仓库根）`)
  }
  const raw = readFileSync(abs, 'utf8')
  const parsed = JSON.parse(raw)
  const list = parsed.list
  if (!Array.isArray(list)) {
    throw new Error('JSON 根对象须包含 `list` 数组，与 weeklySave/weeklyReport 一致。')
  }
  const pathSuffix =
    kind === 'save' ? 'workHour/weeklySave' : 'workHour/weeklyReport'
  const calTimes = []
  for (const row of list) {
    const dl = row?.dateList
    if (!Array.isArray(dl)) continue
    for (const cell of dl) {
      const t = cell?.tsmTime
      if (t && !calTimes.includes(t)) calTimes.push(t)
    }
  }
  calTimes.sort()
  await logCalendarLeaveNoticeForWeek(calTimes)

  console.log(`POST ${pathSuffix}，共 ${list.length} 行`)
  const result = await postTsm(pathSuffix, { list })
  printResponse(result)
}

async function cmdWeekDates() {
  const weekStart = process.env.TSM_WEEK_START
  const ref = weekStart ? new Date(weekStart + 'T12:00:00') : new Date()
  const tsmTimes = await resolveTsmTimesForWeek(ref)
  console.log(
    JSON.stringify(
      {
        mode: isWeekdaysOnly() ? 'weekdays_5' : 'full_week_7',
        calendarFiltered:
          process.env.TSM_SKIP_CALENDAR === '1' ||
          process.env.TSM_USE_NAIVE_WEEKDAYS === '1'
            ? false
            : true,
        tsmTimes
      },
      null,
      2
    )
  )
}

/**
 * 从 `tsm.config.json`（或 `TSM_CONFIG` / 第三参路径）读取 **action、周次、项目、工时** 等，写入 env 后调用对应子命令。
 * 鉴权与网关仍在 `.env.tsm.local`（勿把 token 写进 tsm.config.json）。
 */
async function cmdFromConfig() {
  const cliPath = process.argv[3]
  const { cfg, configDir, configPath } = loadTsmUserConfig(cliPath)
  console.log('from-config:', configPath)
  applyTsmConfigToEnv(cfg, configDir)

  const action = String(cfg.action || '').trim()
  if (!action) {
    throw new Error('tsm.config.json 缺少 "action" 字段')
  }

  switch (action) {
    case 'query':
      return cmdQuery()
    case 'save-draft':
      return cmdSaveDraft()
    case 'save-multi':
      return cmdSaveMulti()
    case 'report-saved-week':
      return cmdReportSavedWeek()
    case 'week-dates':
      return cmdWeekDates()
    case 'delete-week':
      return cmdDeleteWeek()
    case 'save': {
      const listPath = (process.env.TSM_LIST_JSON || '').trim()
      if (!listPath) {
        throw new Error(
          'action=save 须在配置中设置 paths.listJson（含 list 数组的 JSON 文件）'
        )
      }
      return cmdFromJson('save', listPath)
    }
    case 'report': {
      const listPathR = (process.env.TSM_LIST_JSON || '').trim()
      if (!listPathR) {
        throw new Error(
          'action=report 须在配置中设置 paths.listJson（含 list 数组的 JSON 文件）'
        )
      }
      return cmdFromJson('report', listPathR)
    }
    default:
      throw new Error(
        `未知 action: "${action}"（支持 query | save-draft | save-multi | report-saved-week | week-dates | delete-week | save | report）`
      )
  }
}

function printHelp() {
  console.log(`
用法（可在本技能目录或仓库根执行）:
  node tsm-weekly-api.mjs from-config [tsm.config.json]   按业务配置文件执行（推荐：参数全在 tsm.config.json）
  node tsm-weekly-api.mjs query              查询本周已填报（weeklyQuery）
  node tsm-weekly-api.mjs delete-week        按 TSM_WEEK_START 周查询后 batchDeleteByBids（与页面删行一致）
  node tsm-weekly-api.mjs save-draft         仅项目名称+工作内容：拉 BID/审批人后 weeklySave
  node tsm-weekly-api.mjs save-multi [spec.json]  多行按日人天%：默认 weeklySave 后 weeklyQuery 再 weeklyReport；设 TSM_SKIP_REPORT_AFTER_SAVE=1 则仅保存草稿
  node tsm-weekly-api.mjs report-saved-week     对已保存的当周草稿执行 weeklyQuery→weeklyReport（需 TSM_WEEK_START）
  node tsm-weekly-api.mjs save <list.json>   保存草稿（weeklySave）
  node tsm-weekly-api.mjs report <list.json> 提交提报（weeklyReport）

save-draft 额外需要:
  TSM_PROJECT_NAME        项目名称（与系统完全一致）
  TSM_JOB_CONTENT         工作内容
  TSM_WEEK_START          该周周一 YYYY-MM-DD（默认 2026-03-09）
  VUE_APP_SSO             SSO 网关（与 .env 一致，用于 utoken/getUserInfo 解析提报人工号/姓名）
  VUE_APP_BASE_API_2      上层业务服务（与 .env 一致，用于 batchFindQuitUserDetailByNo）
  可选 TSM_REPORT_RANGE
  调试可设 TSM_USE_ENV_PROPOSER=1 并配置 P_EMP_NO、TSM_REPORT_PROPOSER_NAME，跳过 getUserInfo

save-multi 额外需要:
  TSM_MULTI_JSON 或参数 spec.json  含 weekStart、rows[].projectName、jobContent、rows[].hours.{日期: 人天%}

环境变量:
  TSM_REPO_ROOT / TSM_WORKSPACE_ROOT  业务仓库根（Claude 等全局技能安装时建议设置；用于 save/report 相对路径第二候选、TSM_USE_REPO_ENV 时 .env 候选）
  TSM_SKILL_DIR                      可选；覆盖「脚本所在目录」作为默认 .env.tsm.local 目录
  TSM_API_BASE / VUE_APP_TSM_API   TSM 网关前缀
  P_AUTH, P_RTOKEN                 Cookie ipm-token、ipm-rtoken
  VUE_APP_SSO                      解析当前用户（推荐，替代手写 P_EMP_NO）
  业务 code 30003（Token expired）时自动调 SSO rtoken/get 刷新并重试一次（与 src/api/user.js getRefreshToken 一致）
  可选 TSM_SKIP_PERSIST=1          刷新成功后不写入 .env.tsm.local（默认会写回 P_AUTH/P_RTOKEN 便于下次运行）
  可选 TSM_USE_REPO_ENV=1          同时从 cwd / 仓库根读或写 .env.tsm.local（默认仅用技能目录）
  可选 TSM_SKIP_CALENDAR=1         不调用 workHourQuery/count（含请假/假期提示与可填日过滤）
  可选 TSM_USE_NAIVE_WEEKDAYS=1     可填日仅用周一至周五/7天截取，不调 count 过滤（与 TSM_SKIP_CALENDAR 二选一即可跳过过滤）
  可选 P_EMP_NO                    仅当 getUserInfo 失败时回退请求头
  可选: TSM_TIMES                  逗号分隔日期，覆盖下方默认周范围
  可选: TSM_WEEK_START=YYYY-MM-DD  该周周一
  TSM_WEEKDAYS_ONLY              默认 1：仅周一至周五 5 天；设 0 或 7 或 full 为周一～周日 7 天
  save/report: 可用 TSM_LIST_JSON 代替命令行文件路径
  TSM_CONFIG                     业务配置文件路径（默认 技能目录/tsm.config.json）；与 from-config 第三参二选一
  TSM_MULTI_SPEC_FILE            内部：save-multi 规格 JSON 绝对路径（from-config 使用 paths.multiSpecJson 时设置）

from-config / tsm.config.json 概要:
  action        必填：query | save-draft | save-multi | report-saved-week | week-dates | delete-week | save | report
  week          可选：{ start, weekdaysOnly, times }
  saveDraft     save-draft 用：{ projectName, jobContent }
  saveMulti     save-multi 用：{ rows, skipReport } 或改用 paths.multiSpecJson
  paths         可选：{ listJson, multiSpecJson }（相对配置文件所在目录）
  flags         可选：{ skipCalendar, useNaiveWeekdays, skipPersist, skipReportAfterSave, useRepoEnv }
  env           可选：额外写入 process.env 的键值（如 TSM_REPO_ROOT）

自动加载 .env.tsm.local（勿提交）:
  默认仅: getSkillDir()（脚本同级，或 TSM_SKILL_DIR）
  TSM_USE_REPO_ENV=1 时: 技能目录 → 当前工作目录 → resolveWorkspaceRoot()（第一个存在的文件）
`)
}

loadEnvLocal()

const cmd = process.argv[2]
if (cmd === 'query') {
  cmdQuery().catch((e) => {
    console.error(e.message || e)
    process.exit(1)
  })
} else if (cmd === 'delete-week') {
  cmdDeleteWeek().catch((e) => {
    console.error(e.message || e)
    process.exit(1)
  })
} else if (cmd === 'save') {
  cmdFromJson('save').catch((e) => {
    console.error(e.message || e)
    process.exit(1)
  })
} else if (cmd === 'report') {
  cmdFromJson('report').catch((e) => {
    console.error(e.message || e)
    process.exit(1)
  })
} else if (cmd === 'save-draft') {
  cmdSaveDraft().catch((e) => {
    console.error(e.message || e)
    process.exit(1)
  })
} else if (cmd === 'save-multi') {
  cmdSaveMulti().catch((e) => {
    console.error(e.message || e)
    process.exit(1)
  })
} else if (cmd === 'report-saved-week') {
  cmdReportSavedWeek().catch((e) => {
    console.error(e.message || e)
    process.exit(1)
  })
} else if (cmd === 'from-config' || cmd === 'config') {
  cmdFromConfig().catch((e) => {
    console.error(e.message || e)
    process.exit(1)
  })
} else if (cmd === 'week-dates') {
  cmdWeekDates().catch((e) => {
    console.error(e.message || e)
    process.exit(1)
  })
} else {
  printHelp()
  process.exit(cmd ? 1 : 0)
}
