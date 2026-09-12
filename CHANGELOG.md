# Changelog

All notable changes to this plugin are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[SemVer](https://semver.org/).

## [0.1.0]

First release. Brings Visual Studio's LibMan experience to Rider, with an inline update
view over `libman.json`.

### Added
- **Inline updates**: opening `libman.json` checks each library for updates (cached, 1-hour
  expiry). The version is highlighted by the biggest available jump - green (patch),
  yellow (minor), red (major or pre-release).
- **Clickable version chips** above each library line - one per available update
  (patch / minor / major / pre-release); click to install. A **Check for updates** chip
  re-checks and refreshes the cache on demand.
- **Library description** from the provider (up to 3 lines, truncated) with a link to the
  library's provider page.
- **Providers**: cdnjs, unpkg (npm), and jsDelivr (npm + GitHub).
- **Context-menu actions** on `libman.json` (Solution Explorer + editor): **Restore**,
  **Clean**, **Manage**. **Uninstall** on Alt+Enter.
- CLI, network, and provider errors are shown as notifications with a **Copy Details**
  action, never as plugin crashes.

### Planned
- **Enable/Disable Restore on Build** context action (toggles the
  `Microsoft.Web.LibraryManager.Build` package on the owning project).
