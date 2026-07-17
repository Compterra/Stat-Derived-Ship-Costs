# Changelog

## Version 1.1.4

- Completes the D-hull baseline fix by updating the LunaLib default and runtime settings fallback from four assumed D-mods to one.

## Version 1.1.3

- Reduces the generic D-hull baseline from four assumed D-mods to one, preventing excessive capital-ship devaluation while preserving exact member-level D-mod counting.

## Version 1.1.2

- Propagates stat-derived component values into variant-held hull-spec copies used by modular storefront members.
- Removes the ineffective full-market member refresh, avoiding thousands of unnecessary stat updates during game load.

## Version 1.1.1

- Adds an enabled-by-default LunaLib option to force-refresh existing storefront and storage fleet members after pricing changes, invalidating cached assembled modular-ship values.

## Version 1.1.0

- Prices modular ships at the component level: core hull specs receive core stat values and every referenced module hull spec receives its own stat-derived value.
- Uses the assembled core-plus-module total for transaction appraisal without double-counting modules in storefront pricing.

## Version 1.0.9

- Removes the declared base-value floor from active SDSC pricing. Modular hulls are valued only from core and module statistics; declared values are retained solely for restoring vanilla prices when SDSC is disabled.

## Version 1.0.8

- Fixes canonical modular-variant selection and prevents SHIP_WITH_MODULES hulls from being valued below their declared base when module data is incomplete.

## Version 1.0.7

- Fixes modular valuation for variants that declare modules through Starsector's station-module map, including Farsight Drive's Equilibrium.

## Version 1.0.6

- Prices modular hulls as their assembled vessel by including installed canonical module hulls. This fixes Farsight Drive's Equilibrium and other SHIP_WITH_MODULES ships being valued as core hulls only.
- Restores the declared ship_data.csv base value when SDSC is disabled, repairing saves where older releases had cached a market-specific value as the hull baseline.

## Version 1.0.5

- Fixes LunaLib enable/disable handling: disabling SDSC now restores every hull's original base value instead of leaving a prior pricing pass active.
- Stops writing market- and D-mod-specific values into globally shared hull specifications. This removes cross-market price contamination and stale tooltip prices.
- Removes the static submarket signature cache and repeated full-economy repricing pass, eliminating the campaign-object retention path and substantial load-time JSON/log spam.
- Keeps market-context adjustments in the transaction guard, where the actual market and individual ship condition are available.

## Version 1.0.4

- Repackaged the Version Checker download with current 1.0.4 release metadata.

## Version 1.0.3

- Prevents free player-storage transfers from being treated as ship sales and granting credits.

## Version 1.0.2

- Makes cargo-update repricing change-aware so external market viewers such as Stellar Networks do not trigger repeated repricing when the ship roster is unchanged.
- Avoids creating submarket cargo from passive cargo-update callbacks; explicit market open, transaction, load, and settings-change paths still force pricing updates.

## Version 1.0.1

- Registers `SDSCTransactionGuard` as a transient campaign listener so the mod can be removed from existing saves.
- Converts any previously persistent transaction guard instance to the transient form on load.
- Debounces cargo-update repricing to avoid external market UI modules triggering a reprice every frame.

## Version 1.0.0

- Initial public release.
- Derives ship value from hull stats, visible D-mod condition, local market context, and market channel.
- Adds LunaLib settings for the major economy levers.
- Adds storefront pricing for open, military, and black markets.
- Adds sale-channel payouts and transaction protection against stale market-tab pricing.
- Writes hull value reports to `Starsector/saves/common/sdsc_hull_value_report.csv`.
