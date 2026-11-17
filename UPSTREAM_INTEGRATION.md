# Integration of Beta3 fixes into original Pele Shaon

This branch is based on the independent Beta3 development branch, with original-package identity restored for upstream review.

- Target: `TeacherRG/Peleshaon:main`
- Source: `bystrzyca-cloud/Peleshaon:integration/upstream-beta3`
- Kept: Kotlin fixes for alarm reliability, halachic time computations, calendar transitions, localization, moon reminders, Wear OS standalone configuration, Android lifecycle/performance, regression tests.
- Restored: original `applicationId = app.mychitas.peleshaon` in both APK modules; user-visible original name in all five locales; resource-based manifest labels.
- Version: `versionCode = 2` to upgrade original `versionCode = 1`; `versionName = 1.0.0-beta3` identifies this as a **pre-release candidate**, not a final certified release.
- CI: `pull_request`, upstream `main` and `ccr-*` push triggers preserved. Same core unit tests, translation checks, Android/Wear APK packaging, widgets and no-INTERNET checks.
- Build signing: the repository's **existing public debug.keystore** is retained for reproducible debugging only, **not suitable for a trusted release**.
- Phone and Wear configuration: Wear has independent city/Minhag selectors; it does **not** sync preferences with the phone.
- Halakhic rules: some communal/candle/Kiddush Levana choices require qualified review; no claim of certification.
- Before publication: real-device tests from `QA_DEVICE_MATRIX.md`; verify old original app upgrades without data loss; configure private release signing; review Android exact-alarm and full-screen policies.

**Integration is a code merge, not approval to publish a production release.** Do not copy Beta3's `.beta` package identity to upstream.
