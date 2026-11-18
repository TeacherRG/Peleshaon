# Curated offline city catalog — owner-approved scope (2026-11-18)

The production **selection** contains **337 static coordinates**, not 1,694.
There are **140 retained Ukrainian records** and **197 previously existing
international/legacy records**. Ukrainian selection derives exclusively from:
- 111 named places in the user's supplied calendar photo;
- 26 administrative centres, including occupied centres and Sevastopol;
- 31 priority Jewish centres, excluding **Сіверськодонецьк (Sievierodonetsk)**
  following the owner's explicit update.

After resolving overlap, there are **147 unique Ukrainian place names**.
Two deliberately retained same-name pairs (Городок in Lviv/Khmelnytskyi
and Рокитне in Kyiv/Rivne oblasts) are different geographic points, giving
140 Ukrainian catalog records while 9 requested points already exist under
their stable legacy IDs. No arbitrary bulk source places are loaded.

**Critical correction:** Тернівка from the calendar is the **city in the
Pavlohrad raion, Dnipropetrovsk oblast**, at 48.5244825, 36.081723,
KATOTTG UA12120090010057273. This is NOT the eponymous village near
Sevastopol. Sources: https://decentralization.ua/locality/25560 ; https://maps.visicom.ua/i/STL1NLX3K .

**Critical correction:** the Donetsk oblast city Костянтинівка preserves its
saved ID `ua:14:kostiantynivka`; the centre moved from a 4.3 km-offset
coordinate to 48.5277,37.7069, closer to the independent KATOTTG settlement
centre (approximately 0.815 km). It does not duplicate the same city.

**Saved preferences:** Existing supported IDs are unchanged. If the owner
previously selected one of the removed bulk settlements, `city_id` remains
stored but is recognized as obsolete. The interface warns and refuses to
silently activate geographic religious alarms for an unrelated city until
the user reselects an approved settlement or supplies exact coordinates.
The strict six-hour kashrut policy is unchanged.

**Translations:** Every shipping city has non-empty visible text in UA/RU/EN/DE/HE.
The entire displayed string mapping and its source mode are recorded in
`qa/approved_city_localizations.tsv`. Ukrainian settlements have curated
Ukrainian names and curated Russian equivalents; English/German use attested
exonyms where provided, otherwise standard Latin romanization. Hebrew uses
widely known exonyms where available, otherwise visibly usable *phonetic
transcription*, **not a rabbinically or administratively certified toponym**.
An actual official five-language multilingual gazetteer has not been independently
validated.

**Geography:** `qa/approved_city_localizations.tsv` records all 337
coordinates and source status; `qa/selected_geodata_requires_review.tsv`
is a strict exception queue. Strong matches are based on KATOTTG/OSM
and GeoNames, with pinned sources and known coordinate-centre uncertainty.
This does not certify precision to a street address. Historic 1694-record
audits remain only in earlier Git history, not the shipping catalog.

ODbL / OSM attribution: https://www.openstreetmap.org/copyright .
KATOTTG / ua-geo source: https://github.com/alexbabintsev/ua-geo .
GeoNames: https://www.geonames.org/ .
