# PeleShaon independent geographic and localization QA

Generated from **the actual source tree** and the pinned KATOTTG/OpenStreetMap
and GeoNames independent audit. An ID-level, auditable result is committed
in [qa/independent_geodata_1694.tsv](qa/independent_geodata_1694.tsv).

## Scope and verification totals

| Class | Total | Independently strong-matched | Requires review |
|---|---:|---:|---:|
| Ukraine | 1497 | 1076 | 421 |
| Worldwide legacy | 197 | 192 | 5 |
| **All** | **1694** | **1268** | **426** |

**Ukrainian localized names populated:** 1085/1497.
**Ukrainian place names still lacking independently supported Ukrainian form:** 412.
These are recorded by ID in [qa/ukraine_missing_uk_names.tsv](qa/ukraine_missing_uk_names.tsv).
**Ambiguous or unverified coordinates:** 426 entries in
[qa/geodata_requires_review.tsv](qa/geodata_requires_review.tsv).

## How independence is assessed

Ukraine: KATOTTG government settlement name + oblast code, matched geographically
to independent OpenStreetMap points/polygons from [ua-geo](https://github.com/alexbabintsev/ua-geo),
source commit d37d5e967e3bb45255d180ea740e5a6ca90e00cc.
KATOTTG input SHA-256: 0952de2b19b1fed78b041cac415f296c73b663649e1325f0823082c4f7db1024.

Worldwide: GeoNames cities500 published names and attested alternate names,
within a conservative spatial radius. Download SHA-256:
682a5f7e48463d64663755b3a4e17219dc4f27cea35d4b88c194d73a9bf453d1.

INDEPENDENT_MATCH requires >=0.85 normalized place-name similarity,
a plausible location within five kilometres for Ukraine (seven for worldwide),
and no other similarly matching place that would make identity ambiguous.
The rule establishes **strong independent consistency, not centimetre-level or
individual-address precision**.

Before this audit, 1,494 imported coordinates were merely compared to the
source dataset they originated from. Their equality did **not** constitute
independent verification; the source-match metadata is preserved separately
in [qa/coordinate_inventory.tsv](qa/coordinate_inventory.tsv).

## Language coverage vs certification

UI languages: Ukrainian, Russian, English, German, Hebrew.
- Ukrainian: government attested where known; 412 records remain unresolved,
  for which original Latin names may be shown rather than fabricated Ukrainian spelling.
- Russian: curated Russian exonyms where present, or Ukrainian Cyrillic as a documented
  fallback; for unresolved points original romanized name may remain.
- English: existing international romanizations / exonyms.
- German: original nameDe if curated; otherwise international Latin romanization.
- Hebrew: known Hebrew exonyms or automatically generated **phonetic transcription**.
  Phonetic rendering is not a certified Hebrew place name.

All five locales can display a non-blank title and region label, but **official
native-language translations of every settlement have not been certified**.
Do not equate five-language display coverage with fully reviewed exonyms.

## Release gate

- Technical inventory: complete for every 1694 location.
- Independent identity agreement: 1268 strong matches,
  426 unresolved.
- Hallakhic calendar rules and city-specific candle offsets require their own
  religious review, independent of geographic source quality.
- Do not mark all location data as geographically certified; inspect every
  remaining queue before final sign-off.

Attribution: OpenStreetMap under ODbL 1.0
https://www.openstreetmap.org/copyright ;
GeoNames https://www.geonames.org/ ;
KATOTTG public Ukrainian register.
