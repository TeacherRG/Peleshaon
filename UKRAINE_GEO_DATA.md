# Ukrainian offline catalog — data provenance and acceptance limits

Source settlement names, longitude, latitude and source-regions: https://github.com/srestre/world-countries-cities-db (`countries/UA.json`, ODbL-derived, attribution/share alike).
Cross-check of source records against approximate oblast rectangles: https://github.com/peacerep/ukraine-map (`data/ukraine_bounds.json`).
Missing **Гнівань** (49.0939,28.33785) sourced from GeoNames search; missing **Покров** (47.65598,34.06847) sourced from Visicom mapping, https://maps.visicom.ua/c/34.06847%2C47.65598%2C13/f/STL1NLX3C?lang=uk.
Modern toponyms and 111 photo names: user source and https://zakon.rada.gov.ua/laws/show/3984-IX.
Jewish locality priorities: https://www.chabad.org/jewish-centers/country/ukraine and https://fjc-fsu.org/centers/.

Number of raw source records: 1819; after region-bounds / subdivision exclusion: 1505. Imported after preserving original 197 IDs: 1496. Photo names in acceptance tests: 111. Capital/Crimea/Sevastopol names: 26.

**Limits:** regional bounding-box checks are screening, not verification of precise coordinates. Additional localized names for less-common settlements and exact coordinate provenance still require review. Civil-time rules for occupied mainland territories may differ from official Ukraine time and need a separate explicit user timezone selection. Do not infer approval of religious times from the geodata source. No live geocoding API and no INTERNET permission. Retain data attribution and ODbL obligations when redistributing the derivative geographic database.

The same source data is shared by core, Android and Wear OS; existing CityOption IDs and persistent preferences remain stable.

QA correction (2026-11-18): `ua:71:kamianka` retains its stable ID and source coordinates (49.0318, 32.10396) but its Ukrainian display name is corrected to Кам’янка, Cherkasy Oblast, cross-checked against https://maps.visicom.ua/i/STL1NYKCA. The distinct Знам’янка, Kirovohrad Oblast, has its own `ua:35:znamianka` record (48.71702, 32.65411) referenced against https://maps.visicom.ua/i/STL1NQUKC. Search aliases never cross these cities.
