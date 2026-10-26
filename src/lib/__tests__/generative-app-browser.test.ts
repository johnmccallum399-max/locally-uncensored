import { afterEach, describe, expect, it, vi } from 'vitest'
import { buildAppBrowserDocument } from '../generative-app'
import { openGeneratedAppDocument } from '../generative-app-browser'

const { isTauri, invoke } = vi.hoisted(() => ({ isTauri: vi.fn(() => false), invoke: vi.fn() }))
vi.mock('../../api/backend', () => ({ isTauri, openExternal: async () => {} }))
vi.mock('@tauri-apps/api/core', () => ({ invoke }))
const doc = buildAppBrowserDocument('<html><body>Calculator</body></html>')

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  vi.useRealTimers()
  isTauri.mockReturnValue(false)
  invoke.mockReset()
})

describe('opening a generated app', () => {
  it('opens only the sandbox wrapper and severs the opener', async () => {
    vi.useFakeTimers()
    let navigated = ''
    const popup = { opener: {}, location: { replace: (url: string) => { navigated = url } } }
    vi.stubGlobal('window', { open: () => popup })
    const revoke = vi.spyOn(URL, 'revokeObjectURL')
    await openGeneratedAppDocument(doc)
    expect(popup.opener).toBeNull()
    expect(navigated).toMatch(/^blob:/)
    expect(await (await fetch(navigated)).text()).toBe(doc)
    await vi.advanceTimersByTimeAsync(60_000)
    expect(revoke).toHaveBeenCalledWith(navigated)
  })

  it('explains popup blocking and releases the unused blob', async () => {
    vi.stubGlobal('window', { open: () => null })
    const revoke = vi.spyOn(URL, 'revokeObjectURL')
    await expect(openGeneratedAppDocument(doc)).rejects.toThrow(/blocked.*tab|popup/i)
    expect(revoke).toHaveBeenCalledOnce()
  })

  it('surfaces desktop shell rejection instead of silently falling back', async () => {
    isTauri.mockReturnValue(true)
    invoke.mockRejectedValue(new Error('URL not allowed'))
    await expect(openGeneratedAppDocument(doc)).rejects.toThrow(/desktop.*preview/i)
  })

  it('preserves the sandbox in the document passed to an accepting desktop opener', async () => {
    isTauri.mockReturnValue(true)
    let opened = ''
    invoke.mockImplementation(async (command: string, args: { path: string }) => {
      if (command !== 'plugin:shell|open') throw new Error('Unexpected native command')
      opened = args.path
    })
    await openGeneratedAppDocument(doc)
    expect(opened).toMatch(/^data:text\/html;charset=utf-8;base64,/)
    expect(atob(opened.split(',')[1])).toContain('sandbox="allow-scripts"')
  })
})
