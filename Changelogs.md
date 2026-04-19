# Changelog

All notable changes to the **Mulya** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Multi-Profile Support**: Ability to create, switch, and manage multiple budget profiles (e.g., Personal, Business).
- **Savings Goals**: Added a feature to set and track savings goals. Adding money to a goal automatically updates the overall balance and creates a transaction record.
- **Recurring Transactions**: Support for automated, recurring transactions with customizable frequencies.
- **Category Management**: Categories can now be edited, added, or deleted directly from the custom transaction keypad dialog.
- **Backup & Restore**: Completely offline JSON-based backup and restore system added to the settings menu.
- **Dashboard Enhancements**: Added an "Upcoming Actions" summary card directly on the main dashboard for quick access.

### Changed
- **Navigation Redesign**: Replaced the dedicated "Future Plans" page with bottom-sheet based "Upcoming Actions" accessible from the dashboard.
- **History Shortcut**: Added a quick-access History icon to the top right of the main app bar.
- **Side Drawer**: Updated side drawer navigation to prioritize Transaction History instead of Plans.
- **Dark Theme Improvements**: Adjusted contrast for donut chart legends and history date headers to improve readability in dark mode.

### Fixed
- Resolved app crashes when navigating between History and Profile pages.
- Fixed a bug where creating a new profile would unintentionally switch the active profile context incorrectly.
- Fixed an overlapping UI issue on the top screen involving the menu icon.
- Fixed unresolved reference and compilation errors in the Kotlin backend.

---

## [1.0.0] - Initial Release

### Added
- Basic offline expense and income tracking.
- Room database implementation for local storage.
- Custom numeric keypad for fast transaction entry.
- Visual dashboard with sparklines and donut charts.
- Export functionality for CSV and PDF.
- Dark mode compatibility.
- User profile creation.
