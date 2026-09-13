# Changelog

All notable changes to this plugin are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[SemVer](https://semver.org/).

## [0.1.0]

First release. Brings Visual Studio's LibMan experience to Rider, with an inline update
view over `libman.json`.

### Added
- **Inline updates**: opening `libman.json` force-checks each library for updates (cancelled if
  the file is closed; results cached with a configurable expiry). The version gets an amber
  highlight when a newer version exists.
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
- **Missing-CLI warning**: opening a `libman.json` warns once if the `libman` CLI is not
  installed, with the install command and a shortcut to settings (to set a custom path).
- **Install LibMan CLI** button in settings: runs `dotnet tool install -g` (no project needed),
  disabled when the CLI is already found.
- CLI, network, and provider errors are shown as notifications with a **Copy Details**
  action, never as plugin crashes.

### Planned
- **Enable/Disable Restore on Build** context action (toggles the
  `Microsoft.Web.LibraryManager.Build` package on the owning project).
