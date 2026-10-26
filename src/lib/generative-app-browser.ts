import { isTauri } from '../api/backend'

/** Open only a trusted sandbox wrapper, never raw model output. */
export async function openGeneratedAppDocument(doc: string): Promise<void> {
  if (isTauri()) {
    // The generic opener silently falls back to window.open on shell failure.
    // Keep native permission failures observable; never widen shell URL scope.
    try {
      const { invoke } = await import('@tauri-apps/api/core')
      await invoke('plugin:shell|open', {
        path: `data:text/html;charset=utf-8;base64,${btoa(unescape(encodeURIComponent(doc)))}`,
      })
    } catch {
      throw new Error('The desktop shell could not open this generated app. Use the in-app preview; HTML data URLs may be blocked.')
    }
    return
  }
  const url = URL.createObjectURL(new Blob([doc], { type: 'text/html;charset=utf-8' }))
  const popup = window.open('about:blank', '_blank')
  if (!popup) {
    URL.revokeObjectURL(url)
    throw new Error('The browser blocked the new tab. Allow popups for this app and try again.')
  }
  popup.opener = null
  popup.location.replace(url)
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}
