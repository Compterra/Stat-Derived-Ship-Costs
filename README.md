# Stat-Derived Ship Costs

Release build for a Starsector pricing mod that replaces hand-authored ship credit values with a transparent formula.

Current scope:

- derive hull value from hull stats
- reserve hooks for variant/loadout value from weapons, fighters, vents, capacitors, and hullmods
- appraise the current ship state like an in-space price guide: D-mods heavily reduce market value, while S-mods only matter through final stats and loadout
- apply local market context such as fuel prices, economic cycle, supply, demand, and hull rarity to transaction prices
- apply storefront/channel pricing for open market, military market, and black market access
- apply separate sale-channel offers so open markets protect margin, militaries prefer faction-fit hulls, and black markets pay for liquidity and rarity
- expose the major economy levers through LunaLib while keeping expert coefficients in JSON
- map the result back onto vanilla economy multipliers for market buy and market sell
- keep blueprint, storage, production, and exact loadout pricing documented for future passes

Version `1.0.1` defaults to LunaLib `Full Market` mode, evaluates loaded hull specs, applies the hull base-value pricing pass, registers a submarket listener for exact listed D-mod counts and buy-channel storefront pricing, registers a transaction guard against stale-tab buy/sell cheese, applies open/military/black sale-channel payouts, and writes `sdsc_hull_value_report.csv` to `Starsector/saves/common`.

Current project files:

- `docs/economy_baseline.md` records the vanilla economy multipliers.
- `docs/formula_draft.md` defines the current hull and planned loadout formula.
- `docs/calibration_notes.md` records the latest vanilla fit and outliers.
- `docs/library_plan.md` records required and optional mod-library integrations.
- `docs/lunalib_setting_map.md` maps LunaLib field ids to formula config paths.
- `docs/runtime_status.md` records what the current jar does and does not hook yet.
- `data/config/stat_derived_ship_costs/formula.json` stores the tunable coefficients.
- `data/config/LunaSettings.csv` exposes the player/modpack-facing LunaLib levers.
- `tools/calibrate_formula.py` runs the current offline vanilla calibration.

Build command:

```text
.\build.ps1
```

TriOS and Version Checker:

- `data/config/version/version_files.csv` points at `stat_derived_ship_costs.version`.
- `stat_derived_ship_costs.version` points at the raw online master file on GitHub.
- The current Version Checker download URL points at the repository copy: `https://github.com/Compterra/Stat-Derived-Ship-Costs/archive/refs/heads/main.zip`.
