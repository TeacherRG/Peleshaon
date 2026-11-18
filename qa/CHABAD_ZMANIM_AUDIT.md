# Independent Chabad.org zmanim comparison — 2026-11-18

PeleShaon generated 165 unrounded, second-precision city/date results
(15 cities × 11 Fridays) from its own KosherJava 3.1 adapter in CI run
44331180598. Artifact: peleshaon-zmanim-internal-probe.

17 independent official candle-lighting date comparisons across nine cities
had difference 0 or -1 minute (application equal/earlier);
15 comparable Saturday-night Shabbat-end times differed by 0 or +1 minute
(application equal/later). Chabad's schedule specifically uses 16.9°
dawn, 6° regular nightfall, 8.5° Shabbat end; the code uses these angles.
No >1 minute mismatch was detected in this selected sample.

On 2026-11-13 in Simferopol, the exact app dawn is 06:02:50 and midday
is 12:27:49. Both previously displayed one minute late because generic
earliest-event rounding was used. Dawn is a fasting onset threshold and
midday can be the latest time for late Shacharit, so now both are floored
to their previous whole minute. Raw mathematical instants are unchanged.
Regression tests verify these and other city/season boundaries.

Limitations: this validates calculation code and its display-rounding rules;
it does NOT photographically verify a live device's screen. Chabad.org
times were compared manually for a subset, not all 337 catalog records.
No Chabad.org protected time tables are stored in the repository.

Source: https://www.chabad.org/library/article_cdo/aid/3209349/jewish/About-Our-Zmanim-Calculations.htm
