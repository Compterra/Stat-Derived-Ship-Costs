package sdsc;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.util.Misc;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

public class SDSCMarketPricing {
    private final SDSCSettings settings;
    private final Map<String, Float> rarityTags;

    public SDSCMarketPricing(SDSCSettings settings) {
        this.settings = settings;
        this.rarityTags = settings.getNestedFloatMap("marketContext.rarityTags");
    }

    public MarketPrice priceBuyHull(int appraisedHullValue, FleetMemberAPI member, SubmarketAPI submarket, int dMods) {
        float mult = buyChannelMult(member, submarket, dMods);
        int value = Math.max(1, roundTo100(appraisedHullValue * mult));
        return new MarketPrice(value, mult, channelName(submarket));
    }

    public MarketPrice priceSellHull(int appraisedHullValue, FleetMemberAPI member, SubmarketAPI submarket, int dMods) {
        float mult = sellChannelMult(member, submarket, dMods);
        int value = Math.max(1, roundTo100(appraisedHullValue * mult));
        return new MarketPrice(value, mult, channelName(submarket));
    }

    private float buyChannelMult(FleetMemberAPI member, SubmarketAPI submarket, int dMods) {
        if (!"Full Market".equalsIgnoreCase(settings.pricingMode)) {
            return 1f;
        }

        float mult;
        if (isBlackMarket(submarket)) {
            mult = blackMarketMult(member, dMods);
        } else if (isMilitaryMarket(submarket)) {
            mult = militaryMarketMult(member, submarket);
        } else {
            mult = openMarketMult(submarket);
        }

        float strength = clamp(settings.marketContextStrength, 0f, 3f);
        float min = settings.get("safety", "buyChannelMinMult", 0.55f);
        float max = settings.get("safety", "buyChannelMaxMult", 1.35f);
        return clamp(1f + (mult - 1f) * strength, min, max);
    }

    private float openMarketMult(SubmarketAPI submarket) {
        float mult = settings.get("marketChannels.OPEN", "baseMult", 1f);
        if (isCommissionedWith(submarket)) {
            float maxRelief = settings.get("marketChannels.OPEN", "commissionTaxReliefMax", 0.18f);
            float minRelation = settings.get("marketChannels.OPEN", "commissionTaxReliefMinRelation", 25f);
            float maxRelation = settings.get("marketChannels.OPEN", "commissionTaxReliefMaxRelation", 75f);
            mult *= 1f - maxRelief * relationProgress(submarket, minRelation, maxRelation);
        }
        return mult;
    }

    private float militaryMarketMult(FleetMemberAPI member, SubmarketAPI submarket) {
        boolean civilian = isCivilian(member);
        float mult = civilian
                ? settings.get("marketChannels.MILITARY", "civilianHullBaseMult", 0.55f)
                : settings.get("marketChannels.MILITARY", "militaryHullBaseMult", 0.92f);

        float maxDiscount = settings.get("marketChannels.MILITARY", "relationDiscountMax", 0.25f);
        float minRelation = settings.get("marketChannels.MILITARY", "relationDiscountMinRelation", 0f);
        float maxRelation = settings.get("marketChannels.MILITARY", "relationDiscountMaxRelation", 100f);
        mult *= 1f - maxDiscount * relationProgress(submarket, minRelation, maxRelation);

        if (isCommissionedWith(submarket)) {
            mult *= 1f - settings.get("marketChannels.MILITARY", "commissionDiscount", 0.12f);
        }

        return mult;
    }

    private float blackMarketMult(FleetMemberAPI member, int dMods) {
        float mult = settings.get("marketChannels.BLACK", "baseMult", 0.78f)
                * settings.get("marketChannels.BLACK", "riskDiscountMult", 0.95f);

        float rarityScore = rarityScore(member);
        if (rarityScore > 0f) {
            mult *= 1f + rarityScore * settings.get("marketChannels.BLACK", "rarityPremiumSensitivity", 0.35f);
        }

        if (dMods > 0) {
            float tolerance = settings.get("marketChannels.BLACK", "dModToleranceBonus", 0.08f);
            mult *= 1f + tolerance * clamp(dMods / 5f, 0f, 1f);
        }

        return mult;
    }

    private float sellChannelMult(FleetMemberAPI member, SubmarketAPI submarket, int dMods) {
        if (!"Full Market".equalsIgnoreCase(settings.pricingMode)) {
            return settings.get("safety", "shipSalePayoutCapMult", 0.62f);
        }

        float mult;
        if (isBlackMarket(submarket)) {
            mult = blackSaleMult(member, dMods);
        } else if (isMilitaryMarket(submarket)) {
            mult = militarySaleMult(member, submarket);
        } else {
            mult = openSaleMult(member, dMods);
        }

        float cap = settings.get("safety", "shipSalePayoutCapMult", 0.62f);
        return clamp(mult, 0f, cap);
    }

    private float openSaleMult(FleetMemberAPI member, int dMods) {
        float mult = settings.get("saleChannels.OPEN", "baseMult", 0.58f)
                * settings.get("saleChannels.OPEN", "profitGuardMult", 0.95f);

        if (dMods > 0) {
            float penalty = settings.get("saleChannels.OPEN", "dModExtraPenaltyPerMod", 0.06f);
            mult *= Math.max(0f, 1f - penalty * dMods);
        }

        float rarityScore = rarityScore(member);
        if (rarityScore > 0f) {
            mult *= 1f + rarityScore * settings.get("saleChannels.OPEN", "rarityOfferSensitivity", 0.12f);
        }

        return mult;
    }

    private float militarySaleMult(FleetMemberAPI member, SubmarketAPI submarket) {
        float mult = settings.get("saleChannels.MILITARY", "baseMult", 0.62f);

        float maxBonus = settings.get("saleChannels.MILITARY", "relationBonusMax", 0.15f);
        float minRelation = settings.get("saleChannels.MILITARY", "relationBonusMinRelation", 0f);
        float maxRelation = settings.get("saleChannels.MILITARY", "relationBonusMaxRelation", 100f);
        mult *= 1f + maxBonus * relationProgress(submarket, minRelation, maxRelation);

        if (isCommissionedWith(submarket)) {
            mult *= 1f + settings.get("saleChannels.MILITARY", "commissionBonus", 0.08f);
        }

        if (isCivilian(member)) {
            mult *= Math.max(0f, 1f - settings.get("saleChannels.MILITARY", "civilianHullPenalty", 0.30f));
        }

        float rarityScore = rarityScore(member);
        if (rarityScore > 0f) {
            mult *= 1f + rarityScore * settings.get("saleChannels.MILITARY", "rarityOfferSensitivity", 0.18f);
        }

        return mult;
    }

    private float blackSaleMult(FleetMemberAPI member, int dMods) {
        float mult = settings.get("saleChannels.BLACK", "baseMult", 0.66f)
                * settings.get("saleChannels.BLACK", "quickCashMult", 0.95f);

        float rarityScore = rarityScore(member);
        if (rarityScore > 0f) {
            mult *= 1f + rarityScore * settings.get("saleChannels.BLACK", "rarityOfferSensitivity", 0.35f);
        }

        if (dMods > 0) {
            float tolerance = settings.get("saleChannels.BLACK", "dModToleranceBonus", 0.05f);
            mult *= 1f + tolerance * clamp(dMods / 5f, 0f, 1f);
            mult = Math.max(mult, settings.get("saleChannels.BLACK", "trashFloor", 0.25f));
        }

        return mult;
    }

    private float rarityScore(FleetMemberAPI member) {
        if (member == null) {
            return 0f;
        }

        float score = 0f;
        ShipHullSpecAPI spec = member.getHullSpec();
        ShipVariantAPI variant = member.getVariant();

        if (member.isPhaseShip()) {
            score = Math.max(score, rarityTagValue("phase"));
        }
        if (spec != null) {
            score = Math.max(score, rarityScoreFromTags(spec.getTags()));
            score = Math.max(score, rarityTokenScore(spec.getHullId()));
        }
        if (variant != null) {
            score = Math.max(score, rarityScoreFromTags(variant.getTags()));
            score = Math.max(score, rarityTokenScore(variant.getHullVariantId()));
        }
        return score;
    }

    private float rarityScoreFromTags(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return 0f;
        }
        float score = 0f;
        for (String tag : tags) {
            score = Math.max(score, rarityTagValue(tag));
        }
        return score;
    }

    private float rarityTokenScore(String text) {
        String normalized = safe(text).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return 0f;
        }
        float score = 0f;
        for (String tag : rarityTags.keySet()) {
            if (normalized.contains(tag.toLowerCase(Locale.ROOT))) {
                score = Math.max(score, rarityTags.get(tag));
            }
        }
        return score;
    }

    private float rarityTagValue(String tag) {
        String normalized = safe(tag).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return 0f;
        }
        Float value = rarityTags.get(normalized);
        return value == null ? 0f : value;
    }

    private boolean isCommissionedWith(SubmarketAPI submarket) {
        String commission = Misc.getCommissionFactionId();
        if (commission == null || commission.trim().isEmpty()) {
            return false;
        }
        FactionAPI faction = submarketFaction(submarket);
        return faction != null && commission.equals(faction.getId());
    }

    private float relationProgress(SubmarketAPI submarket, float minRelation, float maxRelation) {
        FactionAPI faction = submarketFaction(submarket);
        if (faction == null) {
            return 0f;
        }
        float relation = faction.getRelationship(Factions.PLAYER) * 100f;
        if (maxRelation <= minRelation) {
            return relation >= maxRelation ? 1f : 0f;
        }
        return clamp((relation - minRelation) / (maxRelation - minRelation), 0f, 1f);
    }

    private FactionAPI submarketFaction(SubmarketAPI submarket) {
        if (submarket == null) {
            return null;
        }
        FactionAPI faction = submarket.getFaction();
        if (faction != null) {
            return faction;
        }
        MarketAPI market = submarket.getMarket();
        return market == null ? null : market.getFaction();
    }

    private boolean isCivilian(FleetMemberAPI member) {
        if (member == null) {
            return false;
        }
        if (member.isCivilian()) {
            return true;
        }
        ShipVariantAPI variant = member.getVariant();
        return variant != null && variant.isCivilian();
    }

    private static boolean isBlackMarket(SubmarketAPI submarket) {
        if (submarket == null) {
            return false;
        }
        SubmarketPlugin plugin = submarket.getPlugin();
        return Submarkets.SUBMARKET_BLACK.equals(submarket.getSpecId())
                || (plugin != null && plugin.isBlackMarket());
    }

    private static boolean isMilitaryMarket(SubmarketAPI submarket) {
        if (submarket == null) {
            return false;
        }
        SubmarketPlugin plugin = submarket.getPlugin();
        return Submarkets.GENERIC_MILITARY.equals(submarket.getSpecId())
                || (plugin != null && plugin.isMilitaryMarket());
    }

    private static String channelName(SubmarketAPI submarket) {
        if (isBlackMarket(submarket)) {
            return "BLACK";
        }
        if (isMilitaryMarket(submarket)) {
            return "MILITARY";
        }
        return "OPEN";
    }

    private static int roundTo100(float value) {
        return Math.round(value / 100f) * 100;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static class MarketPrice {
        public final int hullValue;
        public final float channelMult;
        public final String channel;

        public MarketPrice(int hullValue, float channelMult, String channel) {
            this.hullValue = hullValue;
            this.channelMult = channelMult;
            this.channel = channel;
        }
    }
}
