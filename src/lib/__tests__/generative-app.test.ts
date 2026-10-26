import { describe, it, expect } from 'vitest'
import { extractHtml, buildAppDocument, appIcon, buildAppPrompt } from '../generative-app'

describe('extractHtml', () => {
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
    expect(out).toContain('<head><meta http-equiv="Content-Security-Policy"')
    expect(out).toContain("default-src 'none'")
  })

  it('adds a head when only <html> is present', () => {
    const out = buildAppDocument('<html><body>x</body></html>')
    expect(out).toMatch(/<html><head><meta http-equiv="Content-Security-Policy"[^>]*><\/head><body>/)
  })

  it('wraps a bare fragment in a full document', () => {
    const out = buildAppDocument('<div>hi</div>')
    expect(out.startsWith('<!doctype html><html><head>')).toBe(true)
    expect(out).toContain('<body><div>hi</div></body>')
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
