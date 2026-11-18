#!/usr/bin/env python3
"""Apply ONLY independently verified official KATOTTG Ukrainian display names.
The app stays offline. Coordinates, zones, IDs and other fields stay unchanged.
"""
import argparse
import csv
import hashlib
import json
from collections import Counter
from pathlib import Path
from audit_geodata_external import RECORD

EXPECTED_UA_SHA256 = "0952de2b19b1fed78b041cac415f296c73b663649e1325f0823082c4f7db1024"

def apply(catalog: Path, review: Path, summary: Path, source_file: Path, manifest: Path):
    assert hashlib.sha256(source_file.read_bytes()).hexdigest() == EXPECTED_UA_SHA256, "Changed source data"
    metadata = json.loads(summary.read_text(encoding="utf-8"))
    assert metadata["ua_dataset_sha256"] == EXPECTED_UA_SHA256, "Source digest mismatch"
    with review.open(encoding="utf-8",newline="") as f:
        data = list(csv.DictReader(f,delimiter="\t"))
    counts=Counter(r["candidate_code"] for r in data if r["status"] == "INDEPENDENT_MATCH" and r["kind"] == "ua")
    accepted={}
    skipped=[]
    for row in data:
        if row["kind"]!="ua" or row["status"]!="INDEPENDENT_MATCH" or row["uk"] or not row["candidate"]:
            continue
        if counts[row["candidate_code"]]!=1:
            skipped.append((row["id"],"DUPLICATE_KATOTTG_CODE"));continue
        if float(row["distance_km"])>5 or float(row["name_similarity"])<.85:
            skipped.append((row["id"],"LOW_CONFIDENCE"));continue
        if row["candidate_code"].startswith("UA"):
            accepted[row["id"]]=row
    if len(accepted)<900:
        if manifest.exists() and sum(1 for _ in manifest.open(encoding="utf-8"))>900:
            print("Previously applied; no new names")
            return 0
        raise ValueError("Insufficient independently matched names: "+str(len(accepted)))
    lines=catalog.read_text(encoding="utf-8").splitlines(keepends=True)
    modified=[]
    for index,line in enumerate(lines):
        m=RECORD.search(line)
        if not m or m[1] not in accepted:continue
        row=accepted[m[1]]
        if m[9]!="null":raise ValueError("Existing Ukrainian label must not be overwritten: "+m[1])
        if m[2]!=row["en"] or abs(float(m[5])-float(row["lat"]))>1e-7 or abs(float(m[6])-float(row["lon"]))>1e-7:
            raise ValueError("Unexpected location or coordinates change: "+m[1])
        anchor=', false, null, "'+row["oblast"]+'",'
        if line.count(anchor)!=1:raise ValueError("Missing immutable label anchor: "+m[1])
        lines[index]=line.replace(anchor,', false, '+json.dumps(row["candidate"],ensure_ascii=False)+', "'+row["oblast"]+'",',1)
        modified.append(row)
    if len(modified)!=len(accepted):
        raise ValueError("Missing or duplicated ID while updating Ukrainian names")
    catalog.write_text("".join(lines),encoding="utf-8")
    manifest.parent.mkdir(parents=True,exist_ok=True)
    columns=["id","en","candidate","candidate_code","lat","lon","reference_lat","reference_lon","distance_km","name_similarity","reference_match"]
    with manifest.open("w",encoding="utf-8",newline="") as f:
        writer=csv.DictWriter(f,fieldnames=columns,delimiter="\t",extrasaction="ignore")
        writer.writeheader()
        for row in sorted(modified,key=lambda r:r["id"]):writer.writerow(row)
    print(json.dumps({"official_ukrainian_names_added":len(modified),"duplicate_source_ids_skipped":skipped,
                      "coordinate_fields_changed":0,"source_sha256":EXPECTED_UA_SHA256},ensure_ascii=False))
    return len(modified)

if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--catalog",type=Path,default=Path("core/src/main/kotlin/app/mychitas/peleshaon/domain/UkraineCities.kt"))
    p.add_argument("--audit",type=Path,default=Path("build/geo-audit/geodata_independent_1694.tsv"))
    p.add_argument("--summary",type=Path,default=Path("build/geo-audit/summary.json"))
    p.add_argument("--source",type=Path,default=Path("/tmp/pele-geodata/katottg.csv"))
    p.add_argument("--manifest",type=Path,default=Path("qa/verified_ukrainian_names.tsv"))
    a=p.parse_args()
    apply(a.catalog,a.audit,a.summary,a.source,a.manifest)
