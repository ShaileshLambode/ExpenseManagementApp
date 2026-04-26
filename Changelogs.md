# Changelog

All notable changes to the **Mulya** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.26] - 2026-04-26

### Added
- **Interactive Trend Charts**: Touch and swipe support on Sparkline and Donut charts to see exact values and dates.
- **Smart Recycle Bin**: 30-day safety net for deleted transactions, plans, and goals with automatic balance restoration.
- **Review & Feedback Portal**: New guided feedback screen within the app with Google Form integration.
- **"What's New" Page**: Dedicated screen to highlight latest updates and major features.
- **Multi-Profile Support**: Ability to create, switch, and manage multiple budget profiles (e.g., Personal, Business).
- **Savings Goals**: Track savings goals with automated balance updates and transaction records.
- **Recurring Transactions**: Support for Daily, Weekly, and Monthly automated transactions.
- **Backup & Restore**: Completely offline JSON-based system with foreign key safety and self-healing profile recovery.

### Changed
- **UI Simplification**: Removed manual color selection for budget profiles to streamline the creation process.
- **Navigation Redesign**: Updated side drawer and bottom navigation for better accessibility of core features.
- **History Enhancements**: Quick-access History icon in the top app bar and improved date grouping.
- **Dark Theme Improvements**: Optimized contrast for charts and headers across the entire application.

### Fixed
- **Restore Reliability**: Fixed foreign key constraint errors during full database restoration.
- **Profile Recovery**: Fixed issues where transactions from old backups would lose their budget context.
- **Navigation Crashes**: Resolved lifecycle-related crashes when switching between History and Profile.
- **UI Overlaps**: Fixed menu icon and toolbar title overlapping issues.

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
