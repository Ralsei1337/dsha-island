/**
 * dsh-island-lite — DeepSeek Harness ⇄ DSHA Island Lite (no root).
 *
 * Watches the live agent event stream and pushes a small state snapshot to the
 * companion app on 127.0.0.1:8798. The app turns that into one promoted
 * notification (HyperOS capsule). No Android API is used from this plugin.
 */
export const name = 'dsh-island-lite'

const BRIDGE = 'http://127.0.0.1:8798'
const MIN_INTERVAL_MS = 120
const TIMEOUT_MS = 1200

const TOOL_STATES = [
  [/todo|plan/i, 'thinking'],
  [/web|fetch|http|browse|url/i, 'searching'],
  [/write|edit|patch|apply|create/i, 'writing'],
  [/read|cat|view|glob|grep|search|find|list/i, 'reading'],
  [/ask|question|confirm|permission/i, 'waiting'],
  [/bash|shell|exec|terminal|command/i, 'running'],
]

const ARG_KEYS = ['command', 'cmd', 'script', 'path', 'file_path', 'filePath',
  'pattern', 'query', 'url', 'description', 'prompt']

const buckets = new Map()

function sessionKey(session) {
  try {
    const raw = session?.id ?? session?.sessionId ?? session?.key ?? ''
    const s = String(raw)
    return s ? s.slice(-8) : '-'
  } catch {
    return '-'
  }
}

function bucket(key) {
  let b = buckets.get(key)
  if (!b) {
    b = { last: '', lastAt: 0, todoDone: 0, todoTotal: 0, turnStartedAt: 0, timer: undefined }
    buckets.set(key, b)
  }
  return b
}

function stateFor(toolName) {
  const n = String(toolName || '')
  for (const [re, state] of TOOL_STATES) if (re.test(n)) return state
  return 'running'
}

function detailOf(argsJson) {
  try {
    if (!argsJson) return ''
    const o = typeof argsJson === 'string' ? JSON.parse(argsJson) : argsJson
    if (!o || typeof o !== 'object') return ''
    for (const key of ARG_KEYS) {
      const v = o[key]
      if (typeof v === 'string' && v.trim()) {
        const one = v.replace(/\s+/g, ' ').trim()
        return one.length > 24 ? one.slice(0, 23) + '…' : one
      }
    }
  } catch { /* half-written JSON: ignore */ }
  return ''
}

async function push(path, params) {
  try {
    const qs = new URLSearchParams()
    for (const [k, v] of Object.entries(params || {})) {
      if (v === undefined || v === null || v === '') continue
      qs.set(k, String(v))
    }
    const url = `${BRIDGE}${path}${qs.size > 0 ? `?${qs}` : ''}`
    await fetch(url, { signal: AbortSignal.timeout(TIMEOUT_MS) })
  } catch { /* app not running: silent */ }
}

function sendState(b, state, text) {
  const signature = `${state}|${text}|${b.todoDone}/${b.todoTotal}`
  const now = Date.now()
  const fire = () => {
    b.last = signature
    b.lastAt = Date.now()
    void push('/state', { state, text, todoDone: b.todoDone, todoTotal: b.todoTotal })
  }
  if (signature === b.last && now - b.lastAt < 1200) return
  if (now - b.lastAt < MIN_INTERVAL_MS) {
    if (b.timer) clearTimeout(b.timer)
    b.timer = setTimeout(() => { b.timer = undefined; fire() }, MIN_INTERVAL_MS)
    return
  }
  if (b.timer) { clearTimeout(b.timer); b.timer = undefined }
  fire()
}

function onEvent(session, event) {
  const type = event?.type
  const data = event?.data
  const key = sessionKey(session)
  const b = bucket(key)

  if (type === 'turn/start') {
    b.turnStartedAt = Date.now()
    sendState(b, 'thinking', '收到任务')
    return
  }

  if (type === 'todo/write') {
    const todos = Array.isArray(data?.todos) ? data.todos : []
    if (todos.length > 0) {
      b.todoTotal = todos.length
      b.todoDone = todos.filter((t) => t?.status === 'completed').length
      const doing = todos.find((t) => t?.status === 'in_progress')
      const doingText = doing?.content ? String(doing.content).slice(0, 18) : ''
      const text = `${b.todoDone}/${b.todoTotal}${doingText ? ' ' + doingText : ''}`
      const signature = `todo|${text}|${b.todoDone}/${b.todoTotal}`
      if (signature !== b.last) {
        b.last = signature
        b.lastAt = Date.now()
        if (b.timer) { clearTimeout(b.timer); b.timer = undefined }
        void push('/state', {
          state: 'todo',
          text,
          todoDone: b.todoDone,
          todoTotal: b.todoTotal,
        })
        return
      }
      sendState(b, 'todo', text)
    }
    return
  }

  if (type === 'tool/call') {
    const toolName = String(data?.name || '')
    const state = stateFor(toolName)
    const detail = detailOf(data?.arguments) || toolName
    const now = Date.now()
    const signature = `${state}|${detail}|${b.todoDone}/${b.todoTotal}`
    if (signature !== b.last) {
      b.last = signature
      b.lastAt = now
      if (b.timer) { clearTimeout(b.timer); b.timer = undefined }
      void push('/state', {
        state,
        text: detail || state,
        todoDone: b.todoDone,
        todoTotal: b.todoTotal,
      })
      return
    }
    sendState(b, state, detail || state)
    return
  }

  if (type === 'turn/end') {
    if (b.timer) { clearTimeout(b.timer); b.timer = undefined }
    const ms = b.turnStartedAt > 0 ? Date.now() - b.turnStartedAt : 0
    const human = ms <= 0 ? '' : ms < 60000 ? `${Math.round(ms / 1000)} 秒` : `${(ms / 60000).toFixed(1)} 分`
    void push('/done', { title: '任务完成', text: human ? `用时 ${human}` : '任务完成' })
    buckets.delete(key)
  }
}

export function apply(ctx, config) {
  if (config?.auto === false) return
  ctx.on('session/event', (session, event) => {
    try {
      onEvent(session, event)
    } catch { /* status display must never break a turn */ }
  })
  ctx.on('dispose', () => {
    for (const b of buckets.values()) if (b.timer) clearTimeout(b.timer)
    buckets.clear()
  })
}
