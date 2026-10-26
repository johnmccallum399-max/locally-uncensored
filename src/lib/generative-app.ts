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
  if (start !== -1) out = out.slice(start)
  return out.trim()
}

// Generated apps run offline: no network, no navigation, no forms posting out.
// Combined with `sandbox="allow-scripts"` (opaque origin) this keeps an
// unreviewed, model-written app from reaching the host app or the internet.
const CSP =
  "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; media-src data: blob:; form-action 'none'"

/** Wrap (partial) HTML in a document that carries the offline CSP. */
export function buildAppDocument(html: string): string {
  const meta = `<meta http-equiv="Content-Security-Policy" content="${CSP}">`
  const trimmed = html.trim()
  if (!trimmed) return ''

  if (/<head[\s>]/i.test(trimmed)) {
    return trimmed.replace(/<head([^>]*)>/i, `<head$1>${meta}`)
  }
  if (/<html[\s>]/i.test(trimmed)) {
    return trimmed.replace(/<html([^>]*)>/i, `<html$1><head>${meta}</head>`)
  }
  const body = /^<!doctype/i.test(trimmed) ? trimmed.replace(/^<!doctype[^>]*>/i, '') : trimmed
  return `<!doctype html><html><head><meta charset="utf-8">${meta}</head><body>${body}</body></html>`
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
