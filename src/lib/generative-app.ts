/**
 * Generative Apps — helpers for turning a one-line title into a working,
 * self-contained single-file web app produced by the active local model.
 *
 * Everything here is pure (no store / network access) so it is unit-testable.
 */
import { stripAllThinkingTags } from './thinking-stripper'

export const APP_SYSTEM_PROMPT = [
  'You are an app generator. The user gives you only the NAME of an app.',
  'Reply with ONE complete, self-contained, fully working HTML document and nothing else.',
  'Rules:',
  '- Start with <!doctype html>. No markdown fences, no explanations before or after.',
  '- All CSS in one <style> tag and all JavaScript in one <script> tag. No external files, CDNs, fonts, images or network calls.',
  '- The app must actually work: wire up every button and input with real logic and keep state in memory.',
  '- Responsive, polished dark UI with a clear title, good spacing and large touch-friendly controls.',
  '- If the app would normally need live data (maps, weather, search), build a convincing interactive version with realistic sample data and say so subtly in the UI.',
].join('\n')

export function buildAppPrompt(title: string): string {
  return `App name: ${title.trim()}\n\nBuild this app now.`
}

/**
 * Pull the HTML out of a (possibly still streaming) model reply: drops
 * thinking blocks, markdown fences and any prose before the document.
 */
export function extractHtml(raw: string): string {
  if (!raw) return ''
  let out = stripAllThinkingTags(raw).replace(/<think>[\s\S]*?<\/think>/gi, '')
  // An unclosed <think> block mid-stream: hide everything from the opener on.
  const openThink = out.search(/<think>/i)
  if (openThink !== -1) out = out.slice(0, openThink)

  const fence = out.match(/```(?:html)?\s*\n?/i)
  if (fence && fence.index !== undefined) {
    out = out.slice(fence.index + fence[0].length)
    const close = out.indexOf('```')
    if (close !== -1) out = out.slice(0, close)
  }

  const start = out.search(/<!doctype html|<html[\s>]/i)
  return start === -1 ? '' : out.slice(start).trim()
}

/**
 * Completion gate, not an HTML conformance or JavaScript correctness check.
 * Require explicit html/body boundaries; browser auto-repair would otherwise
 * hide truncation. Ignore comments, quoted attributes and raw-text contents so
 * strings such as "</body></html>" cannot masquerade as document completion.
 */
export function validateAppHtml(html: string): string | null {
  if (!/^\s*(?:<!doctype html\s*>\s*)?<html[\s>]/i.test(html)) {
    return 'The model did not return an HTML document. Regenerate or try another model.'
  }
  const incomplete = 'The HTML document is incomplete or cut off. Regenerate or try a model with a larger output limit.'
  let phase: 'start' | 'html' | 'head' | 'body' | 'after-body' | 'end' = 'start'
  let hadHead = false
  let templateDepth = 0
  let offset = 0
  while (offset < html.length) {
    const next = html.indexOf('<', offset)
    const text = html.slice(offset, next === -1 ? html.length : next)
    if (text.trim() && phase !== 'head' && phase !== 'body') return incomplete
    if (next === -1) break
    offset = next
    if (html.startsWith('<!--', offset)) {
      const end = html.indexOf('-->', offset + 4)
      if (end === -1) return incomplete
      offset = end + 3
      continue
    }
    const doctype = html.slice(offset).match(/^<!doctype html\s*>/i)
    if (doctype && phase === 'start') {
      offset += doctype[0].length
      continue
    }
    // A complete tag, with > allowed inside quoted attribute values.
    const tag = html.slice(offset).match(/^<(\/?)([a-z][a-z0-9:-]*)(?=[\s/>])(?:[^<>"']|"[^"]*"|'[^']*')*>/i)
    if (!tag) {
      // A literal less-than sign in body text is legal; an unfinished tag isn't.
      if (phase === 'body' && !/^<[a-z!/?]/i.test(html.slice(offset))) {
        offset++
        continue
      }
      return incomplete
    }
    offset += tag[0].length
    const name = tag[2].toLowerCase()
    const closing = tag[1] === '/'
    // Template contents are inert: their body/html tags cannot close the app.
    if (name === 'template') {
      if (phase !== 'head' && phase !== 'body') return incomplete
      templateDepth += closing ? -1 : 1
      if (templateDepth < 0) return incomplete
      continue
    }
    if (name === 'plaintext' && !closing) return incomplete
    if (templateDepth && /^(html|head|body)$/.test(name)) continue
    if (name === 'html') {
      if (!closing && phase === 'start') phase = 'html'
      else if (closing && phase === 'after-body') phase = 'end'
      else return incomplete
    } else if (name === 'head') {
      if (!closing && phase === 'html' && !hadHead) { phase = 'head'; hadHead = true }
      else if (closing && phase === 'head') phase = 'html'
      else return incomplete
    } else if (name === 'body') {
      if (!closing && phase === 'html') phase = 'body'
      else if (closing && phase === 'body') phase = 'after-body'
      else return incomplete
    } else {
      if (phase !== 'head' && phase !== 'body') return incomplete
      if (!closing && /^(script|style|title|textarea|xmp|iframe|noembed|noframes|noscript)$/.test(name)) {
        const end = new RegExp(`</${name}\\s*>`, 'ig')
        end.lastIndex = offset
        const match = end.exec(html)
        if (!match) return incomplete
        offset = end.lastIndex
      }
    }
  }
  return phase === 'end' && templateDepth === 0 ? null : incomplete
}

// Block network resource loads and form submissions. The trusted frame wrapper
// below also restricts child-frame navigation; CSP alone on a top-level
// generated document is not a sandbox.
const CSP =
  "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; media-src data: blob:; form-action 'none'; base-uri 'none'"

/** Put the offline policy BEFORE all generated markup, including partial HTML. */
export function buildAppDocument(html: string): string {
  const trimmed = html.trim()
  if (!trimmed) return ''
  const body = trimmed.replace(/^<!doctype[^>]*>/i, '')
  return `<!doctype html><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="${CSP}">${body}`
}

/** Trusted wrapper used both in-app and when opening in a separate browser. */
export function buildAppBrowserDocument(html: string): string {
  const doc = buildAppDocument(html)
  if (!doc) return ''
  const escaped = doc.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  // Parent frame-src blocks the child's self-navigation to a network URL.
  // No allow-same-origin, popups, forms, downloads or top-navigation tokens.
  return `<!doctype html><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="${CSP}; frame-src about:"><style>html,body,iframe{margin:0;width:100%;height:100%;border:0}iframe{display:block}</style><iframe sandbox="allow-scripts" referrerpolicy="no-referrer" title="Generated app" srcdoc="${escaped}"></iframe>`
}

const GRADIENTS: [string, string][] = [
  ['#6366f1', '#8b5cf6'],
  ['#ec4899', '#f43f5e'],
  ['#14b8a6', '#0ea5e9'],
  ['#f59e0b', '#ef4444'],
  ['#22c55e', '#14b8a6'],
  ['#a855f7', '#ec4899'],
]

/** Deterministic icon (letter + gradient) so every app gets one with no extra model call. */
export function appIcon(title: string): { letter: string; from: string; to: string } {
  const clean = title.trim()
  let hash = 0
  for (let i = 0; i < clean.length; i++) hash = (hash * 31 + clean.charCodeAt(i)) >>> 0
  const [from, to] = GRADIENTS[hash % GRADIENTS.length]
  return { letter: (clean[0] || '?').toUpperCase(), from, to }
}
