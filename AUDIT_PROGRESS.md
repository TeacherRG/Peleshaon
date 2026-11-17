# Peleshaon Beta — remediation register (17 November 2026)

Branch: `fixes/independent-beta` (never `main`). Source for this audit: `543abe294b1d94ba60374d8e909bbd7df26f2231`.
The previously successful run 44162207356 tested **Beta 1**, NOT these subsequent changes.

This register merges the supplied neighboring-chat audit, the prior Beta defect audit, and direct code inspection.
`FIXED_SOURCE` means patched; only a green **new** CI can confirm compilation, and a **device test** is still required for UI/runtime behavior.

## High-priority calendar and religious timing

| ID | Status | Change / unresolved decision |
| --- | --- | --- |
| P0-01 Yom-Tov Friday → Shabbat | FIXED_SOURCE + HALAKHIC_REVIEW | `JewishCal.candleTransition` distinguishes before-sunset Friday from after-nightfall holy-day transitions. Eruv Tavshilin, transfer of flame, and local restrictions require rabbinic confirmation |
| P0-02 all cities 18 min | FIXED_SOURCE + HALAKHIC_REVIEW | `ZmanimEngine.candleMinutesFor`, Jerusalem 40m, Haifa 30m, otherwise 18m by default; explicit user override 18/20/30/40 minutes controls alarm quiet window as well. Local minhag may differ |
| P0-03 inconsistent havdalah | FIXED_SOURCE | Calendar uses `ZmanimEngine.havdalahOn` for selected tradition instead of adding 50 minutes |
| P1-03 Kiddush Levana during twilight | FIXED_SOURCE + HALAKHIC_REVIEW | Require selected `TZAIT` before showing eligibility. Exact communal rules still require review |
| P1-09 fixed kashrut six-hour wait | FIXED_SOURCE + HALAKHIC_REVIEW | User selects 1/3/5/6 hours; not labeled as universal permission |
| P1-10 tradition definitions | HALAKHIC_REVIEW | Preserve existing CHABAD/LITVISH/SEFARDI formulas; a qualified authority must approve each event-specific method; no automatic KosherJava major-version migration |

References: https://www.hebcal.com/home/4463/candle-lighting-havdalah-fast-times and https://www.ou.org/life/torah/eruv-tavshilin-primer/.

## Beta issue inventory (confirmed source changes or open risks)

| ID | Status | Fix / verification |
| --- | --- | --- |
| BETA-01 concurrent rings | FIXED_SOURCE / DEVICE_PENDING | FIFO `PendingAlarmQueue`, current session stays audible until dismissed/timeout; duplicates ignored, stale tokens rejected; test two alarms on device |
| BETA-02 inexact FGS start | MITIGATED / DEVICE_PENDING | Receiver catches background launch failure and logs, attempts a high-priority warning notification; cannot guarantee sound without exact alarm permission |
| BETA-03 stale ringing Activity | FIXED_SOURCE / DEVICE_PENDING | Non-exported session-finish dynamic receiver and `onResume` session guard |
| BETA-04 swiping app stops alarm | FIXED_SOURCE / DEVICE_PENDING | Removed unconditional `stopSelf()` from `onTaskRemoved` |
| BETA-05 expired kashrut timer discarded | FIXED_SOURCE / DEVICE_PENDING | Overdue expiry re-armed instead of silently erased; one-shot cleared in actual receiver |
| BETA-06 wrong calendar havdalah | FIXED_SOURCE | Shared engine calculation |
| BETA-07 city-change propagation | FIXED_SOURCE | Alarms, theme, MoonReminder, widgets, live notification updated |
| BETA-08 compass sensor in background | FIXED_SOURCE / DEVICE_PENDING | Register only when lifecycle RESUMED; unregister on PAUSE/disposal |
| BETA-09 moon reminder below threshold | FIXED_SOURCE | Both scheduling and notification require altitude at least 5 degrees |
| BETA-10 moon rises later | FIXED_SOURCE / NEEDS_TEST | Scan eligible evening in 10-minute steps until sunrise/window end |
| BETA-11 Wear community differs | FIXED_SOURCE (ALTERNATIVE) / DEVICE_PENDING | Independent CHABAD/LITVISH/SEFARDI selector on watch, NOT phone synchronization |
| BETA-12 Wear auto city inaccurate | FIXED_SOURCE (ALTERNATIVE) | Manual watch city picker; time-zone default explicitly labeled approximate |
| BETA-13 partial polar results hidden | FIXED_SOURCE | Use surviving astronomical events even without sunrise/sunset, never invent missing times |
| BETA-14 widget render on receiver thread | FIXED_SOURCE / DEVICE_PENDING | Shared single-thread executor, `goAsync`, bounded per-receiver completion needed in tests |
| BETA-15 full-screen notification access | DEVICE_PENDING | Existing permission screen retained; need OEM/API 34–35 testing |
| BETA-16 exact permission change cancels alarms | PARTIAL / DEVICE_PENDING | Add permission-grant broadcast to BootReceiver; revocation still requires user to re-enable |
| BETA-17 theme and clock marks stale widget | FIXED_SOURCE | Update widgets on selection changes |
| BETA-18 kashrut timer displayed in wrong zone | FIXED_SOURCE | Same selected-city time zone as clock |
| BETA-19 sunset delayed to next minute in calendar | FIXED_SOURCE | Compare current tick with cached sunset |
| BETA-20 independent app naming | FIXED_SOURCE | App name and manifest identify Beta on phone and watch |
| BETA-21 OEM localization behavior | MITIGATED / DEVICE_PENDING | LocaleManager fallback flag, background contexts; ColorOS still needs physical verification |
| BETA-22 DST gap/overlap | FIXED_SOURCE / DEVICE_PENDING | Gap => first valid instant; overlap => earlier offset; tested for Vienna 2027 |
| BETA-23 no audio focus | FIXED_SOURCE / DEVICE_PENDING | Request transient exclusive alarm focus; release on stop |
| BETA-24 kashrut snooze | DEFERRED | One-shot timer intentionally cannot be snoozed; UI documents this limitation |
| BETA-25 public debug signing key | RELEASE_BLOCKED | Do not use public key for trusted distribution; separate private signing key required |
| BETA-26 no Android UI automation | DEVICE_PENDING | Core JVM tests expanded; emulator/OEM tests not executed |
| BETA-27 Play exact-alarm policy | RELEASE_BLOCKED | Permissions / FGS declarations need Play compliance review before publishing |
| BETA-28 version increment | FIXED_SOURCE | Beta2: versionCode 3 and versionName 1.0.0-beta2 |

## Neighbor-chat notes

- P1-01 language: source fallback made safer, but *instant switching on real ColorOS* remains unverified.
- P1-02 moon at later night: scanned rather than discarded.
- P1-04 location auto is timezone-based, **never precise GPS**; users can pick a city manually.
- P1-05 phone vs Wear: independent watch preferences implemented (accepted alternative), **not live device-to-device synchronization**.
- P1-06 exact alarms: fallback cannot promise punctual sound.
- P1-07 simultaneous alarms: queue and session guard; full-screen and notification actions require real-device tests.
- P1-08 one-shot kashrut during Shabbat: behavior intentionally unchanged pending product/halakhic choice; no silent invented rule.
- P2-01 Adar I/II: `CalEvent.leap` now passes through `jewishMonthOnly`.
- P2-02 Wear Ukrainian strings: `wear/src/main/res/values-uk/strings.xml` added.
- P2-03 widgets: heavy bitmap work moved off main thread, performance not measured on OEM.
- P2-04 background locale: notification context and service localized; device verification needed.
- P2-05 KosherJava 2.5.0: intentionally retained; 3.x compatibility requires separate parity audit.

## Release and privacy safeguards

Beta is standalone `app.mychitas.peleshaon.beta` on both Android and Wear.
No INTERNET permission is added. The publicly committed `debug.keystore` remains for upgrade-compatible private debugging only.
A private release signer and secure GitHub Secrets must be provisioned by the owner before any trusted release; never put its password or keystore in Git.
The project uses a **manual-only** GitHub Actions workflow while patching; one controlled verification run can be enabled when source validation is complete.

## Approval

Source patches: YES. Compiled/CI-tested for Beta2: **YES — PASS** (`:core:test`, Android and Wear debug APKs, resources, widget registration, no INTERNET), run [44175656618](https://github.com/bystrzyca-cloud/Peleshaon/actions/runs/44175656618), source commit `421c5e5f298ab6a39713a5376f6fd2530e39aa10`.
Verified on Android/Wear devices: **NO**.
Halakhic correctness of all communal rules: **NOT CERTIFIED**.

See `QA_DEVICE_MATRIX.md` and `FORK_BETA.md` before using the alarm for important wake-ups.
