# LibMan for Rider

Bring Visual Studio's **Library Manager (LibMan)** experience to JetBrains Rider. Manage
client-side libraries in `libman.json` without leaving the editor: check for updates, update to
the latest version, uninstall, restore, and clean - all from intentions and context menus, backed
by the official [`libman`](https://learn.microsoft.com/aspnet/core/client-side/libman/libman-cli)
CLI.

Rider has no built-in LibMan support (see the long-standing request
[RIDER-15368](https://youtrack.jetbrains.com/issue/RIDER-15368)). This plugin fills that gap.

## Features

- **Editor intentions** (Alt+Enter) on a library entry in `libman.json`:
  - **Check for updates** - shows the latest version, or "up to date".
  - **Update to latest** / **Update to latest prerelease**.
  - **Uninstall** the library (removes files and the manifest entry).
- **Context-menu actions** on `libman.json` (Solution Explorer and editor):
  - **Restore Client-Side Libraries**
  - **Clean Client-Side Libraries**
  - **Manage Client-Side Libraries** (open the manifest)
- CLI failures (missing tool, network, provider errors) are shown as notifications with a
  **Copy Details** action - not as plugin crashes.

Planned: an **Enable / Disable Restore on Build** context action.

## Requirements

- JetBrains Rider 2024.3 or newer.
- .NET SDK on `PATH`.
- The LibMan CLI:

  ```bash
  dotnet tool install -g Microsoft.Web.LibraryManager.Cli
  ```

## How it works

The plugin shells out to `libman`. "Check for updates" runs `libman update <lib> --whatif`
(read-only); updates run `libman update`; restore/clean/uninstall map to the matching commands.
Each command runs in the manifest's own directory, so multiple `libman.json` files in one solution
are handled independently.

## Building

Tests and the plugin build run in CI (see `.github/workflows/build.yml`). Locally, if you have a
JDK and the Gradle wrapper:

```bash
./gradlew test          # pure logic: id parsing, arg builders, whatif parser, severity
./gradlew buildPlugin   # produces build/distributions/*.zip
./gradlew runIde        # sandbox Rider with the plugin
```

## License

[MIT](LICENSE).
