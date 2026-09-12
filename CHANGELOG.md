# Changelog

All notable changes to this plugin are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[SemVer](https://semver.org/).

## [0.1.0]

First release. Brings Visual Studio's LibMan experience to Rider by wrapping the
`libman` CLI.

### Added
- Editor intentions (Alt+Enter) on a library entry in `libman.json`:
  - **Check for updates** — shows the latest version, or "up to date".
  - **Update to latest** and **Update to latest prerelease**.
  - **Uninstall** the library.
- Context-menu actions on `libman.json` (Solution Explorer + editor):
  **Restore**, **Clean**, and **Manage** (open the manifest).
- CLI errors and missing-tool cases are shown as notifications with a
  **Copy Details** action, never as plugin crashes.

### Planned
- **Enable/Disable Restore on Build** context action (toggles the
  `Microsoft.Web.LibraryManager.Build` package on the owning project).
