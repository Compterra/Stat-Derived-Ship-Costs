# Library Plan

This mod should keep a small required dependency set and use soft integrations for richer market context.

## Required

| Library | Why |
|---|---|
| LunaLib | In-game settings UI for the pricing levers in `data/config/LunaSettings.csv` |

Local installed id: `lunalib`.

LunaLib already depends on LazyLib in this install, so a direct LazyLib dependency is only needed if our runtime code uses LazyLib APIs directly. LazyLib's local installed id is `lw_lazylib`.

## Strong Optional Integrations

| Library/mod | Use if present | Hard dependency? |
|---|---|---|
| Nexerelin (`nexerelin`) | Better war, faction, invasion, diplomacy, and commission context for market demand and faction-fit pricing | No |
| Console Commands (`lw_console`) | Developer commands such as `sdsc_value`, `sdsc_market`, `sdsc_reload`, and `sdsc_explain` | No |
| Starship Legends | Reputation/fame as an extra rarity and prestige signal | No |
| Ship Mastery System / Progressive S-Mods | Prefer final observed stats after mastery or S-mod systems alter ship performance | No |
| DMODServices | Compatibility testing for unusual D-mod/S-mod workflows | No |

## Probably Not Required

| Library | Reason |
|---|---|
| MagicLib (`MagicLib`) | Useful general modding library, but this mod does not yet need MagicLib-specific APIs |
| GraphicsLib | No rendering-heavy features planned |
| Particle Engine | No combat visuals planned |

## Runtime Strategy

Start with:

```text
LunaLib hard dependency
formula.json default coefficients
LunaSettings.csv player-facing overrides
optional soft checks for installed context mods
```

The first implementation should not require Nexerelin, Starship Legends, Console Commands, or Ship Mastery System. Instead, runtime code should detect whether each mod is loaded and only activate that integration when available.

## LunaLib Setting Policy

Expose:

- global appraisal scale
- D-mod and rare-hull condition behavior
- market-context strength
- fuel, demand, supply, and rarity sensitivities
- buy-channel multipliers
- sale-channel multipliers
- compatibility toggles

Keep in `formula.json`:

- low-level hull stat weights
- hull-size calibration multipliers
- shield and phase cloak bases
- weapon slot bases
- system valuation bands
- rarity tag weights

This gives modpack authors and players understandable levers while preserving a full expert config for deeper balancing.
