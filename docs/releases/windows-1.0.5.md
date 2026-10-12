# Anchor Windows 1.0.5

Public release: October 11, 2026. Windows x64.

## Changes

This release includes the [Windows 1.0.4 improvements](windows-1.0.4.md): awake-time focus timers with paused recovery, durable focus credits, atomic task changes and NOW ordering, exact timer durations, retained drafts, compact layouts, validated backups and single-instance recovery.

- Removed developer-specific courses, timetables, saved leisure links, blocking defaults and local document paths. Users add their own academic data and protection settings.
- Scope classification follows the current task across academic subjects. Local model decisions are cached per task; explicit user classifications remain authoritative.
- Document integration tests generate fictional PDFs, scanned PDFs and DOCX tables. Public source and compiled application payloads are screened before publication.
- Version 1.0.5 identifies the sanitized Windows distribution.

## Install

Choose the EXE installer or MSI package from this release. Both include the Windows x64 Java runtime and Java Access Bridge. Local AI requires a compatible GGUF model and llama-server executable configured in Settings; Windows installers do not bundle those weights or runtime.

Existing local user data is retained. These installers are unsigned. App blocking and scope classification are experimental; review the protection settings you enable.

## Verification

All 323 desktop, 8 shared and 223 Android unit tests passed. The Windows application image, MSI and EXE were rebuilt from sanitized source. Packaged application and shared JAR hashes match the source build, the Access Bridge runtime check passes, and the application payload privacy screen passes. SHA-256 checksums are provided as a release asset.

Earlier isolated native checks exercised workflows at 900 by 600 and 640 by 450 dp. Physical sleep/hibernation, actual screen-reader interaction, device-specific blocking and every display scaling configuration remain unverified.
