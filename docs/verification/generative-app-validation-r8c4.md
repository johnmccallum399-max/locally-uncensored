# Generated app validation — R8C4

2026-10-26. Follow-up to draft PR #5, based on `7e97117`.

## Changes

- Extraction returns empty for prose/refusals without a document start.
- Ready requires provider-stream completion and an explicitly closed HTML/body document. Optional head, raw-text elements, comments, quoted attributes and templates are accounted for. This is a completion check, not proof of HTML conformance, valid JavaScript or useful application behavior.
- Partial HTML remains visible during generation and after failures. Stop/reload cannot promote interrupted generations to ready; persisted ready documents are revalidated. Aborted old streams cannot overwrite regeneration results. Errors remain visible above partial previews.
- CSP precedes all generated markup. A trusted wrapper embeds the output in an opaque `sandbox="allow-scripts"` iframe, with parent `frame-src about:` intended to restrict child navigation. No same-origin, forms, popups, downloads or top-navigation permissions were added.
- Open in browser is available only for ready apps and retains the wrapper. Web uses a temporary blob and clears the opener. Desktop invokes the existing shell command directly so permission failures reach the error banner. Shell permissions remain unchanged; default shell scope may reject HTML data URLs. Native HTML-file export/opening is not implemented.

## Executed verification

- Test-first reproductions: prose promoted to ready, truncated final response, missing completion chunk, cancellation/reload promotion, stale stream overwrite, policy insertion after script/comment, and raw-text/template false completion. Each was observed failing before its correction.
- 56 focused tests pass, including stream/persistence fixtures and mocked browser/native opening boundaries.
- Full Vitest suite: **2,815 tests, 126 files, zero failures**.
- ESLint passes for all seven changed TypeScript/TSX files; `git diff --check` passes.
- `vite build` passes, with existing chunk-size/dynamic-import warnings.
- `tsc -b` reports 446 errors; none name these changed files. Existing repository typing/CI cleanup remains separate. Rust checks were not rerun for this frontend-only correction.
- Independent source review identified raw-text/template completion gaps and swallowed desktop-opening failures; both now have regressions and fixes.

## Execution limits

Actual LCDR browser/model interaction remains **unverified**. The cloud browser could not reach the workspace server (`ERR_CONNECTION_REFUSED`). A standalone data-URL fixture was then rejected by the browser URL policy; no bypass was attempted. No browser security or UI behavior is claimed as runtime-verified. Workspace Ollama at localhost:11434 was unavailable, and the Ollama executable was absent. Provider interactions above are fixtures, not live inference.

The OpenAI/Anthropic adapters currently synthesize `done:true` on transport EOF. The store honors that existing provider contract; it does not prove an upstream stop reason. Structural validation independently catches ordinary document truncation. Provider integrations were not changed.

## Remaining manual checks

1. In a connected LCDR environment, select a real model, generate Calculator, confirm progressive preview and button behavior, then reload and confirm persistence.
2. Return prose, truncated HTML and a dropped stream from a controlled provider; verify errors, preserved partial preview, disabled external opening, Stop, reload and regeneration.
3. In the actual browser/WebView, test blocked fetch/external image/form, parent access, popup, top navigation and child self-navigation. Verify legitimate inline scripts/CSS still work.
4. Test web opening, popup rejection and desktop shell rejection. An accepting system opener must retain the sandbox. Default desktop scope may instead show the documented error; do not widen shell permissions to silence it.

No merge or deployment performed. PR remains draft.
