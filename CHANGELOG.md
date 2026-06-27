# Changelog

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
