# Calibration Notes

Calibration command:

```text
py tools\calibrate_formula.py
```

The script currently reads vanilla hull rows from:

```text
C:\Games\Starsector\starsector-core\data\hulls\ship_data.csv
```

It excludes stations, modules, unboardable hulls, restricted/special enemies, no-sell hulls, and the shuttle pod. It parses `.ship` files for real hull size and `civgrade`.

## Current Vanilla Fit

Last run:

| Hull size | Count | Median formula / vanilla | Median absolute log error |
|---|---:|---:|---:|
| Frigate | 24 | `0.92` | `0.37` |
| Destroyer | 19 | `0.88` | `0.32` |
| Cruiser | 20 | `0.94` | `0.19` |
| Capital | 13 | `1.04` | `0.06` |

This is the 1.0 clean-hull calibration baseline. The formula is much closer to vanilla after adding:

- hull-size calibration multipliers
- civilian-grade discount
- FRONT/OMNI shield geometry valuation
- separate phase cloak valuation from phase cost/upkeep
- explicit premium for `phaseteleporter`

This calibration is still a clean-hull baseline. Variant-aware appraisal needs to run on real `FleetMemberAPI` or variant data so it can see D-mods, S-mods, final OP allocation, and fitted equipment.

## Current Overestimates

Largest overestimates from the last run:

| Hull | Vanilla | Estimate | Ratio | Likely reason |
|---|---:|---:|---:|---|
| `hound` | `7500` | `15000` | `2.00` | Combat freighter should probably get partial civilian discount |
| `prometheus` | `80000` | `150700` | `1.88` | Bulk logistics weights high |
| `shrike` | `20000` | `35800` | `1.79` | Mobility premium may be high for destroyers |
| `lasher` | `9000` | `15700` | `1.74` | Frigate OP/combat floor still high |
| `revenant` | `80000` | `134800` | `1.69` | Phase logistics hull needs special handling |
| `cerberus` | `9000` | `15000` | `1.67` | Combat freighter should probably get partial civilian discount |

## Current Underestimates

Largest underestimates from the last run:

| Hull | Vanilla | Estimate | Ratio | Likely reason |
|---|---:|---:|---:|---|
| `ox` | `16000` | `3000` | `0.19` | Tug role needs unique logistics multiplier |
| `monitor` | `25000` | `11400` | `0.46` | Fortress shield system not valued yet |
| `harbinger` | `120000` | `57100` | `0.48` | Phase ship system value not modeled yet |
| `crig` | `20000` | `10000` | `0.50` | Salvage gantry role needs utility value |
| `shade` | `40000` | `20400` | `0.51` | Phase ship system value not modeled yet |
| `scarab` | `30000` | `15900` | `0.53` | Temporal shell not valued yet |
| `tempest` | `36000` | `19600` | `0.54` | Drone/system package not valued yet |
| `aurora` | `200000` | `112900` | `0.56` | Plasma jets/high-tech cruiser premium missing |
| `medusa` | `60000` | `36500` | `0.61` | Phase skimmer/high-tech premium missing |

## Next Math Pass

The next pass should add a ship-system valuation table instead of trying to solve these outliers through global weights.

Start with these manual system bands:

| System id/example | Proposed value or multiplier |
|---|---:|
| `fortressshield` | add `18000` frigate/destroyer, `35000` cruiser/capital |
| `temporalshell` | multiply by `1.8` |
| `terminatorcore` / built-in drone package | add `12000` |
| `plasmajets` | multiply by `1.35` |
| `phasecloak` defense | value from `shield type = PHASE`, `defense id = phasecloak`, phase cost, and phase upkeep |
| `displacer`, `displacer_degraded`, `skimmer_drone` | phase skimmer/displacer mobility value |
| `phaseteleporter` | high phase mobility value; keep current multiplier until it becomes additive system value |
| `salvage_gantry` / `drone_pd` utility systems | add role-specific utility value |

After system valuation, parse `.ship` weapon slots. The current calibration does not include weapon-slot value yet, so high-tech combat ships with compact but premium slot layouts are still under-modeled.

## Variant Appraisal Pass

The mod should behave like an in-space ship value guide:

```text
observed_member_stat_value = computed_hull_value + computed_loadout_value
computed_member_value = observed_member_stat_value * condition_value_mult
```

Recommended first pass:

| Feature | Treatment |
|---|---|
| D-mod count | Apply a harsh condition multiplier after final stat/loadout value, with softer per-mark scaling as hull size increases |
| D-hull skin | Do not apply a separate skin penalty; live submarket pricing reads exact visible D-mods from the listed member |
| Severe structural D-mods | Apply a small extra condition hit, capped to avoid over-penalizing twice |
| D-mod stat losses | Let them reduce value naturally through final observed stats |
| S-mods | No default face premium |
| S-mod stat/loadout impact | Value only the final end-state stats, freed OP, and resulting loadout |

This keeps the formula buyer-facing. A seller does not get reimbursed for story points; they get paid for the ship's current performance and condition.

## Market Context Pass

After the clean hull and variant appraisal are stable, add a local market layer:

```text
local_market_member_value = computed_member_value * market_context_mult
```

Recommended factors:

| Factor | First-pass behavior |
|---|---|
| Fuel price | Discount high fuel-per-light-year ships when fuel is expensive |
| Economic boom | Slightly higher asking prices; good deals become harder to find |
| Recession/crash | Common hulls discount harder |
| War mobilization | Combat and logistics demand rises |
| Post-war surplus | Common military hulls discount, rare hulls hold value better |
| Local supply | Ship gluts create deals |
| Local demand | Requested roles become pricier |
| Rarity/prestige | Raises value and softens the D-mod floor |

Use real-world vehicle and industrial equipment behavior as the guide: operating costs affect demand, cash-rich markets reduce bargain availability, and scarce prestige assets retain value even when damaged.

## Market Channel Pass

After local market context, apply the storefront layer:

```text
listed_member_value = local_market_member_value * market_channel_mult
```

Recommended first-pass behavior:

| Channel | Behavior |
|---|---|
| Open market | Competitive, predictable legal pricing; commission can reduce effective tax/markup |
| Military market | Restricted access; military hulls are better than open-market acquisition, relation improves discounts |
| Military civilian stock | Very cheap surplus because the channel does not value civilian hulls highly |
| Black market | Cheap base prices, no official tax, uncertain provenance |
| Black-market rarity | Rare and restricted hulls retain stronger premiums because everyone is hunting the good stuff |

Useful scenario tests:

| Scenario | Expected result |
|---|---|
| Neutral player buying common open-market frigate | Near local market value plus normal legal markup |
| Commissioned high-relation player buying faction combat hull | Noticeably cheaper than open market |
| Same player buying civilian hauler from military market | Stupid cheap, assuming it appears there at all |
| Black-market common D-modded destroyer | Very affordable |
| Black-market rare D-modded phase hull | Discounted for damage but still expensive due to scarcity |

## Sale Channel Pass

Selling should use a separate channel layer:

```text
sale_offer_value = local_market_member_value * sale_channel_mult
```

Recommended first-pass behavior:

| Channel | Behavior |
|---|---|
| Open market | Protects dealer margin; player rarely profits from ordinary resale |
| Military market | Pays best for faction-matching and doctrine-compatible hulls |
| Military civilian purchase | Low offers unless the hull has useful logistics value |
| Black market | Accepts trash for quick money |
| Black-market rarity | Pays meaningfully for rare, restricted, phase, or faction-locked hulls |

Useful sale scenario tests:

| Scenario | Expected result |
|---|---|
| Player sells common open-market frigate | Conservative offer below likely resale price |
| Player sells normal D-modded destroyer on open market | Bad offer because legal dealers dislike damaged stock |
| Player sells faction cruiser to matching military market | Stronger offer than open market |
| Player sells civilian freighter to military market | Weak offer unless that faction needs logistics hulls |
| Player sells junk D-modded hull on black market | Low but immediate cash |
| Player sells rare D-modded phase hull on black market | Better than legal channels despite damage |
