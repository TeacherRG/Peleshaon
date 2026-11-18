# Zmanim 3.1.0 regression matrix (automated first, rabbinic validation separate)

Version target: `com.kosherjava:zmanim:3.1.0`. Project revision on `fix/kosherjava-3-1-zmanim`.

| Reference city | Latitude | Longitude | Time zone | Winter | Spring | Summer | Autumn | Focus |
| --- | ---: | ---: | --- | --- | --- | --- | --- | --- |
| Jerusalem | 31.78 | 35.22 | Asia/Jerusalem | 2026-01-15 | 2026-04-15 | 2026-06-20 | 2026-10-15 | 40m candle default |
| Dnipro | 48.46 | 35.05 | Europe/Kyiv | 2026-01-15 | 2026-04-15 | 2026-06-20 | 2026-10-15 | Manual city vs timezone guess |
| Vienna | 48.21 | 16.37 | Europe/Vienna | 2026-01-15 | 2026-04-15 | 2026-06-20 | 2026-10-15 | Chabad ordinary vs Shabbat end |
| **Berlin** | 52.52 | 13.40 | Europe/Berlin | 2026-01-15 | 2026-04-15 | 2026-06-20 | 2026-10-15 | Explicit 6°/8.5° regression and seasonal twilight |
| Vilnius | 54.69 | 25.28 | Europe/Vilnius | 2026-01-15 | 2026-04-15 | 2026-06-20 | 2026-10-15 | Extended twilight |
| London | 51.51 | −0.13 | Europe/London | 2026-01-15 | 2026-04-15 | 2026-06-20 | 2026-10-15 | Northern twilight / DST |
| Tromsø | 69.6492 | 18.9553 | Europe/Oslo | 2026-01-15 | 2026-04-15 | 2026-06-20 | 2026-10-15 | Null on missing angle/solar events |

All values are test **locations and dates**, not claimed rabbinically approved end-times.

## Must pass

- For each date/city: if a sunrise/sunset exists, it is ordered correctly; if an angle is unreachable, do NOT manufacture a fixed-minute fallback.
- Chabad: ordinary tzait uses KosherJava 6° and Shabbat exit 8.5°; Berlin winter/spring/summer/autumn checked independently.
- Candle configuration and selected time zone reflect city, not automatic timezone-only inference.
- Precise deadlines remain unrounded until display, and starting-time displays may never predate the exact event.
- No Android INTERNET permission; unchanged original application ID; strict six-hour meat/dairy timer.

## Additional acceptance (not automatically certified)

Exact numerical comparison to calendar approved by local rav; manual city selection, Yom-Tov transitions in Israel/diaspora; Android 26–35 plus Wear OS; approved high-latitude practice, security key; correctness of Sephardi/Litvish profile names.

**Status:** test cases authored prior to code migration; no pass claimed until new GitHub Actions PR build is green.
