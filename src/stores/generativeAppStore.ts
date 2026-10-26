import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { v4 as uuid } from 'uuid'
import { getProviderForModel } from '../api/providers'
import { useModelStore } from './modelStore'
import { useSettingsStore } from './settingsStore'
import {
  APP_SYSTEM_PROMPT,
  buildAppPrompt,
  extractHtml,
} from '../lib/generative-app'

export type GenerativeAppStatus = 'generating' | 'ready' | 'error'

export interface GenerativeApp {
  id: string
  title: string
  html: string
  status: GenerativeAppStatus
  error?: string
  model?: string
  createdAt: number
}

interface GenerativeAppState {
  apps: GenerativeApp[]
  /** Create an app from just a title and start generating it in the background. */
  createApp: (title: string) => string | null
  /** (Re)generate an existing app from its title. */
  regenerateApp: (id: string) => void
  cancelGeneration: (id: string) => void
  renameApp: (id: string, title: string) => void
  deleteApp: (id: string) => void
}

// Generation runs outside React so an app keeps building while the user
// browses elsewhere (like the other apps in the dashboard).
const controllers = new Map<string, AbortController>()

/** Fallback ctx: Ollama silently defaults to 2048, which would truncate a whole app. */
const DEFAULT_APP_CONTEXT = 8192

export const useGenerativeAppStore = create<GenerativeAppState>()(
  persist(
    (set, get) => {
      const patch = (id: string, changes: Partial<GenerativeApp>) =>
        set((s) => ({ apps: s.apps.map((a) => (a.id === id ? { ...a, ...changes } : a)) }))

      const run = async (id: string) => {
        const app = get().apps.find((a) => a.id === id)
        if (!app) return

        const activeModel = useModelStore.getState().activeModel
        if (!activeModel) {
          patch(id, { status: 'error', error: 'No model selected. Pick one in the header first.' })
          return
        }

        controllers.get(id)?.abort()
        const abort = new AbortController()
        controllers.set(id, abort)
        patch(id, { status: 'generating', error: undefined, html: '', model: activeModel })

        try {
          const { provider, modelId } = getProviderForModel(activeModel)
          const settings = useSettingsStore.getState().settings
          const stream = provider.chatStream(
            modelId,
            [
              { role: 'system', content: APP_SYSTEM_PROMPT },
              { role: 'user', content: buildAppPrompt(app.title) },
            ],
            {
              temperature: 0.4,
              maxTokens: 8192,
              contextWindow: settings.contextWindowOverride || DEFAULT_APP_CONTEXT,
              signal: abort.signal,
            },
          )

          let raw = ''
          let lastFlush = 0
          for await (const chunk of stream) {
            if (abort.signal.aborted) return
            raw += chunk.content
            // Throttle store writes so the iframe isn't rebuilt on every token.
            const now = Date.now()
            if (now - lastFlush > 250) {
              lastFlush = now
              patch(id, { html: extractHtml(raw) })
            }
          }

          const html = extractHtml(raw)
          if (!html) {
            patch(id, { status: 'error', error: 'The model returned no app. Try again or pick a bigger model.' })
          } else {
            // A cut-off document still renders; the browser repairs it.
            patch(id, { html, status: 'ready' })
          }
        } catch (err) {
          if (abort.signal.aborted) return
          patch(id, { status: 'error', error: err instanceof Error ? err.message : String(err) })
        } finally {
          if (controllers.get(id) === abort) controllers.delete(id)
        }
      }

      return {
        apps: [],

        createApp: (title) => {
          const clean = title.trim().slice(0, 60)
          if (!clean) return null
          const id = uuid()
          set((s) => ({
            apps: [
              { id, title: clean, html: '', status: 'generating', createdAt: Date.now() },
              ...s.apps,
            ],
          }))
          void run(id)
          return id
        },

        regenerateApp: (id) => { void run(id) },

        cancelGeneration: (id) => {
          controllers.get(id)?.abort()
          controllers.delete(id)
          const app = get().apps.find((a) => a.id === id)
          if (app?.status === 'generating') {
            patch(id, app.html ? { status: 'ready' } : { status: 'error', error: 'Cancelled.' })
          }
        },

        renameApp: (id, title) => {
          const clean = title.trim().slice(0, 60)
          if (clean) patch(id, { title: clean })
        },

        deleteApp: (id) => {
          controllers.get(id)?.abort()
          controllers.delete(id)
          set((s) => ({ apps: s.apps.filter((a) => a.id !== id) }))
        },
      }
    },
    {
      name: 'lu-generative-apps',
      // A generation can't survive a restart — don't resurrect a stuck spinner.
      merge: (persisted, current) => {
        const saved = (persisted as Partial<GenerativeAppState> | undefined)?.apps ?? []
        return {
          ...current,
          apps: saved.map((a) =>
            a.status === 'generating'
              ? a.html
                ? { ...a, status: 'ready' as const }
                : { ...a, status: 'error' as const, error: 'Interrupted. Regenerate to try again.' }
              : a,
          ),
        }
      },
    },
  ),
)
