# Full coordinate inventory audit (2026-11-18)

**Source commit**: `bc1a03f184add375334456b4f2272ab63d7479c0`; checks cover **every 1694 static geographic record** (1497 imported Ukraine, 197 legacy worldwide).

## Exhaustive checks
- **1494 imported positions exactly match** the cited original source's longitude+latitude at 5 decimals: https://github.com/srestre/world-countries-cities-db . This is transfer integrity **NOT independent real-world geocoding**.
- **3 supplemental records**: `ua:12:pokrov`, `ua:35:znamianka`, `ua:05:hnivan`; source-not-exact status is expected.
- All 1694 positions are finite and within global longitude/latitude bounds. All 1497 imported Ukraine positions fit the broad Ukraine bounding rectangle (44..53 north and 22..41 east), which does NOT establish accurate region assignment.
- The 1494 source-exact positions all preserve original region codes, **zero cross-region mismatches**.
- **Zero duplicate coordinate pairs**, rounded to four decimal places, among all 1694 records.
- **1351 imported entries lack independently checked Ukrainian display labels**. Never fill these with guessed translations.
- **Two same-name+oblast ambiguities**: Самар (`ua:12:novomoskovsk`, `ua:12:novomoskovsk:2`); Подільськ (`ua:51:podilsk`, `ua:51:podilsk:2`). Keep both IDs until independently disambiguated; coordinates should be shown in UI. Five other same-name cases are distinguishable by their oblast text.
- Every record and its individual status is enumerated in `qa/coordinate_inventory.tsv`. Unverified Ukrainian-language labels are listed in `qa/ukraine_missing_uk_names.tsv`.

## External cross-checks for supplemental Ukrainian cities
- Кам’янка (Cherkasy, 49.0318,32.10396): https://www.fallingrain.com/world/UP/01/Kamyanka.html
- Знам’янка (Kirovohrad, app 48.71702,32.65411): independently near 48.71996,32.66595 https://mapcarta.com/13719516 . The discrepancy is sub-km to about a kilometre and needs an authoritative settlement-centre definition for high-precision certification.
- Гнівань (Vinnytsia, 49.0939,28.33785): independently appears at matching coordinates https://www.geonames.org/search.html?country=UA&q=Vinn%C3%B5tsja&startRow=50
- Покров (Dnipropetrovsk, 47.65598,34.06847): https://maps.visicom.ua/c/34.06847%2C47.65598%2C13/f/STL1NLX3C?lang=uk

## Unresolved external-verification limits
- The source copied for the 1,494 identical positions **cannot** independently validate itself. A licensed authoritative settlement index with precise boundaries and stable matching IDs is required to certify all positions geographically.
- **197 legacy worldwide positions** passed full numeric integrity checks but were **not** compared one-by-one with another global geocoding index. The full list is retained as `LEGACY_UNVERIFIED`.
- Correct arithmetic with approximate village coordinates does not make the coordinates authoritative. Use the exact-location option for sensitive religious times.

**QA gate**: complete static inventory and provenance/transfer validation; independent verification of every locality remains OPEN, and must not be labeled complete.
