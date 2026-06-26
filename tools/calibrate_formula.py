#!/usr/bin/env python3
"""Quick offline calibration for the stat-derived hull formula.

Run from the mod folder:

    py tools/calibrate_formula.py

The script reads vanilla `ship_data.csv` from the local Starsector install and
prints formula error by hull size. It intentionally ignores weapon-slot and
ship-system value for now; those need API/ship-file parsing in the next pass.
"""

from __future__ import annotations

import csv
import json
import math
from pathlib import Path
from statistics import median


MOD_ROOT = Path(__file__).resolve().parents[1]
STARSECTOR_ROOT = MOD_ROOT.parents[1]
CORE = STARSECTOR_ROOT / "starsector-core"
FORMULA_PATH = MOD_ROOT / "data" / "config" / "stat_derived_ship_costs" / "formula.json"
SHIP_DATA = CORE / "data" / "hulls" / "ship_data.csv"
HULL_DIR = CORE / "data" / "hulls"

EXCLUDED_HULL_IDS = {
    "shuttlepod",
}

EXCLUDED_TOKEN_SUBSTRINGS = {
    "HIDE_IN_CODEX",
    "MODULE",
    "STATION",
    "UNBOARDABLE",
    "no_dealer",
    "no_sell",
    "restricted",
    "monster",
    "module_hull_bar_only",
    "threat",
    "dweller",
    "omega",
}


def as_float(row: dict[str, str], key: str, default: float = 0.0) -> float:
    value = (row.get(key) or "").strip()
    if not value:
        return default
    try:
        return float(value)
    except ValueError:
        return default


def round_to_100(value: float) -> int:
    return int(round(value / 100.0) * 100)


def load_hull_data() -> dict[str, dict]:
    hull_data: dict[str, dict] = {}
    for path in HULL_DIR.glob("*.ship"):
        try:
            data = json.loads(path.read_text(encoding="utf-8-sig"))
        except Exception:
            continue
        hull_id = data.get("hullId")
        hull_size = data.get("hullSize")
        if hull_id and hull_size:
            hull_data[hull_id] = {
                "hullSize": hull_size,
                "builtInMods": set(data.get("builtInMods") or []),
            }
    return hull_data


def is_market_hull(row: dict[str, str], hull_data: dict[str, dict]) -> bool:
    hull_id = (row.get("id") or "").strip()
    if not hull_id or hull_id in EXCLUDED_HULL_IDS or hull_id not in hull_data:
        return False
    tokens = " ".join(
        [
            row.get("hints") or "",
            row.get("tags") or "",
            row.get("logistics n/a reason") or "",
        ]
    )
    return not any(token in tokens for token in EXCLUDED_TOKEN_SUBSTRINGS)


def defense_value(row: dict[str, str], size: str, formula: dict) -> float:
    shield_type = (row.get("shield type") or "").strip().upper()
    if not shield_type or shield_type == "NONE":
        return 0.0
    if shield_type == "PHASE":
        if (row.get("defense id") or "").strip() != "phasecloak":
            return 0.0
        phase_cost = as_float(row, "phase cost", 0.06)
        phase_upkeep = as_float(row, "phase upkeep", 0.06)
        phase_cost_factor = max(0.6, min(2.0, 0.06 / max(0.01, phase_cost)))
        phase_upkeep_factor = max(0.6, min(2.0, 0.06 / max(0.01, phase_upkeep)))
        return formula["phaseCloakSizeBase"][size] * phase_cost_factor * phase_upkeep_factor

    base = formula["shieldSizeBase"][size]
    arc = as_float(row, "shield arc")
    upkeep = as_float(row, "shield upkeep")
    efficiency = as_float(row, "shield efficiency", 1.0)
    dissipation = max(1.0, as_float(row, "flux dissipation"))

    arc_factor = max(0.25, min(2.0, arc / 180.0))
    efficiency_factor = max(0.45, min(2.5, 1.0 / max(0.1, efficiency)))
    upkeep_factor = max(0.5, min(1.25, 1.25 - upkeep / dissipation))
    geometry_factor = formula["shieldGeometryMult"].get(shield_type, 1.0)
    return base * arc_factor * efficiency_factor * upkeep_factor * geometry_factor


def estimate_hull_value(row: dict[str, str], hull_data: dict, formula: dict) -> int:
    size = hull_data["hullSize"]
    weights = formula["weights"]
    value = float(formula["hullSizeFloor"][size])

    value += as_float(row, "hitpoints") * weights["hitpoints"]
    value += as_float(row, "armor rating") * formula["armorSizeMult"][size]
    value += as_float(row, "max flux") * weights["maxFlux"]
    value += as_float(row, "flux dissipation") * weights["fluxDissipation"]
    value += as_float(row, "ordnance points") * weights["ordnancePoints"]
    value += as_float(row, "fighter bays") * weights["fighterBay"]

    value += as_float(row, "max speed") * formula["speedSizeMult"][size]
    value += as_float(row, "acceleration") * weights["acceleration"]
    value += as_float(row, "deceleration") * weights["deceleration"]
    value += as_float(row, "max turn rate") * weights["maxTurnRate"]
    value += as_float(row, "turn acceleration") * weights["turnAcceleration"]

    value += defense_value(row, size, formula)

    value += as_float(row, "cargo") * weights["cargo"]
    value += as_float(row, "fuel") * weights["fuel"]
    value += max(0.0, as_float(row, "max crew") - as_float(row, "min crew")) * weights["crewRange"]
    value -= as_float(row, "fuel/ly") * weights["fuelPerLightYearPenalty"]
    value -= as_float(row, "supplies/mo") * weights["suppliesPerMonthPenalty"]
    value -= as_float(row, "supplies/rec") * weights["suppliesToRecoverPenalty"]

    calibration = formula["calibration"]
    value *= calibration["hullSizeValueMult"][size]

    if "civgrade" in hull_data["builtInMods"]:
        value *= calibration["civgradeValueMult"]

    if (row.get("shield type") or "").strip().upper() == "PHASE":
        value *= calibration["phaseCloakValueMult"][size]

    system_id = (row.get("system id") or "").strip()
    value *= calibration["systemIdValueMult"].get(system_id, 1.0)

    return max(int(formula["hullSizeFloor"][size]), round_to_100(value))


def main() -> int:
    formula = json.loads(FORMULA_PATH.read_text())
    hull_data_by_id = load_hull_data()
    rows = []
    with SHIP_DATA.open(newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            if not is_market_hull(row, hull_data_by_id):
                continue
            if not (row.get("id") or "").strip():
                continue
            vanilla = as_float(row, "base value")
            if vanilla <= 0:
                continue
            hull_data = hull_data_by_id[row["id"]]
            size = hull_data["hullSize"]
            estimate = estimate_hull_value(row, hull_data, formula)
            rows.append((size, row["id"], vanilla, estimate, estimate / vanilla))

    print(f"Read {len(rows)} vanilla hull rows from {SHIP_DATA}")
    print()
    for size in ["FRIGATE", "DESTROYER", "CRUISER", "CAPITAL_SHIP"]:
        bucket = [r for r in rows if r[0] == size]
        if not bucket:
            continue
        ratios = [r[4] for r in bucket]
        abs_log_error = [abs(math.log(max(0.01, r))) for r in ratios]
        print(
            f"{size:12} count={len(bucket):3d} "
            f"median_ratio={median(ratios):5.2f} "
            f"median_abs_log_error={median(abs_log_error):5.2f}"
        )

    print()
    print("Largest overestimates:")
    for _, hull_id, vanilla, estimate, ratio in sorted(rows, key=lambda r: r[4], reverse=True)[:10]:
        print(f"  {hull_id:28} vanilla={vanilla:8.0f} estimate={estimate:8.0f} ratio={ratio:5.2f}")

    print()
    print("Largest underestimates:")
    for _, hull_id, vanilla, estimate, ratio in sorted(rows, key=lambda r: r[4])[:10]:
        print(f"  {hull_id:28} vanilla={vanilla:8.0f} estimate={estimate:8.0f} ratio={ratio:5.2f}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
