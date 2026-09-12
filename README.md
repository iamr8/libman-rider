# LibMan for Rider

Bring Visual Studio's **Library Manager (LibMan)** experience to JetBrains Rider. Manage
client-side libraries in `libman.json` without leaving the editor: check for updates, update to
the latest version, uninstall, restore, and clean - all from intentions and context menus, backed
by the official [`libman`](https://learn.microsoft.com/aspnet/core/client-side/libman/libman-cli)
CLI.

Rider has no built-in LibMan support (see the long-standing request
[RIDER-15368](https://youtrack.jetbrains.com/issue/RIDER-15368)). This plugin fills that gap.

## Features

Open a `libman.json` and every library is checked for updates (results are cached). For each
library, right in the editor:

- **Colored version highlight** - green for a patch update, yellow for a minor, red for a major
  or pre-release.
- **Clickable version chips above the line** - one per available update
  (patch / minor / major / pre-release); click to install it. A **Check for updates** chip
  re-checks and refreshes the cache on demand.
- **Library description** from the provider (up to 3 lines, truncated) with a link to the
  library's page.

Providers supported: **cdnjs**, **unpkg** (npm), and **jsDelivr** (npm + GitHub).

Also:

- **Context-menu actions** on `libman.json` (Solution Explorer and editor):
  **Restore**, **Clean**, **Manage**.
- **Uninstall** a library on Alt+Enter.
- CLI, network, and provider errors are shown as notifications with a **Copy Details**
  action - not as plugin crashes.

Planned: an **Enable / Disable Restore on Build** context action.

## Requirements

- JetBrains Rider 2024.3 or newer.
- .NET SDK on `PATH`.
- The LibMan CLI:

  ```bash
  dotnet tool install -g Microsoft.Web.LibraryManager.Cli
  ```

## How it works

Available versions and the description come from the provider's public API - cdnjs
(`api.cdnjs.com`), npm (`registry.npmjs.org`, for unpkg and npm-form jsDelivr), and the jsDelivr
data API (for GitHub-form jsDelivr). Lookups are cached per project with a 1-hour expiry; the
**Check for updates** chip forces a refresh. Installing a version, restore, clean, and uninstall
shell out to the `libman` CLI, run in the manifest's own directory - so multiple `libman.json`
files in one solution are handled independently.

## Building

Tests and the plugin build run in CI (see `.github/workflows/build.yml`). Locally, if you have a
JDK and the Gradle wrapper:

```bash
./gradlew test          # pure logic: id parsing, semver, update buckets, catalog parsers
./gradlew buildPlugin   # produces build/distributions/*.zip
./gradlew runIde        # sandbox Rider with the plugin
```

## License

[MIT](LICENSE).
