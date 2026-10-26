import { describe, it, expect } from 'vitest'
import { extractHtml, buildAppDocument, appIcon, buildAppPrompt, validateAppHtml, buildAppBrowserDocument } from '../generative-app'

describe('extractHtml', () => {
  it.each(['I cannot build that app.', 'Here are the steps to build a calculator.', '```text\nNo app available\n```'])('rejects non-document output: %s', (raw) => {
    expect(extractHtml(raw)).toBe('')
  })

  it('returns empty for empty input', () => {
    expect(extractHtml('')).toBe('')
  })

  it('strips markdown fences and surrounding prose', () => {
    const raw = 'Sure! Here you go:\n```html\n<!doctype html><html><body>hi</body></html>\n```\nEnjoy.'
    expect(extractHtml(raw)).toBe('<!doctype html><html><body>hi</body></html>')
  })

  it('handles a fence that has not closed yet (streaming)', () => {
    expect(extractHtml('```html\n<!doctype html><html><bo')).toBe('<!doctype html><html><bo')
  })

  it('drops prose before an unfenced document', () => {
    expect(extractHtml('Here:\n<!DOCTYPE html><html></html>')).toBe('<!DOCTYPE html><html></html>')
  })

  it('removes closed thinking blocks', () => {
    expect(extractHtml('<think>plan</think><!doctype html><html></html>')).toBe('<!doctype html><html></html>')
  })

  it('hides an unclosed thinking block mid-stream', () => {
    expect(extractHtml('<think>still reasoning')).toBe('')
  })
})

describe('buildAppDocument', () => {
  it('returns empty for empty html', () => {
    expect(buildAppDocument('  ')).toBe('')
  })

  it('injects the CSP into an existing <head>', () => {
    const out = buildAppDocument('<!doctype html><html><head><title>x</title></head><body></body></html>')
    expect(out).toContain('<meta http-equiv="Content-Security-Policy"')
    expect(out).toContain("default-src 'none'")
  })

  it('adds a head when only <html> is present', () => {
    const out = buildAppDocument('<html><body>x</body></html>')
    expect(out).toContain('<html><body>x</body></html>')
  })

  it('wraps a bare fragment in a full document', () => {
    const out = buildAppDocument('<div>hi</div>')
    expect(out.startsWith('<!doctype html><meta')).toBe(true)
    expect(out).toContain('<div>hi</div>')
  })

  it('does not allow network access', () => {
    expect(buildAppDocument('<div></div>')).not.toMatch(/connect-src/)
    expect(buildAppDocument('<div></div>')).toContain("default-src 'none'")
  })
})

describe('appIcon', () => {
  it('is deterministic and uses the first letter', () => {
    expect(appIcon('calculator')).toEqual(appIcon('calculator'))
    expect(appIcon('calculator').letter).toBe('C')
  })

  it('falls back for empty titles', () => {
    expect(appIcon('').letter).toBe('?')
  })
})

describe('buildAppPrompt', () => {
  it('includes the trimmed title', () => {
    expect(buildAppPrompt('  Todo  ')).toContain('App name: Todo')
  })
})


describe('validateAppHtml', () => {
  it.each([
    '<!doctype html><html><head><title>Calculator</title></head><body><button>1</button></body></html>',
    '<HTML lang="en"><BODY><script>const text = "<html>";</script>OK</BODY></HTML>',
    '<html><body><!-- <html><body>ignored --><p title=">">OK</p></body></html>',
    '<html><body><noscript>Enable scripts</noscript><template><template>Nested</template>OK</template></body></html>',
  ])('accepts an explicitly completed document', (html) => {
    expect(validateAppHtml(html)).toBeNull()
  })

  it.each([
    '', 'I cannot build this.', '<div>Fragment</div>',
    '<!doctype html><html><bo',
    '<html><body><button>1</button>',
    '<html><head><title>unfinished</head><body>OK</body></html>',
    '<html><body><script>const fake = "</body></html>";',
    '<html><body><!-- </body></html>',
    '<html><body><textarea></body></html>',
    '<html><body><noscript>unfinished</body></html>',
    '<html><body><plaintext>unfinished</body></html>',
    '<html><body><template>unfinished</body></html>',
    '<html><body><div title="unterminated></body></html>',
    '<html><head></head></html>',
    '<html><body>OK</html></body>',
    '<html><body>OK</body></html><script>unfinished',
  ])('rejects missing or truncated document structure: %s', (html) => {
    expect(validateAppHtml(html)).toEqual(expect.any(String))
  })
})


describe('preview security', () => {
  it('applies CSP before any generated executable content, even before head', () => {
    const out = buildAppDocument('<html><script>fetch("https://example.invalid")</script><head></head><body>OK</body></html>')
    expect(out.indexOf('Content-Security-Policy')).toBeLessThan(out.indexOf('<script>'))
    expect(out).toContain("base-uri 'none'")
  })

  it('does not mistake a head string in a comment for the policy insertion point', () => {
    const out = buildAppDocument('<html><!-- <head> --><body>OK</body></html>')
    expect(out.indexOf('Content-Security-Policy')).toBeLessThan(out.indexOf('<!--'))
  })

  it('keeps browser-opened apps inside an escaped sandboxed srcdoc', () => {
    const out = buildAppBrowserDocument('<html><body>" & <script>window.open("https://example.invalid")</script></body></html>')
    expect(out).toContain('sandbox="allow-scripts"')
    expect(out).toContain('referrerpolicy="no-referrer"')
    expect(out).toContain('&lt;script&gt;')
    expect(out).not.toContain('<script>')
    expect(out).toContain('&amp;')
  })
})
