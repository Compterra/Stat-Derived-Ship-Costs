# Formula Reference

Goal: replace arbitrary hull credit values with a formula that still lands in vanilla economy scale.

The design target is an in-space Kelly Blue Book: value is based on what the ship is worth in its current, observable state. Buyers and sellers should care about the final stats, fitted equipment, and visible condition risk, not the historical cost of upgrades.

The formula is split into appraisal stages:

```text
observed_member_stat_value = computed_hull_value + computed_loadout_value
computed_member_value = observed_member_stat_value * condition_value_mult
local_market_member_value = computed_member_value * market_context_mult
listed_member_value = local_market_member_value * market_channel_mult
sale_offer_value = local_market_member_value * sale_channel_mult
```

`computed_hull_value` is for the current hull stats. `computed_loadout_value` is for the specific variant: fitted weapons, LPCs, vents, capacitors, and hullmods. `condition_value_mult` handles D-mod market stigma and repair risk.

`local_market_member_value` is the local asking-price guide. It should move with the space economy while leaving the intrinsic ship appraisal intact.

`listed_member_value` is the front-counter sticker price after submarket access rules, faction relationship, commission status, and black-market risk.

`sale_offer_value` is what the same channel is willing to pay the player. Selling is not just buying in reverse: dealers protect margin, militaries care about doctrine fit, and black markets value liquidity and rare contraband differently.

Important principle: S-mods do not add much face value by themselves. If an S-mod improves the ship's final stats or effectively frees OP for a better end-state loadout, that value is captured through the final stats/loadout. The appraisal should not reimburse the seller for story-point investment.

## Stage 1: Hull Value

1.0 hull formula:

```text
hull_value =
    hull_size_floor
  + durability_value
  + flux_value
  + mobility_value
  + ordnance_value
  + weapon_slot_value
  + fighter_bay_value
  + defense_value
  + logistics_value
  + ship_system_value
```

Initial size floors:

| Hull size | Floor |
|---|---:|
| Frigate | `3000` |
| Destroyer | `10000` |
| Cruiser | `30000` |
| Capital | `80000` |

1.0 components:

```text
durability_value = hitpoints * 0.45 + armor * armor_size_mult
armor_size_mult = frigate 8, destroyer 14, cruiser 24, capital 38

flux_value = max_flux * 0.20 + flux_dissipation * 16

mobility_value =
  (max_speed * speed_size_mult)
  + (acceleration + deceleration) * 5
  + (max_turn_rate + turn_acceleration) * 8

speed_size_mult = frigate 90, destroyer 120, cruiser 170, capital 260

ordnance_value = ordnance_points * 450

fighter_bay_value = fighter_bays * 12000
```

Defense value:

```text
if shield_type is FRONT or OMNI:
    shield_arc_factor = clamp(shield_arc / 180, 0.25, 2.0)
    efficiency_factor = clamp(1.0 / shield_efficiency, 0.45, 2.5)
    upkeep_factor = clamp(1.25 - shield_upkeep / max(1, flux_dissipation), 0.5, 1.25)
    geometry_factor = shield_geometry_mult[shield_type]
    defense_value = shield_size_base * shield_arc_factor * efficiency_factor * upkeep_factor * geometry_factor

if shield_type is PHASE and defense_id is phasecloak:
    phase_cost_factor = clamp(0.06 / max(0.01, phase_cost), 0.6, 2.0)
    phase_upkeep_factor = clamp(0.06 / max(0.01, phase_upkeep), 0.6, 2.0)
    defense_value = phase_cloak_size_base * phase_cost_factor * phase_upkeep_factor

if shield_type is NONE:
    defense_value = 0
```

Initial shield size bases:

| Hull size | Shield base |
|---|---:|
| Frigate | `2500` |
| Destroyer | `6000` |
| Cruiser | `14000` |
| Capital | `32000` |

Initial shield geometry multipliers:

| Shield type | Mult | Notes |
|---|---:|---|
| `FRONT` | `1.00` | Directional shield; arc carries most of the value |
| `OMNI` | `1.20` | Extra tactical value from fast omnidirectional coverage |

Initial phase cloak size bases:

| Hull size | Phase cloak base |
|---|---:|
| Frigate | `12000` |
| Destroyer | `30000` |
| Cruiser | `85000` |
| Capital | `220000` |

Weapon slot value should reflect opportunity, not fitted equipment:

```text
weapon_slot_value =
  sum(slot_base_by_size_and_type * slot_mount_mult * slot_arc_factor)
```

Initial slot bases:

| Slot size | Base |
|---|---:|
| Small | `900` |
| Medium | `3200` |
| Large | `11000` |

Initial mount multipliers:

| Mount type | Mult |
|---|---:|
| Ballistic | `1.00` |
| Energy | `1.08` |
| Missile | `0.95` |
| Synergy/Composite/Hybrid | `1.18` |
| Universal | `1.30` |
| Decorative/System | `0.00` |

Logistics value:

```text
logistics_value =
    cargo * 45
  + fuel * 35
  + max(0, max_crew - min_crew) * 25
  - fuel_per_ly * 350
  - supplies_per_month * 900
  - supplies_to_recover * 150
```

Ship system value is intentionally table-driven. It should not include shield geometry or the basic value of a phase cloak; those are part of `defense_value`.

Start with these default bands:

| System class | Value |
|---|---:|
| No ship system | `0` |
| Mobility/utility | `4000` |
| Defensive | `7000` |
| Offensive | `9000` |
| Phase skimmer/displacer | `9000` |
| Phase teleporter | `35000` |
| Unique/supercapital | manual override |

The phase split matters:

| Phase type | Source fields | Pricing treatment |
|---|---|---|
| Phase cloak | `shield type = PHASE`, usually `defense id = phasecloak` | Main defensive system, priced in `defense_value` from phase cost/upkeep |
| Phase skimmer/displacer | `system id = displacer`, `displacer_degraded`, `skimmer_drone`, similar | Mobility system, priced in `ship_system_value` |
| Phase teleporter | `system id = phaseteleporter` | High-end mobility system, priced separately from cloak/skimmer |

Finally:

```text
calibrated_hull_value =
  hull_value
  * hull_size_value_mult
  * civilian_or_tech_mult
  * special_system_mult

computed_hull_value = round_to_nearest_100(max(hull_size_floor, calibrated_hull_value))
```

Skin identity should not apply an extra price penalty. D-hulls are valued by their actual visible D-mod count, not by the fact that the hull spec is presented through a degraded skin.

Initial calibration multipliers:

| Modifier | Value |
|---|---:|
| Frigate hull-size multiplier | `0.32` |
| Destroyer hull-size multiplier | `0.45` |
| Cruiser hull-size multiplier | `0.68` |
| Capital hull-size multiplier | `1.00` |
| Civilian-grade hull multiplier | `0.45` |
| Phase cloak multiplier | `1.00`; phase value now comes from `phase_cloak_size_base` and phase cost/upkeep |
| Phase teleporter multiplier | `5.50` until system value replaces this calibration shortcut |

These are not flavor multipliers; they are calibration knobs. The first vanilla pass showed that an uncompressed stat formula overprices cheap frigates and destroyers while underpricing rare phase-tech hulls. Phase cloak used to have a broad multiplier, but that double-counts once cloak value is modeled as defense from phase cost/upkeep.

## Stage 2: Loadout Value

Loadout value should reward expensive equipment without double-counting the hull's weapon slots.

```text
loadout_value =
    weapon_value
  + fighter_lpc_value
  + flux_upgrade_value
  + hullmod_value
```

Planned loadout components:

```text
weapon_value = sum(weapon_spec.base_value * weapon_condition_mult)
fighter_lpc_value = sum(fighter_wing_spec.base_value)
flux_upgrade_value = vents * 250 + capacitors * 180
hullmod_value = sum(non_built_in_hullmod.base_value * 0.20)
s_mod_face_value = 0 by default
```

S-mod treatment:

| Source of value | Treatment |
|---|---|
| Better final stats from the S-mod | Captured by recomputing/appraising final stats |
| Freed OP that enables better weapons, vents, caps, or hullmods | Captured by `computed_loadout_value` |
| Story point investment or prestige | No default resale value |
| Optional collector/quality premium | Keep tiny, capped, and disabled by default |

## Stage 3: Condition Value

D-mods should tank value because they are visible condition problems, not just because they are stat changes. A buyer sees repair risk, degraded reliability, and reduced resale confidence. The stat losses themselves are reflected in `observed_member_stat_value`; the D-mod condition multiplier then prices the market stigma.

```text
d_mod_size_scale = d_mod_discount_per_mod_size_mult[hull_size]
d_mod_floor = d_mod_discount_floor_by_size[hull_size]
d_mod_count_mult = clamp(1.0 - d_mod_count * d_mod_discount_per_mod * d_mod_size_scale, d_mod_floor, 1.0)
structural_d_mod_mult = 1.0 - min(structural_d_mod_count * structural_d_mod_extra_penalty, max_structural_d_mod_extra_penalty)
condition_value_mult = d_mod_count_mult * structural_d_mod_mult

computed_member_value = observed_member_stat_value * condition_value_mult
sell_price_uses = hullWithDModsSellPriceMult if any dmods else shipSellPriceMult
```

D-hull skin status does not apply a separate discount. The broad hull-spec pass still assumes at least `d_hull_minimum_d_mod_count` D-mods for generated D-hulls when no member listing exists, but the live submarket pass reads the exact visible D-mod count from each `FleetMemberAPI` listing and uses that value instead. This lets a one-D-mod Prometheus stay a rough but usable tanker while a five-D-mod Eagle falls into salvage-auction pricing.

Initial condition knobs:

| Modifier | Value | Notes |
|---|---:|---|
| D-mod discount per mod | `0.20` | Five D-mods should feel like a salvage-title hull |
| D-mod discount size scale | Frigate `1.00`, destroyer `0.90`, cruiser `0.80`, capital `0.70` | Larger hulls retain more value per D-mod because the intact asset is still expensive |
| D-mod discount floor | `0.05` | Common five-D-mod hulls are almost scrap, with loadout carrying most of the visible price |
| D-mod discount floor by size | Frigate `0.05`, destroyer `0.05`, cruiser `0.05`, capital `0.22` | Capital wrecks should not collapse to frigate-style scrap fractions |
| D-hull minimum D-mod count | `4.0` | Report-only fallback for generated D-hulls when no member listing is available |
| Damaged hull floor fraction | `0.02` | Clean size floors should not protect wrecked hulls from salvage pricing |
| Structural D-mod extra penalty | `0.08` | Extra hit for damage that implies expensive core repairs |
| Max structural extra penalty | `0.20` | Prevent double-counting severe stat losses too hard |
| S-mod face-value premium | `0.00` | S-mods matter through final stats/loadout instead |

## Stage 4: Market Context Value

The ship market should behave like real vehicle, aircraft, and industrial equipment markets. Fuel shocks, freight costs, credit conditions, war demand, and scarcity change the transaction price without changing what the ship physically is.

```text
market_context_mult =
    fuel_price_mult
  * economic_cycle_mult
  * supply_pressure_mult
  * local_demand_mult
  * rarity_prestige_mult

local_market_member_value = computed_member_value * market_context_mult
```

The base guide value remains `computed_member_value`. Market context is applied when pricing a specific transaction at a specific market.

Fuel price pressure:

```text
fuel_price_index = local_fuel_price / sector_baseline_fuel_price
fuel_exposure = clamp(fuel_per_ly / hull_size_reference_fuel_per_ly, 0.0, 2.0)
fuel_price_mult = clamp(
    1.0 - max(0, fuel_price_index - 1.0) * fuel_exposure * fuel_price_sensitivity,
    fuel_price_discount_floor,
    fuel_price_premium_cap
)
```

Expensive fuel can make thirsty ships cheaper because their future operating cost rises. Fuel-efficient ships should not become wildly expensive from the same shock; they just hold value better.

Economic cycle:

| Cycle state | Effect |
|---|---|
| Boom | More hulls may appear, but good deals are harder to find because buyers have cash |
| Normal | No adjustment |
| Recession/crash | Common hulls discount harder as owners liquidate assets |
| War mobilization | Combat hulls, carriers, tankers, and troop/logistics hulls gain demand |
| Post-war surplus | Common military hulls get cheaper; rare prestige hulls retain value better |

Supply and demand:

```text
supply_pressure_mult = clamp(1.0 - local_supply_surplus * supply_sensitivity, 0.85, 1.10)
local_demand_mult = clamp(1.0 + local_demand_score * demand_sensitivity, 0.90, 1.25)
```

Scarcity and prestige:

```text
rarity_prestige_mult = 1.0 + rarity_score * rarity_value_sensitivity
effective_d_mod_floor = lerp(d_mod_discount_floor, rare_hull_d_mod_floor, rarity_score)
```

Rare hulls should keep prestige and value even with D-mods. A damaged Doom, Astral, phase hull, XIV hull, or faction-locked prototype should still attract buyers because replacing it is hard. Rarity does not erase D-mod penalties, but it raises the floor and makes condition discounts less brutal.

Initial market knobs:

| Modifier | Value | Notes |
|---|---:|---|
| Fuel price sensitivity | `0.18` | Strong enough for fuel shocks to matter |
| Fuel discount floor | `0.80` | Expensive fuel hurts operating-cost-heavy hulls, but does not erase value |
| Fuel premium cap | `1.05` | Efficient ships hold value rather than spike |
| Demand sensitivity | `0.12` | Local demand changes asking price |
| Supply sensitivity | `0.10` | Gluts create deals |
| Rarity value sensitivity | `0.25` | Rare hulls carry prestige/scarcity value |
| Rare hull D-mod floor | `0.45` | Rare damaged hulls retain more value than common damaged hulls |

Market context should be conservative by default. The intrinsic formula already handles hull quality; this stage is for transaction weather.

## Stage 5: Market Channel Value

This is the most visible layer. A buyer does not just buy from "the economy"; they buy from a storefront with access rules, taxes, risk, faction policy, and different inventory quality.

```text
market_channel_mult =
    channel_base_mult
  * access_discount_mult
  * relation_discount_mult
  * commission_discount_mult
  * channel_rarity_mult

listed_member_value = local_market_member_value * market_channel_mult
```

Open market:

| Trait | Treatment |
|---|---|
| Access | Public |
| Price feel | Competitive and predictable |
| Inventory | Civilian and legal military spillover |
| Commission effect | Owning-faction commission can shave down tax/markup |
| Rarity effect | Moderate; rare hulls are expensive, but open markets are price-visible |

Military market:

| Trait | Treatment |
|---|---|
| Access | Faction relationship and/or commission gated |
| Price feel | Military hulls start pricey, but better than public open-market acquisition |
| Inventory | Better access to military-grade and faction doctrine hulls |
| Relation effect | Higher relationship gives higher discount |
| Commission effect | Strong discount; the faction wants its officer equipped |
| Civilian hulls | Very cheap because they are unwanted surplus in a military procurement channel |

Black market:

| Trait | Treatment |
|---|---|
| Access | Public but risky |
| Price feel | Cheap enough to feel stolen |
| Inventory | Erratic, includes restricted and questionable-origin hulls |
| Tax | No official tax |
| Rarity effect | Strong; everyone is hunting for the good stuff, so rare hulls keep a premium |
| Condition | D-mods are tolerated, but sellers know rare damaged hulls still have buyers |

Vanilla black-market effects:

| Effect | In-game behavior | Pricing implication |
|---|---|---|
| Tariff | Black market tariff is `0`; legal markets still apply tariff on top of the listed value | Black-market ships should already look cheaper before any extra mod discount |
| Access posture | Legal trade wants transponder-on docking; black-market trade is safest when the transponder is off | The discount is partly payment for operating outside normal legal protection |
| Suspicion | Recent black-market and hostile-market trade value is compared against total recent local trade value and `10000 * market size` | Large ship deals create heat when identifiable, especially on small markets |
| Transponder | With vanilla `transponderOffMarketAwarenessMult = 0`, trading with the transponder off avoids the normal market-awareness value | Do not over-penalize black-market sticker prices; the player can manage risk by being sneaky |
| Scans | Suspicion can make patrol cargo scans escalate even without obvious contraband | A big bargain is fair because the hidden cost is patrol attention, not sticker price |
| Economy impact | Submarkets use player-sell-only economy impact for commodity supply/demand | Ship sale pricing should be its own channel model rather than mirroring commodity shortages |

This means black-market buying should be genuinely attractive, especially for common or damaged hulls, but not automatically the cheapest source for prestige hulls. A clean rare cruiser can still get bid up because everyone knows it can be flipped, while a battered common hull should feel like a "take it before someone asks questions" deal.

Initial channel formulas:

```text
open_market_mult =
    1.00
  * commission_tax_relief_mult

military_market_mult =
    hull_role_channel_mult
  * relation_discount_mult
  * commission_discount_mult

black_market_mult =
    black_market_base_mult
  * black_market_rarity_mult
  * black_market_risk_discount_mult
  * damaged_hull_tolerance_mult
```

Initial channel knobs:

| Modifier | Value | Notes |
|---|---:|---|
| Open market base | `1.00` | Competitive legal sticker before vanilla tariff |
| Commission tax relief | up to `0.18` | Good enough to shave most or all sales tax in friendly space |
| Military combat-hull base | `0.92` | Better than open market, but not a fire sale |
| Military civilian-hull base | `0.55` | Civilian hulls are surplus/no-priority stock |
| Military relation discount cap | `0.25` | High relations matter |
| Military commission discount | `0.12` | Additional faction-service discount |
| Black market base | `0.78` | Affordable enough to feel dubious |
| Black market rarity premium sensitivity | `0.35` | Rare hulls get bid up harder here |
| Black market risk discount | `0.95` | Small discount for legal risk and uncertain provenance |
| Black market D-mod tolerance bonus | `0.08` | Damaged hulls are slightly less toxic to black-market sellers |

Channel order matters:

```text
appraised_hull_value
  -> live member D-mod correction
  -> market channel hull-value adjustment
  -> vanilla buy-card surface
```

The channel layer should decide where the player found the ship, not what the ship fundamentally is.

## Stage 6: Sale Channel Value

Selling is the cousin of the storefront layer, but it needs different behavior. A merchant buying from the player is thinking about resale, storage, legality, and how quickly the hull can be moved.

```text
sale_channel_mult =
    sale_base_mult
  * condition_liquidity_mult
  * faction_fit_mult
  * rarity_sale_mult
  * relation_sale_mult

sale_offer_value = local_market_member_value * sale_channel_mult
```

Open-market selling:

| Trait | Treatment |
|---|---|
| Dealer goal | Buy below expected resale value |
| Player profit | Rare unless the player exploits major market differences |
| Common hulls | Conservative offers |
| Rare hulls | Better offers, but still dealer-margin limited |
| D-mods | Penalized because legal dealers worry about warranty, storage, and resale confidence |

Open-market dealers should almost never let the player make easy profit on a normal buy/sell loop. They are legal resellers with overhead, taxes, paperwork, and enough information to avoid overpaying.

Military-market selling:

| Trait | Treatment |
|---|---|
| Buyer goal | Strengthen faction stock and doctrine |
| Faction hulls | Best military offers |
| Allied/compatible doctrine hulls | Decent offers |
| Enemy or awkward doctrine hulls | Reduced offers unless rare |
| Civilian hulls | Low offers except useful logistics hulls |
| Relation/commission | Improves offers, especially for matching hulls |

The military will buy ships, but it cares most about hulls it can actually use, maintain, and assign to its doctrine. A faction-matching cruiser should get a much better offer than a random civilian hauler.

Black-market selling:

| Trait | Treatment |
|---|---|
| Buyer goal | Move inventory fast, no questions asked |
| Trash hulls | Accepted for quick change |
| D-mods | More tolerated than legal markets |
| Rare hulls | Strong offers because the buyer knows they can flip them |
| Restricted/faction hulls | Often better than open-market offers |
| Risk | Still discounted because provenance and heat matter |

The black market is where the player goes to turn ships into money quickly. It should not always pay the most for ordinary hulls, but it should be the obvious place to sell rare, restricted, questionable, or beat-up ships.

The in-game risk is mostly tied to recent identifiable trade value, not to whether the deal was a buy or a sale. For sale offers, that suggests:

| Sale case | Direction |
|---|---|
| Common clean hull | Pays less than open-market resale value; the buyer needs margin |
| Common D-modded hull | Pays enough to be useful because disposal is the service |
| Rare clean hull | Pays strongly because the buyer can flip it quietly |
| Rare D-modded hull | Still pays decently; rarity protects value, but damage and heat keep it below clean |
| Very expensive hull | Should not get a runaway offer, because the transaction itself creates a lot of attention if identifiable |

Initial sale formulas:

```text
open_sale_mult =
    open_sale_base_mult
  * open_condition_mult
  * open_rarity_offer_mult

military_sale_mult =
    military_sale_base_mult
  * faction_fit_mult
  * relation_sale_mult
  * commission_sale_mult

black_sale_mult =
    black_sale_base_mult
  * black_condition_tolerance_mult
  * black_rarity_offer_mult
  * black_quick_cash_mult
```

Initial sale knobs:

| Modifier | Value | Notes |
|---|---:|---|
| Open sale base | `0.58` | Dealer margin keeps player profit rare |
| Open D-mod extra penalty | `0.06` | Legal dealers dislike damaged stock |
| Open rarity offer sensitivity | `0.12` | Rare ships improve offers but do not erase margin |
| Military sale base | `0.62` | Better than open when the hull is useful |
| Military faction-match bonus | `0.20` | Matching doctrine/faction hulls matter |
| Military relation bonus cap | `0.15` | High relations improve procurement offers |
| Military commission bonus | `0.08` | Commission helps, but does not make dumping random hulls optimal |
| Military civilian penalty | `0.30` | Civilian hulls are low-priority purchases |
| Black sale base | `0.66` | Better liquidity than legal dealers |
| Black trash floor | `0.25` | They will accept junk for quick change |
| Black D-mod tolerance | `0.05` | D-mods hurt less here |
| Black rarity offer sensitivity | `0.35` | Rare ships get meaningful offers |
| Black quick-cash discount | `0.95` | Fast, quiet money still has a cut |

Sale offers should use `local_market_member_value`, not `listed_member_value`. A shop's buy price is based on what it thinks the hull is locally worth and how it can resell/use it, not on the exact sticker price it would charge the player.

## Stage 7: Anti-Cheese Guardrails

The mod should let players make the ship economy easier through LunaLib without turning normal buy/sell loops into an exploit. Because the current runtime changes hull spec base values under Starsector's normal cargo UI, the same hull can otherwise be bought from a discounted channel and sold after another market reprices that spec upward.

Runtime safety rails:

```text
guarded_buy_channel_mult = clamp(raw_buy_channel_mult, buy_channel_min_mult, buy_channel_max_mult)

sale_payout_cap =
    exact_visible_member_appraisal
  * ship_sale_payout_cap_mult
```

Completed transactions get one last validation pass using the transaction's actual submarket. If tab switching leaves a hull spec stamped with the previous submarket's value, the guard rescales completed ship buys back toward the current submarket's guarded value. Buy corrections only refund overpayment; they do not surprise-charge the player above the visible card price.

Completed ship sales use the channel sale model directly:

```text
open_sale_payout =
    exact_visible_member_appraisal
  * open_sale_base
  * open_profit_guard
  * d_mod_legal_dealer_penalty
  * rarity_offer_bonus

military_sale_payout =
    exact_visible_member_appraisal
  * military_sale_base
  * relation_and_commission_bonus
  * civilian_priority_penalty
  * rarity_offer_bonus

black_sale_payout =
    exact_visible_member_appraisal
  * black_sale_base
  * quick_cash_mult
  * d_mod_tolerance_bonus
  * rarity_offer_bonus
```

Sale corrections can move up or down, then still respect the anti-cheese sale cap.

Initial safety knobs:

| Modifier | Value | Notes |
|---|---:|---|
| Buy channel minimum multiplier | `0.55` | Keeps maximum Luna discounts generous but above protected resale |
| Buy channel maximum multiplier | `1.35` | Lets rarity and hostile markets raise prices without exploding sale math |
| Ship sale payout cap | `0.62` | Completed ship sales above this cap are trimmed after the transaction |

The cap uses the sold member's exact D-mod count, so a D-modded hull cannot be bought as damaged and sold as if it were clean just because another market currently has a clean listing.

## Runtime Tuning

The mod should expose player/modpack-facing levers through LunaLib while keeping the full coefficient set in `formula.json`.

Runtime precedence:

```text
code defaults
  -> data/config/stat_derived_ship_costs/formula.json
  -> LunaLib saved settings
  -> optional debug/runtime override
```

LunaLib should tune layer strengths and major market behaviors:

| LunaLib tab | Purpose |
|---|---|
| General | Enable hooks, choose pricing mode, write diagnostics |
| Appraisal | Global hull/loadout scale, D-mod floor, rare-D-mod protection, S-mod face value |
| Market Context | Fuel pressure, demand/supply sensitivity, rarity value |
| Buy Channels | Open, military, and black-market sticker behavior |
| Sale Channels | Open, military, and black-market offer behavior |
| Compatibility | Optional context from Nexerelin, Starship Legends, final stat reads, and console debug hooks |

Do not expose every raw hull-stat coefficient by default. Those remain expert controls in `formula.json`. LunaLib should answer "what kind of economy do I want?" rather than forcing players to tune armor and flux weights by hand.

Settings changed through LunaLib should affect future appraisals immediately where safe. Existing market listings may need a market refresh, save reload, or explicit reprice command depending on the final hook surface.

Current 1.0 runtime status: the jar defaults to `Full Market`, evaluates loaded hull specs, applies computed hull base values for market-eligible hulls, corrects live listed D-mod counts in submarkets, applies open/military/black buy-channel hull-value multipliers before Starsector's normal buy-card math, and corrects completed ship sale transactions with open/military/black sale-channel payouts. Variant loadout pricing, pre-click sale-card display replacement, production, and blueprint pricing are post-1.0 hooks.

## Calibration Targets

The first pass should target vanilla median values by hull size rather than exact one-for-one equality.

Good first target:

```text
median_formula_value_by_size ~= median_vanilla_base_value_by_size
80% of vanilla hulls should land within +/- 35%
outliers should be explainable by rare tech, unique systems, civilian logistics, or visible D-mod counts
```

Once the curve is stable, add manual overrides only for true exceptions.
