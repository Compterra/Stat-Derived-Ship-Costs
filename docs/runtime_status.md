# Runtime Status

Version `1.0.0` is the first stable release build.

## Default Behavior

LunaLib `Pricing mode` defaults to `Full Market`.

In that mode the mod:

- loads `formula.json`
- applies LunaLib overrides
- evaluates every loaded hull spec
- attempts to apply computed hull base values to market-eligible hull specs
- registers a submarket listener that reprices listed ships using visible member D-mods and buy-channel storefront rules
- registers a transaction guard that corrects completed ship buy values when submarket tab repricing lags, and applies open/military/black sale-channel payouts
- writes `sdsc_hull_value_report.csv` to `Starsector/saves/common`

## Hull Price Application

In all non-`Report Only` modes, the plugin attempts to apply computed hull base values to market-eligible hull specs.

D-hull specs no longer get a separate `.skin` value penalty. Built-in and visible D-mod hullmods are counted by the `dmod` hullmod tag, and D-hull skins remain priceable even when their skin uses `hide_in_codex`, which catches damaged skins such as `Eagle (D)` during the current hull-spec pass.

Generated market D-hulls can show visible D-mod marks on the listing while exposing no built-in D-mod hullmods on the hull spec. The live submarket listener now reads the listed `FleetMemberAPI` variant, counts the exact visible D-mods, and reapplies that listing's damaged hull value before card prices are calculated. The D-mod penalty is size-aware, so larger hulls are not hit as hard per mark as frigates. The hull-spec report still uses the Luna-tunable `D-hull minimum D-mod count` fallback when no listed member exists.

In `Full Market` mode the same listener applies buy-channel multipliers before setting the hull spec value:

- open market uses the legal baseline and commission tax relief when commissioned with the owning faction
- military market discounts combat hulls by relation/commission and discounts civilian hulls harder
- black market applies risk/base discounts, then lets rarity push special hulls back up

Starsector still applies its normal buy-card math afterward. The mod is changing the storefront-adjusted hull value beneath that surface, not replacing the whole cargo UI pricing calculation.

## Market Safety

LunaLib can make the ship economy easier, but the runtime applies hard safety rails after Luna overrides:

- buy-channel hull-value multipliers are clamped between `safety.buyChannelMinMult` and `safety.buyChannelMaxMult`
- completed ship purchases are checked against the transaction submarket; if a stale tab made the player overpay, the excess is refunded
- completed ship sales are priced from the transaction submarket's sale channel and capped against the sold ship's exact stat-derived appraisal, including its visible D-mod count
- if a sale pays above `appraised_value * safety.shipSalePayoutCapMult`, the transaction guard subtracts the excess credits and logs a debug sample

Default safety keeps the cheapest guarded buy channel at `0.55x` appraisal before Starsector's normal buy multiplier and the guarded sale payout cap at `0.62x` appraisal. This keeps damaged bargain ships useful without allowing a buy-cheap/sell-high loop caused by changing submarket prices.

Implementation detail:

```text
ShipHullSpecAPI has getBaseValue()
Starsector 0.98a-RC8 loads hull specs as com.fs.starfarer.loading.specs.g
that concrete class has setBaseValue(float)
the plugin calls setBaseValue(float) directly because Starsector blocks reflection from scripts
```

If the concrete hull spec implementation changes, the mod logs the failure and keeps running. Set LunaLib `Pricing mode` to `Report Only` to generate reports without changing prices.

A new save is not required, but Starsector must be fully restarted after replacing the jar. Existing LunaLib saved settings can also override changed defaults until LunaLib's selected-mod reset is used or the setting is changed manually.

## Post-1.0 Limits

The current runtime build changes hull base value, has a submarket member pass for exact D-mod counts and open/military/black buy-channel pricing, and corrects completed ship sale transactions with open/military/black sale-channel payouts. It does not yet hook:

- per-variant loadout price changes
- pre-click sale-card display replacement for open/military/black sale-channel offers
- production or blueprint-specific pricing hooks

Those remaining layers are designed and tunable, but are intentionally outside the 1.0 stabilization scope.

## Build

From the mod folder:

```text
.\build.ps1
```

The jar is written to:

```text
jars/stat-derived-ship-costs.jar
```
