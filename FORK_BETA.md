# Peleshaon Beta3 — independent Android / Wear test build

Source branch: `fixes/independent-beta`; independent package `app.mychitas.peleshaon.beta` on phone and watch.

Implemented from the neighboring and in-chat audits: shared community-specific Shma/havdalah; Yom-Tov→Shabbat candle transition; city-specific candle offset plus user override; leap-year Adar naming; changed-city propagation; delayed Moon search with twilight and altitude guards; kashrut strict six-hour policy with legacy-timer reset, quiet-period deferral and overdue handling; distinct ringing sessions with a FIFO queue; audio focus and Android denied-background-start warning; sensor lifecycle handling; off-main widget updates; partial polar results; DST policy; standalone Wear city/community/candle settings and Ukrainian translation.

**Wear settings are independent, NOT automatically synchronized from the phone.** Timezone auto-matching is approximate; confirm your chosen city.

For integrity and JVM testing use `python3 scripts/check_integrity.py` and `gradle :core:test`. For both APKs use `gradle :app:assembleDebug :wear:assembleDebug`. Full acceptance matrix: `QA_DEVICE_MATRIX.md`. Latest per-item source status: `AUDIT_PROGRESS.md`.

**Safety:** existing public `debug.keystore` is only for compatible debug installs, not trusted release signing. Exact-alarm, full-screen and OEM restrictions cannot be proven by CI, and important alarms must be checked on real hardware. Religious practice is not certified by a rabbi; confirm local candle customs, holy-day transitions and the kashrut interval.

No INTERNET permission added. GitHub Actions remains manual-only except for a single controlled Beta3 validation run to conserve minutes.

**Beta3 source changes (device validation pending):** fail-closed Shabbat boundaries and unknown cities; direct ringing-service Sabbath checks; early Friday silence; larger concurrent alarm queue; sound-only screen behavior; Wear halachic half-day dial; moon altitude and remaining-night logic; partial-polar Jewish date; all five phone translations and formatting-argument gate. Community-specific havdalah formulas are not certified and are intentionally unchanged pending qualified halachic review.

## Verified CI (Beta2)

Run: https://github.com/bystrzyca-cloud/Peleshaon/actions/runs/44175656618 (success).
Checked source commit: `421c5e5f298ab6a39713a5376f6fd2530e39aa10`.
Phone artifact: `PeleShaon-Beta-phone`. Wear artifact: `PeleShaon-Beta-wear`.
`core:test`, both debug builds, resource integrity, no INTERNET and widget packaging passed.
**Not tested:** live Android/Wear behavior, OEM locales, sound and exact-alarm runtime, religious interpretation.

GitHub Actions is manual-only again after this verification, to conserve runner minutes.
