package sdsc;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.SubmarketInteractionListener;
import com.fs.starfarer.api.campaign.listeners.SubmarketInteractionListener.SubmarketInteractionType;
import com.fs.starfarer.api.campaign.listeners.SubmarketUpdateListener;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.apache.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SDSCMarketListener implements SubmarketUpdateListener, SubmarketInteractionListener {
    private static final Logger LOG = Global.getLogger(SDSCMarketListener.class);

    @Override
    public void reportSubmarketCargoAndShipsUpdated(SubmarketAPI submarket) {
        repriceSubmarket(submarket, "submarket-cargo-updated", true);
    }

    @Override
    public void reportPlayerOpenedSubmarket(SubmarketAPI submarket, SubmarketInteractionType type) {
        repriceSubmarket(submarket, "submarket-opened-" + type, true);
    }

    public static int repriceAllMarkets(String reason) {
        if (Global.getSector() == null || Global.getSector().getEconomy() == null) {
            return 0;
        }

        int applied = 0;
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market == null) {
                continue;
            }
            for (SubmarketAPI submarket : market.getSubmarketsCopy()) {
                applied += repriceSubmarket(submarket, reason, false);
            }
        }
        return applied;
    }

    public static int repriceSubmarket(SubmarketAPI submarket, String reason, boolean allowCreateCargo) {
        if (submarket == null) {
            return 0;
        }

        SDSCSettings settings = SDSCSettings.load();
        if (!shouldApplyMemberPricing(settings)) {
            return 0;
        }

        CargoAPI cargo = getCargo(submarket, allowCreateCargo);
        if (cargo == null || cargo.getMothballedShips() == null) {
            return 0;
        }

        List<FleetMemberAPI> members = cargo.getMothballedShips().getMembersListCopy();
        if (members == null || members.isEmpty()) {
            return 0;
        }

        SDSCFormula formula = new SDSCFormula(settings);
        SDSCMarketPricing marketPricing = new SDSCMarketPricing(settings);
        Map<ShipHullSpecAPI, DesiredHullValue> desiredValues = new LinkedHashMap<ShipHullSpecAPI, DesiredHullValue>();
        int inspected = 0;
        int exactDModMembers = 0;
        StringBuilder sample = new StringBuilder();

        for (FleetMemberAPI member : members) {
            if (member == null || member.isFighterWing() || member.getHullSpec() == null) {
                continue;
            }

            inspected++;
            int dMods = formula.countMemberDMods(member);
            if (dMods > 0) {
                exactDModMembers++;
            }

            SDSCFormula.HullValueResult result = formula.estimateHull(member.getHullSpec(), dMods);
            if (!result.marketHull) {
                continue;
            }

            SDSCMarketPricing.MarketPrice marketPrice = marketPricing.priceBuyHull(result.computedHullValue, member, submarket, dMods);
            DesiredHullValue desired = new DesiredHullValue(result, marketPrice);
            DesiredHullValue existing = desiredValues.get(result.spec);
            if (existing == null || desired.marketPrice.hullValue < existing.marketPrice.hullValue) {
                desiredValues.put(result.spec, desired);
            }

            if (settings.debugReports && sample.length() < 520) {
                if (sample.length() > 0) {
                    sample.append("; ");
                }
                sample.append(result.hullId)
                        .append(":channel=")
                        .append(marketPrice.channel)
                        .append(",x=")
                        .append(String.format(java.util.Locale.US, "%.2f", marketPrice.channelMult))
                        .append(",dmods=")
                        .append(dMods)
                        .append(",appraisal=")
                        .append(result.computedHullValue)
                        .append(",marketHull=")
                        .append(marketPrice.hullValue);
            }
        }

        int applied = 0;
        int failed = 0;
        for (DesiredHullValue desired : desiredValues.values()) {
            if (SDSCModPlugin.applyHullValue(desired.result.spec, desired.marketPrice.hullValue)) {
                applied++;
            } else {
                failed++;
            }
        }

        if (settings.debugReports && (applied > 0 || exactDModMembers > 0 || failed > 0)) {
            LOG.info("Stat-Derived Ship Costs market member pass: reason=" + reason
                    + ", market=" + marketName(submarket)
                    + ", submarket=" + submarket.getSpecId()
                    + ", members=" + inspected
                    + ", dmodMembers=" + exactDModMembers
                    + ", appliedSpecs=" + applied
                    + ", failed=" + failed
                    + (sample.length() > 0 ? ", sample=" + sample : ""));
        }

        return applied;
    }

    private static class DesiredHullValue {
        public final SDSCFormula.HullValueResult result;
        public final SDSCMarketPricing.MarketPrice marketPrice;

        public DesiredHullValue(SDSCFormula.HullValueResult result, SDSCMarketPricing.MarketPrice marketPrice) {
            this.result = result;
            this.marketPrice = marketPrice;
        }
    }

    private static boolean shouldApplyMemberPricing(SDSCSettings settings) {
        if (settings == null || !settings.enablePricingHooks) {
            return false;
        }
        if ("Report Only".equalsIgnoreCase(settings.pricingMode)) {
            return false;
        }
        return !"Hull Only".equalsIgnoreCase(settings.pricingMode);
    }

    private static CargoAPI getCargo(SubmarketAPI submarket, boolean allowCreateCargo) {
        CargoAPI cargo = submarket.getCargoNullOk();
        if (cargo == null && allowCreateCargo) {
            try {
                cargo = submarket.getCargo();
            } catch (Throwable ignored) {
            }
        }
        return cargo;
    }

    private static String marketName(SubmarketAPI submarket) {
        MarketAPI market = submarket.getMarket();
        if (market == null) {
            return "";
        }
        return market.getName();
    }
}
