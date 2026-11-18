# Independent astronomical QA — 2026-11-18

**Target:** `TeacherRG/Peleshaon` at commit `bc1a03f`; comparison uses a separate Swiss Ephemeris implementation, not KosherJava calls.

## Independent baseline
- Swiss Ephemeris: solar equatorial coordinates and horizontal true altitude; root-finding (15-minute bracket, sub-second refinement).
- For sea-level sunrise and sunset, true solar center altitude **−0.8333°**. Dawn (Baal Hatanya) −16.9°, ordinary Chabad nightfall −6°, selected Shabbat exit −8.5°.
- Local zone according to app: Asia/Jerusalem, Europe/Kyiv, Vienna, Berlin, Vilnius, London, Oslo.
- 7 cities × 4 seasonal dates (2026-03-20, 2026-06-19, 2026-09-18, 2026-12-18), up to 140 independent event estimates; polar events may not exist.
- Regression: `IndependentEphemerisRegressionTest.kt` compares each existing astronomical event within 8 minutes (12 minutes in Tromsø) to account for ephemeris, limb/refraction, and high-latitude sensitivity. This is a regression safety threshold, **not precision certification**.
- Independent published Berlin calendar: https://download.hebcal.com/v4/CAEQARgBIAEoATABUAFYj4i0AWDqD3AyiAEB/hebcal_2026_berlin.pdf — June 19 candle lighting **21:14** at 18 minutes before sunset, consistent with independent 21:32 sunset.
- Reference methodology: https://www.hebcal.com/home/1663/zmanim-halachic-times-api and https://hebcal.github.io/api/core/classes/Zmanim.html ; Swiss Ephemeris documentation https://www.astro.com/swisseph-download/doc/swephprg.pdf .

## Important scope limits
Astronomy is not a substitute for a binding *halakhic* determination. These fixtures do not certify the choice of 6°/8.5° as communal practice, candle lighting default per specific city, or Sephardi/Litvish profile standards. Those remain explicitly pending qualified rabbinic review. Never silently replace missing polar events with fixed offsets.

## Local-time reference matrix
City | Date | Sunrise | Sunset | Dawn 16.9° | Tzait 6° | Exit 8.5°
---|---|---|---|---|---|---
jer | 2026-03-20 | 05:43 | 17:50 | 04:27 | 18:14 | 18:26
jer | 2026-06-19 | 05:33 | 19:47 | 04:00 | 20:15 | 20:29
jer | 2026-09-18 | 06:24 | 18:41 | 05:08 | 19:05 | 19:17
jer | 2026-12-18 | 06:33 | 16:37 | 05:11 | 17:04 | 17:17
dni | 2026-03-20 | 05:43 | 17:52 | 04:04 | 18:23 | 18:38
dni | 2026-06-19 | 04:37 | 20:44 | 01:40 | 21:26 | 21:49
dni | 2026-09-18 | 06:20 | 18:46 | 04:40 | 19:17 | 19:33
dni | 2026-12-18 | 07:26 | 15:45 | 05:38 | 16:22 | 16:39
vie | 2026-03-20 | 05:57 | 18:07 | 04:19 | 18:38 | 18:53
vie | 2026-06-19 | 04:53 | 20:58 | 02:00 | 21:39 | 22:02
vie | 2026-09-18 | 06:35 | 19:01 | 04:55 | 19:32 | 19:47
vie | 2026-12-18 | 07:40 | 16:01 | 05:52 | 16:38 | 16:55
ber | 2026-03-20 | 06:09 | 18:19 | 04:20 | 18:53 | 19:10
ber | 2026-06-19 | 04:42 | 21:32 | -- | 22:23 | 22:52
ber | 2026-09-18 | 06:45 | 19:14 | 04:55 | 19:48 | 20:05
ber | 2026-12-18 | 08:13 | 15:52 | 06:12 | 16:34 | 16:53
vil | 2026-03-20 | 06:21 | 18:32 | 04:26 | 19:08 | 19:25
vil | 2026-06-19 | 04:41 | 21:59 | -- | 22:56 | 23:32
vil | 2026-09-18 | 06:56 | 19:28 | 04:59 | 20:04 | 20:22
vil | 2026-12-18 | 08:38 | 15:52 | 06:29 | 16:37 | 16:57
lon | 2026-03-20 | 06:03 | 18:13 | 04:17 | 18:46 | 19:03
lon | 2026-06-19 | 04:42 | 21:21 | -- | 22:08 | 22:35
lon | 2026-09-18 | 06:40 | 19:08 | 04:52 | 19:41 | 19:58
lon | 2026-12-18 | 08:01 | 15:52 | 06:05 | 16:32 | 16:50
tro | 2026-03-20 | 05:43 | 18:01 | 02:09 | 19:02 | 19:32
tro | 2026-06-19 | -- | -- | -- | -- | --
tro | 2026-09-18 | 06:08 | 19:05 | 02:06 | 20:07 | 20:38
tro | 2026-12-18 | -- | -- | 06:39 | 13:52 | 14:42

Minutes are rounded for reporting; automated comparison uses tolerance to independent astronomical calculations. `--` means the event was not found for that calendar date; no date/time was fabricated.

## Separate holiday and derived-time checks
- Unit regression `IndependentHolidayAndDayFractionsTest` tests the published calendar distinction: **3 October 2026** Shmini Atzeret/Simchat Torah in Israel, **4 October 2026** additional Simchat Torah in the Diaspora; **21 September 2026** Yom Kippur is a quiet day in both jurisdictions. Sources: https://www.chabad.org/library/article_cdo/aid/753891/jewish/Shemini-Atzeret-Simchat-Torah-Reference-Guide-2026.htm and https://www.hebcal.com/holidays/2026 .
- For Jerusalem and Berlin in four seasons, test proportional Baal Hatanya day fractions for Shema 3/12, Tfila 4/12, Chatzot 6/12, Mincha Gedola 6.5/12 and Plag Hamincha 10.75/12; max 10 min tolerance. This checks formula selection, but not the community's adoption of each method.
- Note: 10-minute regression guards are designed to detect gross errors; they do not certify minute-perfect times for community practice. Litvish/Sephardi selected methods and polar halakhic fallback remain subject to qualified religious review.
