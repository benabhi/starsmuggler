# Changelog

Every version of Star Smuggler for Android, newest first, following [semantic versioning](https://semver.org).
A minor version closes a plan (SMUG-PLAN-N) and a patch is any other task approved in between.
Each line names its Lific issue.

This file is generated from git by `scripts\changelog.ps1`: do not edit it by hand.

## [0.2.6] - 2026-11-03

### Changed

- Add .gitattributes so line endings stay LF and git stops warning (SMUG-82)
- Keep release.ps1 going when git writes warnings to stderr (SMUG-82)

## [0.2.5] - 2026-11-03

### Added

- Add three save slots with atomic autosave and a versioned save format (SMUG-23)

## [0.2.4] - 2026-11-03

### Added

- Add the ship loan: weekly interest, default, repossession and principal payments (SMUG-20)

## [0.2.3] - 2026-11-03

### Added

- Create Duke, his ship and starting cash, and add Astucia and its roll (SMUG-21)

## [0.2.2] - 2026-11-03

### Added

- Add the day clock with overtime, end of day and end of week (SMUG-19)

## [0.2.1] - 2026-11-03

### Added

- Add game state, actions, log and seeded dice to the engine (SMUG-18)

## [0.2.0] - 2026-11-03

### Changed

- Add content validator and link only references that exist (SMUG-16)

## [0.1.6] - 2026-11-03

### Added

- Transcribe the twelve map tiles with their seams and scans (SMUG-11)

## [0.1.5] - 2026-11-03

### Fixed

- Fit text tables to the screen width (SMUG-81)

## [0.1.4] - 2026-11-03

### Added

- Add Spanish rule and event texts with event durations (SMUG-13)
- Size text table columns to their content and split column captions (SMUG-13)

## [0.1.3] - 2026-11-02

### Added

- Add game tables, rule paragraphs, equipment and goods as data (SMUG-12)

## [0.1.2] - 2026-11-02

### Added

- Add the ten Pavonis systems and the standard jump pattern (SMUG-10)
- Stack long planet and jump values on the content screen (SMUG-10)

## [0.1.1] - 2026-11-02

### Added

- Add content data model, JSON loader and debug content screen (SMUG-9)

## [0.1.0] - 2026-11-02

### Added

- Add adaptive launcher icon from the cover logo (SMUG-7)

## [0.0.5] - 2026-11-02

### Changed

- Add Spanish text rules, i18n tests and locale formats (SMUG-6)

## [0.0.4] - 2026-11-02

### Added

- Add navigable main screen mock-up with fake data (SMUG-5)

## [0.0.3] - 2026-11-02

### Added

- Add retro paper theme, fonts and base components (SMUG-4)

## [0.0.2] - 2026-11-02

### Changed

- Add run, release and changelog scripts (SMUG-3)

## [0.0.1] - 2026-11-02

### Changed

- Initial project setup: CLAUDE.md, gitignore, Lific MCP config
- Document image generation workflow with Gemini
- Add multi-module Android project skeleton (SMUG-2)
