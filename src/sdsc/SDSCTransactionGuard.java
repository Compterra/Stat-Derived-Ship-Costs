package sdsc;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.apache.log4j.Logger;

import java.util.List;

public class SDSCTransactionGuard extends BaseCampaignEventListener {
    private static final Logger LOG = Global.getLogger(SDSCTransactionGuard.class);
    private static final float PRICE_EPSILON = 1f;

    public SDSCTransactionGuard() {
        super(false);
    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        if (transaction == null || !hasShipTransactions(transaction)) {
            return;
        }

        // Storage and other free-transfer submarkets can report moved ships as
        // "sold". They must never be repriced or credited as market sales.
        SubmarketAPI submarket = transaction.getSubmarket();
        if (submarket == null || submarket.getPlugin() == null
                || !submarket.getPlugin().isParticipatesInEconomy()) {
            return;
        }

        SDSCSettings settings = SDSCSettings.load();
        if (!settings.enablePricingHooks || "Report Only".equalsIgnoreCase(settings.pricingMode)) {
            return;
        }
        if (!settings.getBoolean("safety", "enableTransactionGuard", true)) {
            return;
        }

        SDSCFormula formula = new SDSCFormula(settings);
        SDSCMarketPricing marketPricing = new SDSCMarketPricing(settings);
        Adjustment adjustment = new Adjustment();

        correctBoughtShips(transaction, formula, marketPricing, settings, adjustment);
        correctSoldShips(transaction, formula, marketPricing, settings, adjustment);

        if (Math.abs(adjustment.creditDelta) > PRICE_EPSILON) {
            transaction.setCreditValue(transaction.getCreditValue() + adjustment.creditDelta);
            adjustPlayerCredits(adjustment.creditDelta);
        }

        if (settings.debugReports && adjustment.shouldLog()) {
            LOG.info("Stat-Derived Ship Costs transaction guard: boughtRefunded=" + adjustment.boughtRefunded
                    + ", boughtUnderpriced=" + adjustment.boughtUnderpriced
                    + ", soldCorrected=" + adjustment.soldCorrected
                    + ", creditDelta=" + Math.round(adjustment.creditDelta)
                    + ", submarket=" + submarketId(transaction.getSubmarket())
                    + (adjustment.sample.length() > 0 ? ", sample=" + adjustment.sample : ""));
        }
    }

    private static void correctBoughtShips(PlayerMarketTransaction transaction, SDSCFormula formula,
                                           SDSCMarketPricing marketPricing, SDSCSettings settings,
                                           Adjustment adjustment) {
        List<PlayerMarketTransaction.ShipSaleInfo> bought = transaction.getShipsBought();
        if (bought == null || bought.isEmpty()) {
            return;
        }

        for (PlayerMarketTransaction.ShipSaleInfo sale : bought) {
            PriceContext context = priceContext(sale, transaction.getSubmarket(), formula, marketPricing);
            if (context == null) {
                continue;
            }

            float paid = sale.getPrice();
            float corrected = scaledTransactionPrice(paid, context.currentBaseValue, context.targetBaseValue);
            if (corrected < paid - PRICE_EPSILON) {
                adjustment.creditDelta += paid - corrected;
                adjustment.boughtRefunded++;
                sale.setPrice(corrected);
                appendSample(adjustment, context.result.hullId + ":buy paid="
                        + Math.round(paid) + ",target=" + Math.round(corrected)
                        + ",channel=" + context.marketPrice.channel);
            } else if (corrected > paid + PRICE_EPSILON) {
                adjustment.boughtUnderpriced++;
                appendSample(adjustment, context.result.hullId + ":buy underpriced paid="
                        + Math.round(paid) + ",target=" + Math.round(corrected)
                        + ",channel=" + context.marketPrice.channel);
            }
        }
    }

    private static void correctSoldShips(PlayerMarketTransaction transaction, SDSCFormula formula,
                                         SDSCMarketPricing marketPricing, SDSCSettings settings,
                                         Adjustment adjustment) {
        List<PlayerMarketTransaction.ShipSaleInfo> sold = transaction.getShipsSold();
        if (sold == null || sold.isEmpty()) {
            return;
        }

        boolean saleGuard = settings.getBoolean("safety", "enableSaleGuard", true);
        float capMult = clamp(settings.get("safety", "shipSalePayoutCapMult", 0.62f), 0f, 1f);

        for (PlayerMarketTransaction.ShipSaleInfo sale : sold) {
            PriceContext context = priceContext(sale, transaction.getSubmarket(), formula, marketPricing);
            if (context == null) {
                continue;
            }

            float paid = sale.getPrice();
            float corrected = context.salePrice.hullValue;
            if (saleGuard) {
                float cap = Math.max(0f, context.result.computedHullValue * capMult);
                corrected = Math.min(corrected, cap);
            }

            if (Math.abs(corrected - paid) <= PRICE_EPSILON) {
                continue;
            }

            adjustment.creditDelta += corrected - paid;
            adjustment.soldCorrected++;
            sale.setPrice(corrected);
            appendSample(adjustment, context.result.hullId + ":sell paid="
                    + Math.round(paid) + ",target=" + Math.round(corrected)
                    + ",channel=" + context.salePrice.channel
                    + ",x=" + String.format(java.util.Locale.US, "%.2f", context.salePrice.channelMult));
        }
    }

    private static PriceContext priceContext(PlayerMarketTransaction.ShipSaleInfo sale, SubmarketAPI submarket,
                                             SDSCFormula formula, SDSCMarketPricing marketPricing) {
        FleetMemberAPI member = sale == null ? null : sale.getMember();
        if (member == null || member.getHullSpec() == null) {
            return null;
        }

        int dMods = formula.countMemberDMods(member);
        SDSCFormula.HullValueResult result = formula.estimateMemberHull(member);
        if (!result.marketHull) {
            return null;
        }

        SDSCMarketPricing.MarketPrice marketPrice = marketPricing.priceBuyHull(result.computedHullValue, member, submarket, dMods);
        SDSCMarketPricing.MarketPrice salePrice = marketPricing.priceSellHull(result.computedHullValue, member, submarket, dMods);
        return new PriceContext(result, marketPrice, salePrice, Math.max(0f, member.getHullSpec().getBaseValue()));
    }

    private static boolean hasShipTransactions(PlayerMarketTransaction transaction) {
        return transaction.getShipsBought() != null && !transaction.getShipsBought().isEmpty()
                || transaction.getShipsSold() != null && !transaction.getShipsSold().isEmpty();
    }

    private static float scaledTransactionPrice(float currentPrice, float currentBaseValue, float targetBaseValue) {
        if (currentPrice <= 0f || currentBaseValue <= 0f || targetBaseValue <= 0f) {
            return currentPrice;
        }
        float ratio = clamp(targetBaseValue / currentBaseValue, 0.05f, 20f);
        return Math.max(0f, currentPrice * ratio);
    }

    private static void adjustPlayerCredits(float delta) {
        if (Global.getSector() != null
                && Global.getSector().getPlayerFleet() != null
                && Global.getSector().getPlayerFleet().getCargo() != null) {
            if (delta > 0f) {
                Global.getSector().getPlayerFleet().getCargo().getCredits().add(delta);
            } else if (delta < 0f) {
                Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(-delta);
            }
        }
    }

    private static void appendSample(Adjustment adjustment, String text) {
        if (adjustment.sample.length() >= 520) {
            return;
        }
        if (adjustment.sample.length() > 0) {
            adjustment.sample.append("; ");
        }
        adjustment.sample.append(text);
    }

    private static String submarketId(SubmarketAPI submarket) {
        return submarket == null ? "" : submarket.getSpecId();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class PriceContext {
        public final SDSCFormula.HullValueResult result;
        public final SDSCMarketPricing.MarketPrice marketPrice;
        public final SDSCMarketPricing.MarketPrice salePrice;
        public final float currentBaseValue;
        public final float targetBaseValue;

        public PriceContext(SDSCFormula.HullValueResult result, SDSCMarketPricing.MarketPrice marketPrice,
                            SDSCMarketPricing.MarketPrice salePrice, float currentBaseValue) {
            this.result = result;
            this.marketPrice = marketPrice;
            this.salePrice = salePrice;
            this.currentBaseValue = currentBaseValue;
            this.targetBaseValue = marketPrice.hullValue;
        }
    }

    private static class Adjustment {
        public float creditDelta;
        public int boughtRefunded;
        public int boughtUnderpriced;
        public int soldCorrected;
        public final StringBuilder sample = new StringBuilder();

        public boolean shouldLog() {
            return Math.abs(creditDelta) > PRICE_EPSILON
                    || boughtRefunded > 0
                    || boughtUnderpriced > 0
                    || soldCorrected > 0;
        }
    }
}
