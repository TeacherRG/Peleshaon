#!/usr/bin/env python3
"""Synchronize versioned, auditable QA files after catalog locale changes.
Never leave stale counts in a download link. No runtime/network dependencies.
"""
import csv
import json
from collections import Counter
from pathlib import Path
from audit_geodata_external import load_catalog

ROOT = Path(".")
DOMAIN = ROOT / "core/src/main/kotlin/app/mychitas/peleshaon/domain"
QA = ROOT / "qa"
RESULTS = ROOT / "build/geo-audit"

def load_tsv(path):
    with path.open(encoding="utf-8",newline="") as f:
        return list(csv.DictReader(f,delimiter="\t"))

def write_tsv(path, data, fields):
    with path.open("w",encoding="utf-8",newline="") as f:
        w=csv.DictWriter(f,fieldnames=fields,delimiter="\t",extrasaction="ignore")
        w.writeheader()
        w.writerows(data)

def run():
    qa_old=load_tsv(QA/"coordinate_inventory.tsv")
    audit=load_tsv(RESULTS/"geodata_independent_1694.tsv")
    if len(audit)!=1694 or len(qa_old)!=1694:
        raise ValueError("Independent/current QA record counts must both be 1694")
    original={r["id"]:r for r in qa_old}
    matches={r["id"]:r for r in audit}
    catalog=load_catalog(DOMAIN)
    ids={c["id"] for c in catalog}
    if ids!=set(original) or ids!=set(matches):
        raise ValueError("Audit/source inventory IDs disagree; refuse stale report")
    changed=[]
    all_rows=[]
    for city in catalog:
        ref=matches[city["id"]]
        old=original[city["id"]].copy()
        # The old inventory is source-provenance evidence, never silently erased.
        old_status=old.get("original_source_status") or old.get("position_audit_status","")
        old["original_source_status"]=old_status
        old["kind"]="imported" if city["kind"]=="ua" else "legacy"
        old["english_name"]=city["en"]
        old["ukrainian_name"]=city["uk"]
        old["oblast"]=city["oblast"]
        old["latitude"]=str(city["lat"])
        old["longitude"]=str(city["lon"])
        old["timezone"]=city["zone"]
        old["position_audit_status"]=ref["status"]
        old["localization_flag"]="MISSING_UK_NAME" if city["kind"]=="ua" and not city["uk"] else ""
        old["independent_source"]=ref["provider"]
        old["reference_place"]=ref["candidate"]
        old["reference_code"]=ref["candidate_code"]
        old["reference_lat"]=ref["reference_lat"]
        old["reference_lon"]=ref["reference_lon"]
        old["independent_difference_km"]=ref["distance_km"]
        old["name_similarity"]=ref["name_similarity"]
        old["geodata_match_method"]=ref["reference_match"]
        old["line"]=str(city["line"])
        all_rows.append(old)
        if city["kind"]=="ua" and not city["uk"]:
            changed.append({
                "id":city["id"],"english_name":city["en"],"oblast":city["oblast"],
                "latitude":str(city["lat"]),"longitude":str(city["lon"])
            })
    original_cols=list(qa_old[0])
    extended=["original_source_status","independent_source","reference_place","reference_code",
              "reference_lat","reference_lon","independent_difference_km","name_similarity",
              "geodata_match_method"]
    fields=original_cols+[c for c in extended if c not in original_cols]
    write_tsv(QA/"coordinate_inventory.tsv",all_rows,fields)
    write_tsv(QA/"ukraine_missing_uk_names.tsv",changed,
              ["id","english_name","oblast","latitude","longitude"])
    write_tsv(QA/"independent_geodata_1694.tsv",audit,list(audit[0]))
    blocked=[r for r in audit if r["status"]!="INDEPENDENT_MATCH"]
    write_tsv(QA/"geodata_requires_review.tsv",blocked,list(audit[0]))
    counts=Counter((r["kind"],r["status"]) for r in audit)
    ua=sum(r["kind"]=="ua" for r in audit)
    world=sum(r["kind"]=="legacy" for r in audit)
    confirmed_ua=counts[("ua","INDEPENDENT_MATCH")]
    confirmed_world=counts[("legacy","INDEPENDENT_MATCH")]
    marked_uk=ua-len(changed)
    sha=json.loads((RESULTS/"summary.json").read_text(encoding="utf-8"))
    report=f"""# PeleShaon independent geographic and localization QA

Generated from **the actual source tree** and the pinned KATOTTG/OpenStreetMap
and GeoNames independent audit. An ID-level, auditable result is committed
in [qa/independent_geodata_1694.tsv](qa/independent_geodata_1694.tsv).

## Scope and verification totals

| Class | Total | Independently strong-matched | Requires review |
|---|---:|---:|---:|
| Ukraine | {ua} | {confirmed_ua} | {ua-confirmed_ua} |
| Worldwide legacy | {world} | {confirmed_world} | {world-confirmed_world} |
| **All** | **{len(audit)}** | **{confirmed_ua+confirmed_world}** | **{len(blocked)}** |

**Ukrainian localized names populated:** {marked_uk}/{ua}.
**Ukrainian place names still lacking independently supported Ukrainian form:** {len(changed)}.
These are recorded by ID in [qa/ukraine_missing_uk_names.tsv](qa/ukraine_missing_uk_names.tsv).
**Ambiguous or unverified coordinates:** {len(blocked)} entries in
[qa/geodata_requires_review.tsv](qa/geodata_requires_review.tsv).

## How independence is assessed

Ukraine: KATOTTG government settlement name + oblast code, matched geographically
to independent OpenStreetMap points/polygons from [ua-geo](https://github.com/alexbabintsev/ua-geo),
source commit {sha["ua_source_commit"]}.
KATOTTG input SHA-256: {sha["ua_dataset_sha256"]}.

Worldwide: GeoNames cities500 published names and attested alternate names,
within a conservative spatial radius. Download SHA-256:
{sha["global_dataset_sha256"]}.

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
- Ukrainian: government attested where known; {len(changed)} records remain unresolved,
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

- Technical inventory: complete for every {len(audit)} location.
- Independent identity agreement: {confirmed_ua+confirmed_world} strong matches,
  {len(blocked)} unresolved.
- Hallakhic calendar rules and city-specific candle offsets require their own
  religious review, independent of geographic source quality.
- Do not mark all location data as geographically certified; inspect every
  remaining queue before final sign-off.

Attribution: OpenStreetMap under ODbL 1.0
https://www.openstreetmap.org/copyright ;
GeoNames https://www.geonames.org/ ;
KATOTTG public Ukrainian register.
"""
    (ROOT/"GEODATA_FULL_AUDIT.md").write_text(report,encoding="utf-8")
    print(json.dumps({
        "places":len(audit),"ukrainian_name_filled":marked_uk,
        "ukrainian_name_unresolved":len(changed),
        "independent_strong_matches":confirmed_ua+confirmed_world,
        "identity_unresolved":len(blocked),
        "inventory_ids_consistent":True
    },indent=2))

if __name__=="__main__":
    run()
