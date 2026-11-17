# Peleshaon Beta2 — acceptance tests on real hardware

These tests cannot be proven by compilation or JVM `core:test`. Test with the Beta installed **beside** the original; disable duplicate alarms in the original during the tests. Record Android build, OEM, device, timezone, language and time.

| ID | Action | Expected result |
| --- | --- | --- |
| T01 | Set MORNING and TORAH to same minute | Both events queue and can be dismissed independently; no premature missed label |
| T02 | Start a real alarm, dismiss from notification | Correct session ends, its Activity closes |
| T03 | Leave alarm untouched for 3 minutes | Missed event recorded, ringing Activity closes |
| T04 | Swipe app from Recents during ring | Foreground ringing continues; notification STOP available |
| T05 | Snooze alarm and disable it | No snoozed ring when disabled |
| T06 | Revoke exact alarm permission; test background/Doze | Honest nonexact warning; no crash; punctuality not promised |
| T07 | Regrant exact alarm permission | Replanning occurs, alarm survives reboot and package update |
| T08 | Disable full-screen notifications on API 34+ | Alarm still exposes actionable notification, UI not claimed full-screen |
| T09 | Set kashrut timer, delay delivery beyond deadline, open app | Overdue event not erased; no unexpected duplicate |
| T10 | Let kashrut expire during Shabbat | Behavior recorded and passed to authority; do not infer religious policy |
| T11 | Set 18, 20, 30, 40 and local city defaults | Correct candle times and alarm quiet interval |
| T12 | Compare Shabbat havdalah in calendar, home and alarm for all 3 traditions | No conflicting time displays |
| T13 | Friday Yom-Tov followed by Shabbat and consecutive holy days | Friday uses before-sunset candle transition; other day-to-day transitions after nightfall |
| T14 | Change language while settings sheet open, including system option | Immediate language updates; panel remains open; no double recreation |
| T15 | Change language through Android app settings | Resources/widgets/notifications remain consistent after restart/reboot |
| T16 | Test OPPO, realme or OnePlus ColorOS plus Pixel/Samsung | Any OEM-specific locale bugs recorded |
| T17 | Change city between distant timezones while app remains open | Alarm/Moon/widget/live notification replan without leaving activity |
| T18 | Switch theme, dial marks and tziruf | Widget updates without waiting for next RTC tick |
| T19 | Put Moon compass in background | No sensor sampling until resume |
| T20 | Night with Moon below 5° at tzeit but above later | Reminder scheduled only when >=5° and permitted |
| T21 | Check polar summer and winter | No fabricated sunrise/sunset; available remaining times still shown |
| T22 | On Wear select another city and Minhag | Watch reflects **its own** settings; it does not silently inherit phone |
| T23 | Ukrainian Wear OS language | All app labels and date-time strings localized |
| T24 | DST Europe/Vienna 2027-03-28 02:30 | First valid time 03:00 |
| T25 | DST Europe/Vienna 2027-10-31 02:30 | Earlier 02:30 occurrence, one notification |
| T26 | Run sound-and-light / sound-only / light-only with DND/Bluetooth | Correct focus, vibration and screen |
| T27 | Verify full-screen session actions from old notifications | Stale STOP/SNOOZE cannot control a newer alarm |
| T28 | Verify Beta and original side by side | Separate app ID, prefs, notifications, visible Beta name |

No test is marked PASS until it is actually performed. Report date/time, screenshots/logcat, APK SHA and device model for failures.
