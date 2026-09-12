# AGENTS.md - LibMan for Rider

Context and rules for anyone (human or AI) working in this repo.

## What this is

A **JetBrains Rider plugin** that replicates Visual Studio's LibMan experience for `libman.json`:
editor intentions (check for updates / update / uninstall) and context-menu actions (restore /
clean / manage / enable restore-on-build). It wraps the official
[`libman`](https://learn.microsoft.com/aspnet/core/client-side/libman/libman-cli) CLI.

- Plugin id: `com.github.iamr8.libman` - name **LibMan GUI** - group `com.github.iamr8`.

## Toolchain & build

- **Kotlin** `2.3.0`, IntelliJ Platform Gradle Plugin `2.11.0`, target **Rider**.
- Kotlin version must match Rider's bundled Kotlin metadata (`2.3.0` for build 261+).
- Platform dependency: `local("/Applications/Rider.app")` when present, else `rider("2026.1.4")`.
- JDK: platform bytecode needs `--release 21` (jvmTarget 21). Uses `-jvm-default=no-compatibility`.
- **VERSION** file (no extension) is the single source of truth; read into the plugin version.
- `sinceBuild = "243"` (Rider 2024.3, oldest on JBR 21). `untilBuild` unset.

### Verification policy

Do **not** rely on a local Gradle run. Verification happens in **CI** (`.github/workflows/build.yml`
runs `test`, `verifyPluginProjectConfiguration`, `verifyPluginStructure`, `buildPlugin`, and the
Plugin Verifier against the current Rider). Open a PR and read its checks.

## Architecture

```
cli/     LibmanLocator (find libman), LibmanCommand (pure arg builders), LibmanRunner (process),
         WhatIfParser (--whatif output -> result), CliFailures (CLI output -> user message)
model/   Manifest / LibraryEntry (Gson DTOs), LibraryId (parse name@version), Severity (color)
parse/   ManifestParser (JSON -> model)
ui/      intentions (Alt+Enter on a library entry) + actions (context menu) + notifications
```

### Key behaviors

- **Check for updates** = `libman update <lib> --whatif` (read-only; prints the target version or
  "up to date"). `--pre` for prerelease. Updates run `libman update <lib>`.
- Each command runs with **working directory = the manifest's own folder** (the CLI finds
  `libman.json` by cwd). Stdin is closed to avoid the rare interactive provider prompt.
- **Library id parsing**: handle `name@version`, scoped npm `@scope/name@version`, `@latest`, and
  the `filesystem` provider (a path, no version - intentions skip it).
- **Error routing**: user/environment failures (missing CLI, network, provider errors, non-zero
  exit) -> notification balloon + **Copy Details** + `LOG.warn`. Plugin bugs -> `LOG.error` ->
  IDE error reporter (Marketplace Exception Analyzer).

## Testing policy

Every functional change needs a test where practical. Pure logic (id parsing, arg builders,
whatif parsing, severity, manifest parsing) is unit-tested (JUnit4).

## Conventions

- **Commits**: Conventional Commits. No AI/tool attribution in commit messages, PRs, or any
  user-facing text.
- **Changelog**: user-facing changes go under the current in-progress version in `CHANGELOG.md`.
- License: **MIT**.
- Keep logic pure and small; put testable code where it can be unit-tested.
