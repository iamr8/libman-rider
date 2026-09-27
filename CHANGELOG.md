# Changelog

All notable changes to this plugin are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[SemVer](https://semver.org/).

## [Unreleased]

### Added
- **Completion in `libman.json`**: library names that start with the typed text (from 3 letters),
  from the provider's search (cdnjs, or npm for unpkg / jsDelivr); the 10 newest versions after `@`,
  pre-releases included; `destination` / `defaultDestination` folders under the manifest's folder;
  and the library version's files in `files`.
- A warning on a `files` entry that the library version does not have (glob patterns are not
  checked).

### Fixed
- Two errors in the IDE log at every start: the `libman.json` file type, and the menu entry in
  the Solution Explorer context menu.

### Changed
- The plugin now loads only in Rider.
- A failed update check (network error, provider error, library not found) now shows as
  **Check failed. Retry** in the action row, with the reason on hover. Before, it looked the same
  as "no update".
- Repeat update checks for npm (unpkg) and jsDelivr libraries are conditional (`ETag`). An
  unchanged version list is not downloaded again. cdnjs sends no `ETag`, so it still sends the
  full list.
- Opening `libman.json` checks up to 4 libraries at a time, not one by one, so a large manifest
  shows its updates sooner. Closing the file still cancels the check.
- **Update to X** and **Remove** now queue the change instead of running `libman` at once. A
  banner on `libman.json` shows the pending changes, with **Apply now** and **Discard**. The queue
  runs in one pass on an explicit save (Ctrl+S / Save All, not auto-save) or when the file closes.
  **Undo** in the action row drops one queued change.

## [0.1.0]

First release. Brings Visual Studio's LibMan experience to Rider, with an inline update
view over `libman.json`.

### Added
- **Inline updates**: opening `libman.json` force-checks each library for updates (cancelled if
  the file is closed; results cached with a configurable expiry). The version gets an amber
  highlight when a newer version exists.
- **Cancellable checks**: every update check - on file open and per library - runs as a background
  task in Rider's Background Tasks pane and can be cancelled mid-download. The editor view renders
  from the cache only, so it never blocks the highlighting thread on the network.
- **Action row** above each library line: **Check for updates**, one **Update to X** per
  available version (patch / minor / major / pre-release), and **Remove** (runs
  `libman uninstall` after a confirm). Each link has an icon and a hand cursor.
- **Description tooltip** on the library name, from the provider, with a link to the library's
  provider page.
- **Manifest checks**: a warning on an unknown provider, and a hint when a newer `libman.json`
  schema version is available.
- **Providers**: cdnjs, unpkg (npm), and jsDelivr (npm + GitHub).
- **Settings** (Settings | Tools | LibMan): include pre-releases, check-on-open, cache lifetime,
  a custom `libman` executable path, and CLI output verbosity.
- **Context-menu actions** on `libman.json` (Solution Explorer + editor): **Restore**,
  **Clean**, **Manage**.
- **Plugin suggestion**: the IDE suggests this plugin when a `libman.json` is opened.
- **Live progress**: a running action streams the `libman` output into the background progress
  indicator; the outcome is still shown as a notification.
- **No double-runs**: a second action for a library is ignored while one is already running, so a
  double-click can't run `libman` twice.
- **Missing-CLI warning**: opening a `libman.json` warns once if the `libman` CLI is not
  installed, with the install command and a shortcut to settings (to set a custom path).
- **Install LibMan CLI** button in settings: runs `dotnet tool install -g` (no project needed),
  disabled when the CLI is already found.
- CLI, network, and provider errors are shown as notifications with a **Copy Details**
  action, never as plugin crashes.

### Planned
- **Enable/Disable Restore on Build** context action (toggles the
  `Microsoft.Web.LibraryManager.Build` package on the owning project).
