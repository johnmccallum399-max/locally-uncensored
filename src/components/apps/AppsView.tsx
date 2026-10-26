import { useMemo, useState } from 'react'
import { ArrowLeft, Loader2, Plus, RotateCw, Square, Trash2, TriangleAlert, ExternalLink } from 'lucide-react'
import { useGenerativeAppStore, type GenerativeApp } from '../../stores/generativeAppStore'
import { useModelStore } from '../../stores/modelStore'
import { appIcon, buildAppBrowserDocument } from '../../lib/generative-app'
import { openGeneratedAppDocument } from '../../lib/generative-app-browser'

const EXAMPLES = ['Calculator', 'Pomodoro timer', 'Coffee shop finder', 'Unit converter', 'Snake game']

export function AppsView() {
  const apps = useGenerativeAppStore((s) => s.apps)
  const createApp = useGenerativeAppStore((s) => s.createApp)
  const activeModel = useModelStore((s) => s.activeModel)
  const [title, setTitle] = useState('')
  const [openId, setOpenId] = useState<string | null>(null)

  const openApp = apps.find((a) => a.id === openId)
  if (openApp) return <AppRunner app={openApp} onBack={() => setOpenId(null)} />

  const submit = (value: string) => {
    const id = createApp(value)
    if (id) setTitle('')
  }

  return (
    <div className="h-full overflow-y-auto p-6">
      <div className="max-w-4xl mx-auto">
        <h1 className="text-lg font-semibold text-gray-900 dark:text-white">Apps</h1>
        <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
          Type a name and your local model builds the app on demand. Nothing is stored but the name until you open it.
        </p>

        <form
          onSubmit={(e) => { e.preventDefault(); submit(title) }}
          className="mt-4 flex items-center gap-2"
        >
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g. Calculator, Budget tracker, Chess clock…"
            maxLength={60}
            className="flex-1 px-3 py-2 rounded-lg text-sm bg-gray-100 dark:bg-white/[0.04] border border-gray-200 dark:border-white/10 text-gray-900 dark:text-white placeholder:text-gray-400 outline-none focus:border-gray-400 dark:focus:border-white/30"
          />
          <button
            type="submit"
            disabled={!title.trim() || !activeModel}
            className="flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm font-medium bg-gray-900 text-white dark:bg-white dark:text-gray-900 disabled:opacity-40 transition-opacity"
          >
            <Plus size={14} /> Create
          </button>
        </form>
        {!activeModel && (
          <p className="mt-2 text-xs text-amber-600 dark:text-amber-400">Select a model in the header to generate apps.</p>
        )}

        {apps.length === 0 && (
          <div className="mt-8 text-center">
            <p className="text-sm text-gray-500 dark:text-gray-400">No apps yet. Try one:</p>
            <div className="mt-3 flex flex-wrap justify-center gap-2">
              {EXAMPLES.map((ex) => (
                <button
                  key={ex}
                  onClick={() => submit(ex)}
                  disabled={!activeModel}
                  className="px-3 py-1.5 rounded-full text-xs bg-gray-100 dark:bg-white/[0.06] text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-white/10 disabled:opacity-40 transition-colors"
                >
                  {ex}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="mt-6 grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
          {apps.map((app) => (
            <AppTile key={app.id} app={app} onOpen={() => setOpenId(app.id)} />
          ))}
        </div>
      </div>
    </div>
  )
}

function AppTile({ app, onOpen }: { app: GenerativeApp; onOpen: () => void }) {
  const icon = useMemo(() => appIcon(app.title), [app.title])
  return (
    <button
      onClick={onOpen}
      className="group flex flex-col items-center gap-2 p-3 rounded-xl hover:bg-gray-100 dark:hover:bg-white/[0.04] transition-colors"
      title={app.title}
    >
      <div
        className="relative w-16 h-16 rounded-2xl flex items-center justify-center text-2xl font-bold text-white shadow-md"
        style={{ background: `linear-gradient(135deg, ${icon.from}, ${icon.to})` }}
      >
        {icon.letter}
        {app.status === 'generating' && (
          <span className="absolute -bottom-1 -right-1 p-1 rounded-full bg-white dark:bg-[#1e1e1e] text-gray-700 dark:text-gray-200">
            <Loader2 size={12} className="animate-spin" />
          </span>
        )}
        {app.status === 'error' && (
          <span className="absolute -bottom-1 -right-1 p-1 rounded-full bg-white dark:bg-[#1e1e1e] text-red-500">
            <TriangleAlert size={12} />
          </span>
        )}
      </div>
      <span className="text-xs text-gray-800 dark:text-gray-200 text-center line-clamp-2 break-words w-full">{app.title}</span>
    </button>
  )
}

function AppRunner({ app, onBack }: { app: GenerativeApp; onBack: () => void }) {
  const regenerate = useGenerativeAppStore((s) => s.regenerateApp)
  const cancel = useGenerativeAppStore((s) => s.cancelGeneration)
  const remove = useGenerativeAppStore((s) => s.deleteApp)
  const doc = useMemo(() => buildAppBrowserDocument(app.html), [app.html])
  const generating = app.status === 'generating'
  const [openError, setOpenError] = useState<string | null>(null)

  const openInBrowser = async () => {
    setOpenError(null)
    try {
      await openGeneratedAppDocument(doc)
    } catch (err) {
      setOpenError(err instanceof Error ? err.message : 'Could not open the app in your browser.')
    }
  }

  const iconBtn =
    'p-1.5 rounded-md text-gray-500 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-white/[0.06] hover:text-gray-700 dark:hover:text-white transition-colors disabled:opacity-30'

  return (
    <div className="h-full flex flex-col">
      <div className="flex items-center gap-2 px-3 py-2 border-b border-gray-200 dark:border-white/10">
        <button onClick={onBack} className={iconBtn} aria-label="Back to apps" title="Back to apps">
          <ArrowLeft size={14} />
        </button>
        <span className="text-sm font-medium text-gray-900 dark:text-white truncate">{app.title}</span>
        {generating && (
          <span className="flex items-center gap-1 text-[0.65rem] text-gray-500 dark:text-gray-400">
            <Loader2 size={11} className="animate-spin" /> building…
          </span>
        )}
        <div className="ml-auto flex items-center gap-1">
          {generating ? (
            <button onClick={() => cancel(app.id)} className={iconBtn} aria-label="Stop" title="Stop">
              <Square size={13} />
            </button>
          ) : (
            <button onClick={() => regenerate(app.id)} className={iconBtn} aria-label="Regenerate" title="Regenerate">
              <RotateCw size={14} />
            </button>
          )}
          <button onClick={openInBrowser} disabled={app.status !== 'ready'} className={iconBtn} aria-label="Open in browser" title="Open in browser">
            <ExternalLink size={14} />
          </button>
          <button
            onClick={() => { remove(app.id); onBack() }}
            className={iconBtn}
            aria-label="Delete app"
            title="Delete app"
          >
            <Trash2 size={14} />
          </button>
        </div>
      </div>

      {(app.status === 'error' || openError) && (
        <div role="alert" className="px-3 py-2 text-xs text-red-700 bg-red-50 dark:text-red-300 dark:bg-red-950/30">
          {openError || app.error || 'Generation failed. Regenerate to try again.'}
        </div>
      )}
      <div className="flex-1 relative bg-white">
        {doc ? (
          <iframe
            srcDoc={doc}
            sandbox="allow-scripts"
            referrerPolicy="no-referrer"
            title={app.title}
            className="absolute inset-0 w-full h-full border-0"
          />
        ) : (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-white dark:bg-[#1e1e1e] text-sm text-gray-500 dark:text-gray-400">
            {app.status === 'error' ? (
              <>
                <TriangleAlert size={20} className="text-red-500" />
                <p className="max-w-sm text-center">{app.error || 'Something went wrong.'}</p>
                <button
                  onClick={() => regenerate(app.id)}
                  className="mt-1 px-3 py-1.5 rounded-lg text-xs bg-gray-900 text-white dark:bg-white dark:text-gray-900"
                >
                  Try again
                </button>
              </>
            ) : (
              <>
                <Loader2 size={20} className="animate-spin" />
                <p>Generating {app.title}…</p>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
