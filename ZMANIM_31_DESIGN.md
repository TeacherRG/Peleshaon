# Peleshaon — Zmanim engine migration design (KosherJava 3.1.0)

**Base:** `TeacherRG/Peleshaon@337635e`; **working branch:** `fix/kosherjava-3-1-zmanim`.
**Status:** architecture plan; code, unit tests, reviewer policy and CI must be validated separately.
**Compatibility constraints:** same original Android & Wear applicationId `app.mychitas.peleshaon`, minSdk 26, Java 17, offline-only, no INTERNET permission, preserve current preferences, strict six-hour meat-to-dairy timer.

## Architecture contract

1. `core/domain/ZmanimService.kt`: pure per-call calculation taking immutable `ZmanimConfig(city, minhag, candleOffsetMinutes?)`, `LocalDate`; no mutable global minhag, no Android context, no shared mutable calendar.
2. All methods return precise `Instant` or nullable structured result, never invent a fallback when sunrise / angle is undefined. `ZmanimSnapshot` includes individual calculated fields and calculation-method metadata; event times and display rounding are separate concerns.
3. `ZmanimEngine` retains its public Android/Wear API for compatibility, but delegates base events and profile-dependent calculations to `ZmanimService` while migration is staged. All callers must eventually pass explicit config rather than relying on process-global `minhag`.
4. All `ShabbatGuard` calculations use **exact** `Instant` boundaries. The Shabbat/Yom Tov `havdalah` is distinct from ordinary `tzait`; if required end event is undefined, alarms fail closed.
5. Calendar UI, widgets, ringing service, reminders and watch should consume the same computed event definition; UI rounding occurs only at presentation.
6. `ComprehensiveZmanimCalendar` is instantiated for each city/date/config with `GeoLocation(..., ZoneId)` and `localDate`. `Instant`, `Duration`, `LocalDate` replace `Date`, `TimeZone`, `GregorianCalendar`; `JewishCalendar.Parshah` replaces `Parsha`.
7. Keep NOAA astronomical calculator as the initial baseline. SPA/Meeus, elevation, actual skyline/horizon calibration require independent measurement and are not silently enabled.
8. Preserve original signed app install identity. The public debug key is **not** a legitimate production signing key.

## Halakhic policy that can be implemented deterministically

- `CHABAD` ordinary `tzait`: KosherJava `tzaisBaalHatanya` (solar depression 6°).
- `CHABAD` Shabbat/Yom Tov end: `tzaisGeonim8Point5Degrees` (8.5°), not sunset + 50min.
- `CHABAD` dawn: `alosBaalHatanya` (16.9°).
- `CHABAD` Shema/Tfila: existing `sofZmanShmaBaalHatanya`/`sofZmanTfilaBaalHatanya`.
- `LITVISH` preliminary: the project's existing GRA Shema/Tfila and 8.5° end, explicitly identified as one selected calculation, not a statement about every Lithuanian community.
- `SEFARDI`: retain legacy methods until rabbinic signoff. The UI must identify `tzais72Zmanis` as Rabbeinu Tam / selected stringency rather than a universal Sephardi rule; do not silently change it.
- `candleLighting`: user-selected override or city default; include Tel Aviv 22m, Jerusalem 40m, Haifa 30m as configurable local defaults; for unusually short days require additional plag test.
- Existing `KashrutPolicy.WAIT_HOURS=6` must remain unchanged.
- Unknown sky conditions: return absent/null, never silently substitute fixed-minute Shabbat end.

## Test-first contracts (core JVM)

- At seven reference points: Jerusalem, Dnipro, Vienna, **Berlin**, Vilnius, London, Tromsø, each across winter/spring/summer/autumn, verify sunrise/sunset and computed zmanim preserve order, null for genuinely unreachable twilight, no synthetic event.
- Chabad: 6° ordinary tzait **strictly before** 8.5° Shabbat end for representative midlatitude dates; different summer/winter intervals (not fixed 50m).
- After-sunset Jewish calendar day switch, Erev Yom Tov → Shabbat, consecutive holy days, Israel vs Diaspora.
- Cross-API: immutable ZmanimService twice with different profiles must not contaminate each other; concurrent calculations on workers should return stable values.
- Java-time/DST tests for Vienna, Berlin, Jerusalem, Dnipro; same date/instant semantics.
- Moon window start/end around daylight transitions; no incorrect rounding before allowed moment.
- Verify old 2.5.0 baseline (unchanged rule semantics), explicitly record accepted differences caused by 3.1.0 and separately those caused by policy changes.

## Delivery gates

1. Write architecture contract **before implementation**.
2. Author and commit unit tests prior to modifying engine code; tests intentionally fail before implementation.
3. Implement API migration and rules in source. Run static resource/import checks.
4. Create an upstream PR only after source/test review; the single PR-triggered GitHub Actions run validates `python3 scripts/check_integrity.py`, `:core:test`, `:app:assembleDebug` and `:wear:assembleDebug`.
5. If CI fails, inspect the actual logs and fix; do not claim completion. **Do not merge** until CI is green and high-stakes halakhic policies are reviewed.
6. Real-device Android/OEM and Wear acceptance, future exact-alarm/Play policy, private signing, historical reference dataset remain separate release gates.

## Deferred items requiring external source or policy approval

- Universal Sephardi method designation, local minhag variations, actual geographic horizon height, polar-night halakhic policy, which religious actions a given early zman controls.
- GPS auto-location would require permissions/privacy change; keep offline manual city selection.
- Dedicated integration tests on real OEM devices cannot be replaced by JVM tests or Actions.
