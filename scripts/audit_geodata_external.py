#!/usr/bin/env python3
"""Offline-first PeleShaon geodata independent audit.

This script RUNS ONLY IN CI/development. The app never downloads location data.
Its authoritative Ukrainian name candidates come from the government KATOTTG
register (via ua-geo); independent coordinates come from OSM centres. Other
global place candidates come from GeoNames. It never silently changes the app.
"""
import argparse
import csv
import hashlib
import io
import json
import math
import re
import unicodedata
import zipfile
from collections import Counter, defaultdict
from dataclasses import dataclass
from difflib import SequenceMatcher
from pathlib import Path

UA_SOURCE = "https://github.com/alexbabintsev/ua-geo"
GLOBAL_SOURCE = "https://www.geonames.org/"
# https://github.com/alexbabintsev/ua-geo/commit/d37d5e967e3bb45255d180ea740e5a6ca90e00cc
UA_SOURCE_COMMIT = "d37d5e967e3bb45255d180ea740e5a6ca90e00cc"

RECORD = re.compile(
    r'CityOption\("([^"]+)", "([^"]+)", "([^"]+)", (null|"[^"]*"), '
    r'(-?[0-9.]+), (-?[0-9.]+), "([^"]+)", (true|false)'
    r'(?:, (null|"[^"]*"), (null|"[^"]*"),)?'
)
# Ukrainian official Romanization; approximate spelling comparison ONLY.
UK_LATIN = {
    "а":"a","б":"b","в":"v","г":"h","ґ":"g","д":"d","е":"e","є":"ie",
    "ж":"zh","з":"z","и":"y","і":"i","ї":"i","й":"i","к":"k","л":"l",
    "м":"m","н":"n","о":"o","п":"p","р":"r","с":"s","т":"t","у":"u",
    "ф":"f","х":"kh","ц":"ts","ч":"ch","ш":"sh","щ":"shch","ь":"",
    "ю":"iu","я":"ia","'":"","’":"","-":""," ":""
}
# KATOTTG codes for the two special Ukrainian cities and Crimea; others use
# ISO-3166 region 2-digit codes as in the original imported catalog.
REGION_MAP = {"43":"01", "30":"80", "40":"85"}
def normalize(value):
    value = unicodedata.normalize("NFKD", value.casefold())
    value = "".join(ch for ch in value if not unicodedata.combining(ch))
    return "".join(c for c in value if c.isalnum())
def romanize(value):
    return normalize("".join(UK_LATIN.get(ch, ch) for ch in value.casefold()))
def score_names(a, b):
    if not a or not b:
        return 0.0
    return SequenceMatcher(None, normalize(a), normalize(b)).ratio()
def geo_distance_km(lat1,lon1,lat2,lon2):
    phi1,phi2=math.radians(lat1),math.radians(lat2)
    dphi=phi2-phi1
    dlon=math.radians(lon2-lon1)
    z=math.sin(dphi/2)**2+math.cos(phi1)*math.cos(phi2)*math.sin(dlon/2)**2
    return 12742*math.asin(min(1,math.sqrt(z)))
def load_catalog(core):
    paths=[
        (core/"UkraineCities.kt","ua"),
        (core/"Cities.kt","legacy")
    ]
    out=[]
    for file,kind in paths:
        for line,raw in enumerate(file.read_text(encoding="utf-8").splitlines(),1):
            m=RECORD.search(raw)
            if not m: continue
            uk = m[9][1:-1] if m[9] and m[9]!="null" else ""
            oblast = m[10][1:-1] if m[10] and m[10]!="null" else ""
            out.append({
                "id":m[1],"en":m[2],"ru":m[3],"lat":float(m[5]),
                "lon":float(m[6]),"zone":m[7],"uk":uk,"oblast":oblast,
                "kind":kind,"line":line
            })
    assert 250 <= len(out) <= 400, f"Unapproved catalog size: {len(out)}"
    return out
def load_ua(path):
    rows=defaultdict(list)
    with path.open(encoding="utf-8-sig",newline="") as f:
        reader=csv.DictReader(f)
        required={"name","code","level","region_code","lat","lon"}
        if not required.issubset(reader.fieldnames or []):
            raise ValueError("KATOTTG schema changed: "+repr(reader.fieldnames))
        for row in reader:
            if str(row.get("level","")).strip()!="4":
                continue
            try:
                lat,lon=float(row["lat"]),float(row["lon"])
            except (ValueError,TypeError):
                continue
            code = str(row.get("region_code") or row.get("code") or "")
            if not code.startswith("UA") or len(code)<4: continue
            rows[code[2:4]].append((lat,lon,row["name"].strip(),row["code"],row.get("match","")))
    return rows
def load_geonames(zip_path):
    out=defaultdict(list)
    with zipfile.ZipFile(zip_path) as z:
        members=[n for n in z.namelist() if n.endswith(".txt")]
        if not members: raise ValueError("Missing GeoNames .txt file")
        with z.open(members[0]) as raw:
            for line in io.TextIOWrapper(raw,encoding="utf-8"):
                f=line.rstrip("\n").split("\t")
                if len(f)<15: continue
                try:
                    lat,lon=float(f[4]),float(f[5])
                except ValueError:
                    continue
                point=(lat,lon,f[1],f[2],f[8],f[0],tuple(f[3].split(",")))
                out[(math.floor(lat),math.floor(lon))].append(point)
    return out
def find_ua(city,ua):
    region=REGION_MAP.get(city["id"].split(":")[1],city["id"].split(":")[1])
    near=[]
    for lat,lon,name,code,match in ua.get(region,[]):
        # Cheap bounding prefilter: no valid match can be over 20 km away.
        if abs(lat-city["lat"])>.22 or abs(lon-city["lon"])>.32:continue
        dist=geo_distance_km(city["lat"],city["lon"],lat,lon)
        if dist>20:continue
        sim=max(score_names(city["en"],romanize(name)),score_names(city["uk"],name),
                score_names(city["ru"],name))
        near.append((dist,sim,name,code,lat,lon,match))
    near.sort(key=lambda x:(-x[1],x[0]))
    viable=[x for x in near if x[1]>=.80 and x[0]<=7]
    viable.sort(key=lambda x:(-x[1],x[0]))
    candidate=viable[0] if viable else min(near,key=lambda x:x[0],default=None)
    if not candidate: return "NO_NEARBY_OFFICIAL_PLACE",None,0
    if viable:
        # Two possible matched settlements sharing name and nearby coordinates?
        others=[x for x in viable[1:] if x[1]>=candidate[1]-.035 and x[0]<=candidate[0]+3]
        if others: status="AMBIGUOUS_IDENTITY"
        elif candidate[0]<=5 and candidate[1]>=.85:status="INDEPENDENT_MATCH"
        else: status="POSSIBLE_MATCH_REVIEW"
    else:status="DISTANCE_ONLY_REVIEW"
    return status,candidate,len(near)
def find_world(city,geo):
    near=[]
    for y in range(math.floor(city["lat"])-1, math.floor(city["lat"])+2):
        for x in range(math.floor(city["lon"])-1, math.floor(city["lon"])+2):
            for lat,lon,name,ascii_name,country,geoid,alternates in geo.get((y,x),()):
                if abs(lat-city["lat"])>.3 or abs(lon-city["lon"])>.4:continue
                dist=geo_distance_km(city["lat"],city["lon"],lat,lon)
                if dist>25:continue
                sim=max(score_names(city["en"],name),score_names(city["en"],ascii_name))
                # GeoNames alternative names contain published variants including Cyrillic.
                for alias in alternates:
                    if not alias or len(alias)>65:continue
                    sim=max(sim,score_names(city["en"],alias),score_names(city["ru"],alias))
                    if sim>=1.0:break
                near.append((dist,sim,name,geoid,lat,lon,country))
    near.sort(key=lambda x:(-x[1],x[0]))
    good=[x for x in near if x[1]>=.80 and x[0]<=10]
    candidate=good[0] if good else min(near,key=lambda x:x[0],default=None)
    if not candidate:return "NO_NEARBY_GLOBAL_PLACE",None,0
    if good:
        others=[x for x in good[1:] if x[1]>=candidate[1]-.04 and x[0]<=candidate[0]+5]
        status="AMBIGUOUS_IDENTITY" if others else (
            "INDEPENDENT_MATCH" if candidate[0]<=7 and candidate[1]>=.85
            else "POSSIBLE_MATCH_REVIEW")
    else:status="DISTANCE_ONLY_REVIEW"
    # Some legacy entries name both a city and a specific neighborhood.
    # A nearby district with a similar *city* name is not proof that the
    # designated neighborhood was independently verified.
    qualifier = city["en"].split("(", 1)[1].split(")", 1)[0] if "(" in city["en"] and ")" in city["en"] else ""
    if status == "INDEPENDENT_MATCH" and qualifier and normalize(qualifier) not in normalize(candidate[2]):
        status = "SUBLOCALITY_NAME_REVIEW"
    return status,candidate,len(near)
def write_tsv(path,rows,columns):
    path.parent.mkdir(parents=True,exist_ok=True)
    with path.open("w",encoding="utf-8",newline="") as fh:
        writer=csv.DictWriter(fh,fieldnames=columns,delimiter="\t",extrasaction="ignore")
        writer.writeheader();writer.writerows(rows)
def run(core,uk_file,global_file,out):
    catalog=load_catalog(core)
    assert len(catalog) == 337, "Curated catalog must contain exactly 337 entries"
    ua=load_ua(uk_file)
    geo=load_geonames(global_file)
    results=[]
    for city in catalog:
        if city["kind"]=="ua":
            status,candidate,n=find_ua(city,ua)
            provider="KATOTTG government names + OSM center"
        else:
            status,candidate,n=find_world(city,geo)
            provider="GeoNames populated places (cities500)"
        row={
            **city, "provider":provider,"status":status,
            "candidate":candidate[2] if candidate else "",
            "candidate_code":candidate[3] if candidate else "",
            "reference_lat":candidate[4] if candidate else "",
            "reference_lon":candidate[5] if candidate else "",
            "distance_km":round(candidate[0],3) if candidate else "",
            "name_similarity":round(candidate[1],3) if candidate else "",
            "nearby_count":n,"reference_match":candidate[6] if candidate else ""
        }
        results.append(row)
    cols=["kind","id","en","ru","uk","oblast","lat","lon","zone","provider","status","candidate",
          "candidate_code","reference_lat","reference_lon","distance_km","name_similarity","nearby_count",
          "reference_match","line"]
    write_tsv(out/"geodata_selected_catalog.tsv",results,cols)
    proposed=[r for r in results if r["kind"]=="ua" and not r["uk"] and r["status"]=="INDEPENDENT_MATCH"]
    write_tsv(out/"reviewed_ukrainian_name_candidates.tsv",proposed,cols)
    exceptions=[r for r in results if r["status"]!="INDEPENDENT_MATCH"]
    write_tsv(out/"geodata_requires_review.tsv",exceptions,cols)
    counts=Counter((r["kind"],r["status"]) for r in results)
    summary={
        "catalog_total":len(catalog),"ukrainian":sum(r["kind"]=="ua" for r in results),
        "world_legacy":sum(r["kind"]=="legacy" for r in results),
        "ukrainian_new_name_candidates":len(proposed),
        "statuses":{f"{k[0]}/{k[1]}":v for k,v in sorted(counts.items())},
        "ukraine_source":UA_SOURCE,"ua_source_commit":UA_SOURCE_COMMIT,
        "geonames_source":GLOBAL_SOURCE,
        "ua_dataset_sha256":hashlib.sha256(uk_file.read_bytes()).hexdigest(),
        "global_dataset_sha256":hashlib.sha256(global_file.read_bytes()).hexdigest(),
        "limits":"A matched city and region plus OSM centroid validates independent provenance at a 5-7 km threshold; it cannot certify a single building, civil clock policy, religious jurisdiction or authoritative street address. Mismatches are never silently corrected."
    }
    (out/"summary.json").write_text(json.dumps(summary,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    print(json.dumps(summary,indent=2,ensure_ascii=False))
if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--core",type=Path,default=Path("core/src/main/kotlin/app/mychitas/peleshaon/domain"))
    p.add_argument("--ukraine",type=Path,required=True)
    p.add_argument("--geonames",type=Path,required=True)
    p.add_argument("--out",type=Path,default=Path("build/geo-audit"))
    args=p.parse_args()
    run(args.core,args.ukraine,args.geonames,args.out)
