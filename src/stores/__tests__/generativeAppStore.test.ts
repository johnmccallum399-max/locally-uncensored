import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import type { ChatStreamChunk } from '../../api/providers/types'

const { chatStream, storage } = vi.hoisted(() => {
  const values = new Map<string, string>()
  const storage = {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => { values.set(key, value) },
    removeItem: (key: string) => { values.delete(key) },
    clear: () => values.clear(),
  }
  vi.stubGlobal('localStorage', storage)
  vi.stubGlobal('window', { localStorage: storage })
  return { chatStream: vi.fn(), storage }
})

// Keep the store, HTML helpers and persistence real; replace only inference
// and the unrelated model/settings stores.
vi.mock('../../api/providers', () => ({
  getProviderForModel: () => ({ provider: { chatStream }, modelId: 'test-model' }),
}))
vi.mock('../modelStore', () => ({ useModelStore: { getState: () => ({ activeModel: 'test-model' }) } }))
vi.mock('../settingsStore', () => ({ useSettingsStore: { getState: () => ({ settings: {} }) } }))

import { useGenerativeAppStore as store } from '../generativeAppStore'

const valid = '<!doctype html><html><body><button>1</button></body></html>'
const partial = '<!doctype html><html><body><button>1</button>'
const current = () => store.getState().apps[0]

function respond(content: string, done = true) {
  chatStream.mockImplementation(async function* () {
    yield { content, done }
  })
}

beforeEach(() => {
  storage.clear()
  store.setState({ apps: [] })
  chatStream.mockReset()
})

afterEach(() => {
  for (const app of store.getState().apps) store.getState().deleteApp(app.id)
})

describe('generated app completion', () => {
  it.each(['I cannot create this app.', 'Use a calculator library.', '```text\nNo HTML available\n```'])('does not save prose as ready: %s', async (reply) => {
    respond(reply)
    store.getState().createApp('Calculator')
    await vi.waitFor(() => expect(current().status).toBe('error'))
    expect(current().html).toBe('')
    expect(current().error).toMatch(/html|document|app/i)
    expect(JSON.parse(storage.getItem('lu-generative-apps')!).state.apps[0].status).toBe('error')
  })

  it('saves valid fenced HTML and restores it after reload', async () => {
    respond(`Here it is:\n\`\`\`html\n${valid}\n\`\`\`\nEnjoy.`)
    store.getState().createApp('Calculator')
    await vi.waitFor(() => expect(current().status).toBe('ready'))
    expect(current().html).toBe(valid)
    const saved = storage.getItem('lu-generative-apps')!
    store.setState({ apps: [] })
    storage.setItem('lu-generative-apps', saved)
    await store.persist.rehydrate()
    expect(current()).toMatchObject({ status: 'ready', html: valid })
  })

  it('rejects a truncated final response even with done:true', async () => {
    respond(partial)
    store.getState().createApp('Calculator')
    await vi.waitFor(() => expect(current().status).toBe('error'))
    expect(current().error).toMatch(/incomplete|cut off|truncat/i)
  })

  it('rejects a stream ending without a completion chunk', async () => {
    respond(valid, false)
    store.getState().createApp('Calculator')
    await vi.waitFor(() => expect(current().status).toBe('error'))
    expect(current().error).toMatch(/interrupt|complet|stream/i)
  })

  it('keeps partial HTML visible while streaming, then validates completion', async () => {
    let resume!: () => void
    const gate = new Promise<void>((resolve) => { resume = resolve })
    chatStream.mockImplementation(async function* (): AsyncGenerator<ChatStreamChunk> {
      yield { content: partial, done: false }
      await gate
      yield { content: '</body></html>', done: true }
    })
    store.getState().createApp('Calculator')
    await vi.waitFor(() => expect(current()).toMatchObject({ status: 'generating', html: partial }))
    resume()
    await vi.waitFor(() => expect(current()).toMatchObject({ status: 'ready', html: valid }))
  })

  it('does not promote a cancelled partial document to ready', async () => {
    let resume!: () => void
    const gate = new Promise<void>((resolve) => { resume = resolve })
    chatStream.mockImplementation(async function* () {
      yield { content: partial, done: false }
      await gate
    })
    const id = store.getState().createApp('Calculator')!
    await vi.waitFor(() => expect(current().html).toBe(partial))
    store.getState().cancelGeneration(id)
    resume()
    await Promise.resolve()
    expect(current().status).toBe('error')
    expect(current().error).toMatch(/cancel/i)
  })

  it('does not let an aborted stream overwrite a regenerated app', async () => {
    let resume!: () => void
    const gate = new Promise<void>((resolve) => { resume = resolve })
    chatStream.mockImplementationOnce(async function* () {
      yield { content: partial, done: false }
      await gate
    })
    const id = store.getState().createApp('Calculator')!
    await vi.waitFor(() => expect(current().html).toBe(partial))
    respond(valid)
    store.getState().regenerateApp(id)
    await vi.waitFor(() => expect(current().status).toBe('ready'))
    resume()
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(current()).toMatchObject({ status: 'ready', html: valid })
  })

  it.each(['generating', 'ready'] as const)('revalidates persisted %s apps', async (status) => {
    storage.setItem('lu-generative-apps', JSON.stringify({ state: { apps: [
      { id: 'old', title: 'Calculator', html: partial, status, createdAt: 1 },
    ] }, version: 0 }))
    await store.persist.rehydrate()
    expect(current().status).toBe('error')
    expect(current().error).toMatch(/interrupt|incomplete|regenerate/i)
  })

  it('does not promote an interrupted generation even when its partial HTML looks complete', async () => {
    storage.setItem('lu-generative-apps', JSON.stringify({ state: { apps: [
      { id: 'old', title: 'Calculator', html: valid, status: 'generating', createdAt: 1 },
    ] }, version: 0 }))
    await store.persist.rehydrate()
    expect(current().status).toBe('error')
  })
})
