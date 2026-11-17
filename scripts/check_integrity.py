#!/usr/bin/env python3
"""Offline source-tree integrity gate for the independent Peleshaon Beta fork."""
from __future__ import annotations
from pathlib import Path
import sys
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
LOCALES = ("values", "values-ru", "values-uk", "values-de", "values-iw")

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def check(cond: bool, message: str) -> None:
    if not cond:
        raise AssertionError(message)

def resource_keys(folder: str, module: str = "app") -> set[str]:
    root = ET.parse(ROOT / module / "src" / "main" / "res" / folder / "strings.xml").getroot()
    keys: list[str] = []
    for node in root:
        if node.tag in ("string", "plurals", "string-array"):
            name = node.get("name")
            if name:
                keys.append(f"{node.tag}:{name}")
    check(len(keys) == len(set(keys)), f"Duplicate resource keys in {module}/{folder}")
    return set(keys)

def format_args(text: str) -> list[tuple[int, str]]:
    # Android resources permit numbered arguments and implicit %s/%d; normalize both.
    out: list[tuple[int, str]] = []
    for match in re.finditer(r"%(?:([1-9][0-9]*)\$)?([sdf])", text):
        out.append((int(match.group(1)) if match.group(1) else len(out) + 1, match.group(2)))
    return sorted(out)

def check_format_args(module: str) -> None:
    base = ROOT / module / "src" / "main" / "res"
    defs = {}
    for locale in LOCALES:
        root = ET.parse(base / locale / "strings.xml").getroot()
        defs[locale] = {node.get("name"): format_args("".join(node.itertext()))
                        for node in root if node.tag == "string"}
    for locale in LOCALES[1:]:
        for key, arguments in defs["values"].items():
            check(defs[locale][key] == arguments,
                  f"{module}/{locale}/{key}: format arguments differ from default")

def main() -> None:
    resources = {folder: resource_keys(folder) for folder in LOCALES}
    for folder in LOCALES[1:]:
        missing = resources["values"] - resources[folder]
        extra = resources[folder] - resources["values"]
        check(not missing and not extra,
              f"{folder}: missing={sorted(missing)}, extra={sorted(extra)}")
    check_format_args("app")
    wear_resources = {folder: resource_keys(folder, "wear") for folder in LOCALES}
    for folder in LOCALES[1:]:
        missing = wear_resources["values"] - wear_resources[folder]
        extra = wear_resources[folder] - wear_resources["values"]
        check(not missing and not extra,
              f"wear/{folder}: missing={sorted(missing)}, extra={sorted(extra)}")
    check_format_args("wear")
    for module in ("app", "wear"):
        gradle = read(f"{module}/build.gradle.kts")
        check('applicationId = "app.mychitas.peleshaon.beta"' in gradle,
              f"{module}: unsafe applicationId, would overwrite original")
        check("versionCode = 4" in gradle, f"{module}: unexpected version")
        manifest = ET.parse(ROOT / module / "src" / "main" / "AndroidManifest.xml")
        namespace = "{http://schemas.android.com/apk/res/android}"
        app = manifest.getroot().find("application")
        check(app is not None and app.get(namespace + "label") == "Pele Shaon Beta",
              f"{module}: Beta label missing")
    engine = read("core/src/main/kotlin/app/mychitas/peleshaon/domain/ZmanimEngine.kt")
    check("ZmanType.SOF_ZMAN_SHMA to shmaFrom(cal)" in engine and
          "shmaFrom(cal(date, city))" in engine,
          "Shma deadline and alarm no longer share one community formula")
    guard = read("core/src/main/kotlin/app/mychitas/peleshaon/alarm/ShabbatGuard.kt")
    check("LocalTime.of(14, 0)" not in guard and "LocalTime.of(23, 0)" not in guard,
          "Invented polar quiet-time cutoffs reappeared")
    prefs = read("app/src/main/java/app/mychitas/peleshaon/Prefs.kt")
    check("fun migrateKashrut" in prefs and "remove(\"kashrut_hours\")" in prefs,
          "Legacy configurable kashrut settings survived migration")
    check("for (suffix in listOf(" in prefs and '.remove("wake_mode_$id")' in prefs,
          "Custom alarms could inherit deleted settings")
    scheduler = read("app/src/main/java/app/mychitas/peleshaon/alarm/AlarmScheduler.kt")
    check("includeSnooze = true" in scheduler and "AlarmClockInfo(triggerMillis, showIntent)" in scheduler,
          "Alarm cancel/showIntent regression")
    check("Prefs.kashrutHours" not in scheduler and "KashrutPolicy.deadline" in scheduler,
          "Kashrut must always use exactly six hours")
    service = read("app/src/main/java/app/mychitas/peleshaon/alarm/AlarmRingService.kt")
    check("private var activeSession:" in service and "EXTRA_SESSION" in service and
          "startActivity(fullScreenIntent)" not in service and "pendingAlarms.enqueue(" in service,
          "Ringing-session isolation or background launch regression")
    check("private fun isQuietNow" in service and "nextQuietStart" in service,
          "Shabbat protection missing in the ringing service")
    check("getQuietStart(today, cityName, lat, lon, zoneId, earlyShabbatMinutes)" in guard,
          "Early Friday silence not used by ShabbatGuard")
    cal = read("app/src/main/java/app/mychitas/peleshaon/ui/CalendarSheet.kt")
    check("ZmanimEngine.havdalahOn(d.date, city)" in cal and
          "CandleTransition.BEFORE_SUNSET" in cal and
          "ZmanimEngine.END_MIN" not in cal,
          "Calendar candle/havdalah transition regression")
    check("fun candleMinutesFor(cityName: String)" in engine,
          "Local candle-minute policy missing")
    wear = read("wear/src/main/java/app/mychitas/peleshaon/wear/WearActivity.kt")
    check("WatchRing(cityNow, half, halfItems, next)" in wear,
          "Wear halachic half-day ring regression")
    check("selectedCommunity" in wear and "selectedCity" in wear,
          "Wear OS independent community/city selectors are missing")
    print(f"OK: phone {len(resources['values'])} keys, Wear {len(wear_resources['values'])} keys × {len(LOCALES)} languages")
    print("OK: Beta package/manifest, Shma formula, alarm isolation, polar fallback")

if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"INTEGRITY ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
