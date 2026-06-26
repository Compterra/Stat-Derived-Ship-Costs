# Economy Baseline

This file records the vanilla pricing frame this mod should preserve unless we deliberately rebalance it.

## Vanilla Inputs

From `starsector-core/data/config/settings.json`:

| Setting | Vanilla | Use |
|---|---:|---|
| `shipBuyPriceMult` | `1.2` | Market purchase markup before tariff |
| `shipSellPriceMult` | `0.4` | Normal sale payout baseline |
| `hullWithDModsSellPriceMult` | `0.2` | Sale payout baseline for D-modded hulls |
| `shipWeaponBuyPriceMult` | `1.2` | Weapon purchase markup |
| `shipWeaponSellPriceMult` | `0.5` | Weapon sale payout |
| `fighterLPCBaseValueMult` | `1.0` | LPC base value load multiplier |
| `shipWeaponBaseValueMult` | `1.0` | Weapon base value load multiplier |
| `blueprintPriceOriginalShipMult` | `0.5` | Ship blueprint value multiplier |
| `shipProductionCostBase` | `1000` | Flat production floor |
| `productionCostMult` | `1.0` | Production cost multiplier |
| `storageFreeFraction` | `0.01` | Monthly storage fee fraction of stored value |

## Economy Mapping

The formula should produce one internal appraisal number. Think of it as a ship-price guide for the current hull in front of the buyer, not a receipt for what the owner spent on it:

```text
observed_member_stat_value = computed_hull_value + computed_loadout_value
computed_member_value = observed_member_stat_value * condition_value_mult
local_market_member_value = computed_member_value * market_context_mult
listed_member_value = local_market_member_value * market_channel_mult
sale_offer_value = local_market_member_value * sale_channel_mult
```

S-mod investment has no default face-value premium. Any value from S-mods should appear through better final stats, freed OP, or the resulting loadout. D-mods are different: they affect stats and also apply a visible condition discount because the market treats damaged hulls as riskier assets.

`computed_member_value` is the stable guide value. `local_market_member_value` is the local asking-price value after fuel prices, economic cycle, supply, demand, and rarity are considered. `listed_member_value` is the storefront price for a specific submarket channel. `sale_offer_value` is what that channel is willing to pay the player.

Then vanilla economy surfaces map those values differently:

```text
open_market_buy_price = listed_member_value * shipBuyPriceMult * (1 + effective_tariff)
military_market_buy_price = listed_member_value * shipBuyPriceMult * (1 + effective_tariff)
black_market_buy_price = listed_member_value * shipBuyPriceMult
vanilla_style_normal_sell_price = listed_member_value * shipSellPriceMult * max(0, 1 - effective_tariff)
vanilla_style_d_mod_sell_price = listed_member_value * hullWithDModsSellPriceMult * max(0, 1 - effective_tariff)
channel_sale_offer = sale_offer_value * sell_surface_mult * max(0, 1 - effective_tariff_if_legal)
ship_blueprint_price = blueprint_size_floor + computed_hull_value * blueprintPriceOriginalShipMult
production_cost = shipProductionCostBase + computed_member_value * productionCostMult
monthly_storage_fee = computed_member_value * storageFreeFraction
```

The intended ship-sale model should prefer `channel_sale_offer`. The vanilla-style sell prices are useful as comparison points and fallback behavior if a hook cannot intercept a specific sale surface.

Tariffs are submarket/market state. The formula should not bake tariff into ship value.

Production and storage should usually use stable guide value, not the local asking-price value. Markets can overpay or discount a hull in the moment, but that should not make the actual manufacturing recipe or storage fee whipsaw every time fuel spikes.

## Market Context

The market layer should follow a few real-world patterns:

| Market force | Ship-market result |
|---|---|
| Expensive fuel | High fuel-per-light-year ships get cheaper; efficient ships hold value |
| Booming economy | More ships may be available, but fewer good deals because buyers have cash |
| Recession | Common hulls discount harder as owners liquidate assets |
| War demand | Combat ships, carriers, troop transports, tankers, and logistics hulls rise |
| Surplus after conflict | Common military hulls get cheaper |
| Rare/prestige hull | Rarity protects value and raises the D-mod discount floor |

This layer should be applied conservatively. A market shock should make the ship feel locally mispriced, not overwrite the core appraisal.

## Market Channels

The channel layer sits on top of local market context.

| Channel | Pricing identity |
|---|---|
| Open market | Public, competitive, predictable, and tax-visible |
| Military market | Restricted procurement channel with relation and commission discounts |
| Black market | Cheap, risky, no official tax, with stronger rarity bidding |

Open markets should mostly feel like the expected legal price. A commission with the owning faction can reduce or erase the effective tax/markup, representing official support, paperwork shortcuts, and quartermaster favor.

Military markets should be better than public acquisition for military-grade ships once the player has access, but not automatically cheap. Higher relations produce larger discounts. Civilian ships should be very cheap here because they are not what the military channel is trying to preserve.

Black markets should feel affordable, sometimes suspiciously so. Rarity matters more there because buyers are already searching for restricted hulls, prototypes, phase ships, and faction-locked stock. Common damaged ships can be bargains; rare damaged ships still get bid up.

## Sale Channels

Sale channels answer a different question than storefront channels: "What will this counter pay the player right now?"

| Channel | Sale identity |
|---|---|
| Open market | Legal dealers protect resale margin, so player profit is rare |
| Military market | Buys useful ships, especially faction-matching and doctrine-compatible hulls |
| Black market | Fast liquidity, accepts trash, and pays meaningfully for rare/questionable hulls |

Open-market buyers care about what they can resell at a profit. Normal ships should usually sell for less than the player could buy them for, after taxes and markup. Rare ships can improve the offer, but legal dealers still protect margin.

Military buyers care less about generic resale and more about whether the hull fits their fleet. Faction hulls, doctrine-compatible combat ships, tankers, troop transports, and useful logistics ships should get better offers. Civilian hulls that do not serve the faction are low-priority.

Black-market buyers provide quick money and tolerate damaged or dubious inventory. They will take bad hulls for cheap change, but rare, restricted, phase, prototype, or faction-locked hulls should draw better offers because the buyer knows someone wants them.

## Known Implementation Constraint

`ShipHullSpecAPI` exposes `getBaseValue()` but not a public setter. A runtime mod that changes all hull values may need one of these paths:

1. Generate merged `ship_data.csv` rows before game load. Stable for hull stat value, weak for per-variant loadout value.
2. Patch loaded hull specs by reflection. Stronger coverage, but needs runtime verification against the concrete Starsector class.
3. Intercept specific economy surfaces such as custom production, custom markets, or custom sale flows. Safer locally, incomplete globally.

The first implementation should probably start with a report/calibration tool, then a generated CSV mode for hull values. Loadout pricing can come second as a variant-aware surcharge once we confirm where vanilla includes or omits fitted equipment in `FleetMemberAPI.getBaseValue()`.
