# Desktop CI audit · C8R4 · 2026-10-25

## Finding

PR #3 does not introduce the inspected frontend/Rust CI failures. Base `e45b96fd9ccd5a2e3df6a40e27807b61501bcaee` and PR head `24b476c78bfea99c9e47c455fdf349b182f7b864` have identical web/Rust sources, lockfiles, ESLint/Vite/TypeScript configuration and desktop CI workflow. The PR changes only Android files and its dedicated workflow.

Local checks on both revisions produced identical ESLint diagnostics: **665 errors, 17 warnings across 134 files**. Both frontend production builds succeeded. Real TypeScript project checks produced the same **446 errors** on both revisions. These are inherited failures, not Android regressions.

## Original hosted failures

[CI run 39829223200](https://github.com/johnmccallum399-max/locally-uncensored/actions/runs/39829223200) checked merge revision `a38d1dd6eae380828dabbe6692f8ea777e2f1275`:

- Job `120000600386`: `npm run lint` failed with 665 errors / 17 warnings. Type check and frontend build were skipped.
- Job `120000600528`: Linux `cargo check` failed in `tauri_build::build()` with `resource path ../dist doesn't exist`.
- Windows job `120000600735` was cancelled; it did not establish an independent Windows compile failure.
- No workflow runs were returned for the base SHA by the connected API. The local base reproduction and byte-identical Rust inputs establish inheritance; a historical successful/failed base Rust run is not claimed.

## Why dist is missing

Vite's `npm run build` creates root `dist/`. Both Tauri `build.frontendDist` and `bundle.resources` reference `../dist` relative to `src-tauri`. The build script calls `tauri_build::build()`. Direct `cargo check` does not invoke the Tauri CLI's `beforeBuildCommand`.

Originally the frontend and Rust jobs were independent fresh checkouts, with neither an artifact transfer nor a frontend build in the Rust jobs. Even a successful frontend job would not populate their filesystems. An empty directory or removal of the resource declaration would hide the missing prerequisite rather than validate a real frontend. This correction retains the existing Tauri configuration and packaging behavior.

The workflow now builds the frontend in its own job, uploads the real output, and makes the Rust matrix depend on that job and download the artifact to root `dist/`. Lint/type failures remain visible without preventing independent Rust diagnosis. Matrix fail-fast is disabled so Linux cannot cancel Windows. Cargo uses the committed lockfile.

## ESLint classification and correction order

| Errors | Rule / cause | Manageable correction |
| ---: | --- | --- |
| 505 | `no-explicit-any`: broad API, stream, AST, middleware and fixture typing | Work by subsystem; introduce shared provider/protocol/fixture types. Use `unknown` with narrowing at external boundaries, not blind `any` replacement or casts. |
| 49 | `no-unused-vars`: stale imports, locals, parameters | Remove truly unused bindings in small patches. Preserve initializer side effects; do not assume underscore names are exempt. |
| 47 | `no-require-imports`: CommonJS calls in an ESM project | Use static ESM imports or awaited imports as appropriate; preserve mock isolation/hoisting and Node-only boundaries. |
| 24 | `no-empty`: silent catch/control blocks | Decide whether recovery is intentional. Document justified best-effort cleanup locally; otherwise handle/report failure. Do not add meaningless catch logging everywhere. |
| 18 | React hooks: 9 purity, 8 synchronous set-state-in-effect, 1 use-memo | Review lifecycle behavior per component. Render-time clocks/randomness and derived state need different remedies. Particle randomness inside `useMemo` still triggers purity checking. `useMemory` wraps a module-level function in `useCallback`; assess removing that redundant wrapper. |
| 5 | React refresh export boundaries | Split shared constants/helpers from component exports where appropriate. |
| 17 | Other: 7 useless escapes, 4 prefer-const, 2 optional-chain non-null assertions, one each control-regex / ts-comment / useless-catch / case-declaration | Review individually. The control-character regex is in a test and may be intentional; do not delete its test coverage to satisfy lint. |

Warnings: 13 hook dependency warnings and 4 unused disable directives. Test files account for 150 diagnostics; this is not solely a test-configuration issue. The current broad recommended flat configs apply across TypeScript application, tests and Vite config. No dependency-version regression is established: their lockfile/configuration are identical between base and PR.

Start with a reviewed dead-binding/style patch, then typed test fixtures and provider boundaries in separate patches, and lifecycle-sensitive hook fixes with focused behavior tests. Keep the full lint result failing until the debt is corrected; do not disable whole rules, exclude tests wholesale, or add hundreds of suppression comments. If a no-new-debt gate is later desired, propose it separately with an explicit diagnostic baseline and deletion policy; this patch adds none.

## Previously ineffective TypeScript gate

The root `tsconfig.json` has `files: []` and project references. `tsc --noEmit` reports success without checking those referenced projects. `tsc -b --pretty false` checks them and exposes 446 inherited errors on both revisions, including missing Vitest globals, implicit parameter types, stale fixtures, undeclared Tauri window fields, and strict unused-binding diagnostics. Runtime Vitest globals do not supply TypeScript ambient declarations automatically. Address test typing separately from application/Vite errors; do not turn off strict checking.

The workflow now runs the real project check even after lint fails. The existing `lint-and-build` job ID/display name is preserved for status-check compatibility; the frontend artifact producer has a new job ID. Repository branch-protection requirements were not inspected or altered.

## Validation and limits

- Installed the locked npm dependencies with `npm ci --ignore-scripts --no-audit --no-fund`; actual Vite builds succeeded on base and PR head. Local Node **24.22.1**, npm **11.9.0**; hosted CI uses `.nvmrc` **22**. Exact hosted toolchain equivalence is not claimed.
- Base/head ESLint JSON messages match exactly (including file, position, rule and message). Both return failure; no diagnostics were suppressed.
- Base/head real TypeScript diagnostic lines match exactly: 446 errors. The base rerun used `--force` to avoid incremental-cache ambiguity.
- Original workflow prerequisite assertion failed before the edit. Updated workflow structure, dependency/artifact ordering, real `dist/index.html` presence and `git diff --check` were verified afterward.
- This host has no Cargo/Rust toolchain or actionlint. Full Rust compilation and hosted artifact transfer require the follow-up Actions run; further compiler errors may emerge once the missing resource is supplied. No claim that Rust CI is fully green.
- Only the desktop CI workflow and this audit change. No Android source, voice behavior, Android verification workflow, dependency lockfile, Tauri packaging configuration or release workflow is changed. No Android rebuild, installation, deployment or merge was performed.
