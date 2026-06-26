# LunaLib Setting Map

`data/config/LunaSettings.csv` exposes player-facing levers. Runtime code should read these through LunaLib and apply them over `data/config/stat_derived_ship_costs/formula.json`.

## General

| Luna setting | Formula/default target |
|---|---|
| `sdsc_enable_pricing` | `runtime.enablePricingHooks` |
| `sdsc_pricing_mode` | `runtime.pricingMode` |
| `sdsc_debug_reports` | `runtime.debugReports` |

## Appraisal

| Luna setting | Formula/default target |
|---|---|
| `sdsc_hull_value_mult` | `runtime.hullValueMult` |
| `sdsc_loadout_value_mult` | `runtime.loadoutValueMult` |
| `sdsc_dmod_discount_per_mod` | `condition.dModDiscountPerMod` |
| `sdsc_dmod_scale_frigate` | `condition.dModDiscountPerModSizeMult.FRIGATE` |
| `sdsc_dmod_scale_destroyer` | `condition.dModDiscountPerModSizeMult.DESTROYER` |
| `sdsc_dmod_scale_cruiser` | `condition.dModDiscountPerModSizeMult.CRUISER` |
| `sdsc_dmod_scale_capital` | `condition.dModDiscountPerModSizeMult.CAPITAL_SHIP` |
| `sdsc_dmod_discount_floor` | `condition.dModDiscountFloor` |
| `sdsc_dhull_min_dmods` | `condition.dHullMinimumDModCount` |
| `sdsc_rare_dmod_floor` | `marketContext.rareHullDModFloor` |
| `sdsc_smod_face_value` | `condition.sModFaceValueFraction` |

## Market Context

| Luna setting | Formula/default target |
|---|---|
| `sdsc_market_context_strength` | `runtime.marketContextStrength` |
| `sdsc_fuel_pressure` | `runtime.enableFuelPressure` |
| `sdsc_fuel_sensitivity` | `marketContext.fuelPriceSensitivity` |
| `sdsc_demand_sensitivity` | `marketContext.demandSensitivity` |
| `sdsc_supply_sensitivity` | `marketContext.supplySensitivity` |
| `sdsc_rarity_sensitivity` | `marketContext.rarityValueSensitivity` |

## Buy Channels

| Luna setting | Formula/default target |
|---|---|
| `sdsc_open_buy_base` | `marketChannels.OPEN.baseMult` |
| `sdsc_open_commission_relief` | `marketChannels.OPEN.commissionTaxReliefMax` |
| `sdsc_military_combat_base` | `marketChannels.MILITARY.militaryHullBaseMult` |
| `sdsc_military_civilian_base` | `marketChannels.MILITARY.civilianHullBaseMult` |
| `sdsc_military_relation_discount` | `marketChannels.MILITARY.relationDiscountMax` |
| `sdsc_military_commission_discount` | `marketChannels.MILITARY.commissionDiscount` |
| `sdsc_black_buy_base` | `marketChannels.BLACK.baseMult` |
| `sdsc_black_buy_rarity` | `marketChannels.BLACK.rarityPremiumSensitivity` |
| `sdsc_black_risk_discount` | `marketChannels.BLACK.riskDiscountMult` |
| `sdsc_black_buy_dmod_tolerance` | `marketChannels.BLACK.dModToleranceBonus` |

## Sale Channels

| Luna setting | Formula/default target |
|---|---|
| `sdsc_open_sale_base` | `saleChannels.OPEN.baseMult` |
| `sdsc_open_sale_dmod_penalty` | `saleChannels.OPEN.dModExtraPenaltyPerMod` |
| `sdsc_open_profit_guard` | `saleChannels.OPEN.profitGuardMult` |
| `sdsc_military_sale_base` | `saleChannels.MILITARY.baseMult` |
| `sdsc_military_faction_bonus` | `saleChannels.MILITARY.factionMatchBonus` |
| `sdsc_military_relation_bonus` | `saleChannels.MILITARY.relationBonusMax` |
| `sdsc_military_civilian_penalty` | `saleChannels.MILITARY.civilianHullPenalty` |
| `sdsc_black_sale_base` | `saleChannels.BLACK.baseMult` |
| `sdsc_black_trash_floor` | `saleChannels.BLACK.trashFloor` |
| `sdsc_black_sale_rarity` | `saleChannels.BLACK.rarityOfferSensitivity` |

## Compatibility

| Luna setting | Formula/default target |
|---|---|
| `sdsc_enable_nexerelin_context` | `runtime.compatibility.enableNexerelinContext` |
| `sdsc_enable_starship_legends_rarity` | `runtime.compatibility.enableStarshipLegendsRarity` |
| `sdsc_enable_final_stat_read` | `runtime.compatibility.preferFinalStatRead` |
| `sdsc_enable_console_debug` | `runtime.compatibility.enableConsoleDebugHooks` |
