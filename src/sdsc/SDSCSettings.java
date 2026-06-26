package sdsc;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SDSCSettings {
    private static final Logger LOG = Global.getLogger(SDSCSettings.class);
    private static final String FORMULA_PATH = "data/config/stat_derived_ship_costs/formula.json";

    public final JSONObject root;

    public boolean enablePricingHooks;
    public String pricingMode;
    public boolean debugReports;
    public float hullValueMult;
    public float loadoutValueMult;
    public float marketContextStrength;
    public boolean enableFuelPressure;

    public SDSCSettings(JSONObject root) {
        this.root = root;
    }

    public static SDSCSettings load() {
        try {
            JSONObject root = Global.getSettings().loadJSON(FORMULA_PATH, SDSCModPlugin.MOD_ID);
            SDSCSettings settings = new SDSCSettings(root);
            settings.loadRuntimeDefaults();
            settings.applyLunaOverrides();
            return settings;
        } catch (Throwable ex) {
            LOG.error("Unable to load formula config; using hardcoded emergency defaults.", ex);
            SDSCSettings settings = new SDSCSettings(new JSONObject());
            settings.enablePricingHooks = false;
            settings.pricingMode = "Report Only";
            settings.debugReports = true;
            settings.hullValueMult = 1f;
            settings.loadoutValueMult = 1f;
            settings.marketContextStrength = 1f;
            settings.enableFuelPressure = true;
            return settings;
        }
    }

    private void loadRuntimeDefaults() {
        JSONObject runtime = root.optJSONObject("runtime");
        if (runtime == null) {
            runtime = new JSONObject();
        }
        enablePricingHooks = runtime.optBoolean("enablePricingHooks", true);
        pricingMode = runtime.optString("pricingMode", "Full Market");
        debugReports = runtime.optBoolean("debugReports", true);
        hullValueMult = (float) runtime.optDouble("hullValueMult", 1.0);
        loadoutValueMult = (float) runtime.optDouble("loadoutValueMult", 1.0);
        marketContextStrength = (float) runtime.optDouble("marketContextStrength", 1.0);
        enableFuelPressure = runtime.optBoolean("enableFuelPressure", true);
    }

    private void applyLunaOverrides() {
        enablePricingHooks = getLunaBoolean("sdsc_enable_pricing", enablePricingHooks);
        pricingMode = getLunaString("sdsc_pricing_mode", pricingMode);
        debugReports = getLunaBoolean("sdsc_debug_reports", debugReports);
        hullValueMult = getLunaFloat("sdsc_hull_value_mult", hullValueMult);
        loadoutValueMult = getLunaFloat("sdsc_loadout_value_mult", loadoutValueMult);
        marketContextStrength = getLunaFloat("sdsc_market_context_strength", marketContextStrength);
        enableFuelPressure = getLunaBoolean("sdsc_fuel_pressure", enableFuelPressure);

        set("condition", "dModDiscountPerMod", getLunaFloat("sdsc_dmod_discount_per_mod", get("condition", "dModDiscountPerMod", 0.20f)));
        set("condition.dModDiscountPerModSizeMult", "FRIGATE", getLunaFloat("sdsc_dmod_scale_frigate", getNestedSizeValue("condition.dModDiscountPerModSizeMult", "FRIGATE", 1.0f)));
        set("condition.dModDiscountPerModSizeMult", "DESTROYER", getLunaFloat("sdsc_dmod_scale_destroyer", getNestedSizeValue("condition.dModDiscountPerModSizeMult", "DESTROYER", 0.9f)));
        set("condition.dModDiscountPerModSizeMult", "CRUISER", getLunaFloat("sdsc_dmod_scale_cruiser", getNestedSizeValue("condition.dModDiscountPerModSizeMult", "CRUISER", 0.8f)));
        set("condition.dModDiscountPerModSizeMult", "CAPITAL_SHIP", getLunaFloat("sdsc_dmod_scale_capital", getNestedSizeValue("condition.dModDiscountPerModSizeMult", "CAPITAL_SHIP", 0.7f)));
        set("condition", "dModDiscountFloor", getLunaFloat("sdsc_dmod_discount_floor", get("condition", "dModDiscountFloor", 0.05f)));
        set("condition", "dHullMinimumDModCount", getLunaFloat("sdsc_dhull_min_dmods", get("condition", "dHullMinimumDModCount", 4f)));
        set("condition", "sModFaceValueFraction", getLunaFloat("sdsc_smod_face_value", get("condition", "sModFaceValueFraction", 0f)));

        set("marketContext", "rareHullDModFloor", getLunaFloat("sdsc_rare_dmod_floor", get("marketContext", "rareHullDModFloor", 0.45f)));
        set("marketContext", "fuelPriceSensitivity", getLunaFloat("sdsc_fuel_sensitivity", get("marketContext", "fuelPriceSensitivity", 0.18f)));
        set("marketContext", "demandSensitivity", getLunaFloat("sdsc_demand_sensitivity", get("marketContext", "demandSensitivity", 0.12f)));
        set("marketContext", "supplySensitivity", getLunaFloat("sdsc_supply_sensitivity", get("marketContext", "supplySensitivity", 0.10f)));
        set("marketContext", "rarityValueSensitivity", getLunaFloat("sdsc_rarity_sensitivity", get("marketContext", "rarityValueSensitivity", 0.25f)));

        set("marketChannels.OPEN", "baseMult", getLunaFloat("sdsc_open_buy_base", get("marketChannels.OPEN", "baseMult", 1f)));
        set("marketChannels.OPEN", "commissionTaxReliefMax", getLunaFloat("sdsc_open_commission_relief", get("marketChannels.OPEN", "commissionTaxReliefMax", 0.18f)));
        set("marketChannels.MILITARY", "militaryHullBaseMult", getLunaFloat("sdsc_military_combat_base", get("marketChannels.MILITARY", "militaryHullBaseMult", 0.92f)));
        set("marketChannels.MILITARY", "civilianHullBaseMult", getLunaFloat("sdsc_military_civilian_base", get("marketChannels.MILITARY", "civilianHullBaseMult", 0.55f)));
        set("marketChannels.MILITARY", "relationDiscountMax", getLunaFloat("sdsc_military_relation_discount", get("marketChannels.MILITARY", "relationDiscountMax", 0.25f)));
        set("marketChannels.MILITARY", "commissionDiscount", getLunaFloat("sdsc_military_commission_discount", get("marketChannels.MILITARY", "commissionDiscount", 0.12f)));
        set("marketChannels.BLACK", "baseMult", getLunaFloat("sdsc_black_buy_base", get("marketChannels.BLACK", "baseMult", 0.78f)));
        set("marketChannels.BLACK", "rarityPremiumSensitivity", getLunaFloat("sdsc_black_buy_rarity", get("marketChannels.BLACK", "rarityPremiumSensitivity", 0.35f)));
        set("marketChannels.BLACK", "riskDiscountMult", getLunaFloat("sdsc_black_risk_discount", get("marketChannels.BLACK", "riskDiscountMult", 0.95f)));
        set("marketChannels.BLACK", "dModToleranceBonus", getLunaFloat("sdsc_black_buy_dmod_tolerance", get("marketChannels.BLACK", "dModToleranceBonus", 0.08f)));

        set("saleChannels.OPEN", "baseMult", getLunaFloat("sdsc_open_sale_base", get("saleChannels.OPEN", "baseMult", 0.58f)));
        set("saleChannels.OPEN", "dModExtraPenaltyPerMod", getLunaFloat("sdsc_open_sale_dmod_penalty", get("saleChannels.OPEN", "dModExtraPenaltyPerMod", 0.06f)));
        set("saleChannels.OPEN", "profitGuardMult", getLunaFloat("sdsc_open_profit_guard", get("saleChannels.OPEN", "profitGuardMult", 0.95f)));
        set("saleChannels.MILITARY", "baseMult", getLunaFloat("sdsc_military_sale_base", get("saleChannels.MILITARY", "baseMult", 0.62f)));
        set("saleChannels.MILITARY", "factionMatchBonus", getLunaFloat("sdsc_military_faction_bonus", get("saleChannels.MILITARY", "factionMatchBonus", 0.20f)));
        set("saleChannels.MILITARY", "relationBonusMax", getLunaFloat("sdsc_military_relation_bonus", get("saleChannels.MILITARY", "relationBonusMax", 0.15f)));
        set("saleChannels.MILITARY", "civilianHullPenalty", getLunaFloat("sdsc_military_civilian_penalty", get("saleChannels.MILITARY", "civilianHullPenalty", 0.30f)));
        set("saleChannels.BLACK", "baseMult", getLunaFloat("sdsc_black_sale_base", get("saleChannels.BLACK", "baseMult", 0.66f)));
        set("saleChannels.BLACK", "trashFloor", getLunaFloat("sdsc_black_trash_floor", get("saleChannels.BLACK", "trashFloor", 0.25f)));
        set("saleChannels.BLACK", "rarityOfferSensitivity", getLunaFloat("sdsc_black_sale_rarity", get("saleChannels.BLACK", "rarityOfferSensitivity", 0.35f)));
    }

    public float get(String path, String key, float fallback) {
        JSONObject obj = getObject(path);
        if (obj == null) {
            return fallback;
        }
        return (float) obj.optDouble(key, fallback);
    }

    public boolean getBoolean(String path, String key, boolean fallback) {
        JSONObject obj = getObject(path);
        if (obj == null) {
            return fallback;
        }
        return obj.optBoolean(key, fallback);
    }

    public float getSizeValue(String objectName, String sizeKey, float fallback) {
        JSONObject obj = root.optJSONObject(objectName);
        if (obj == null) {
            return fallback;
        }
        return (float) obj.optDouble(sizeKey, fallback);
    }

    public float getNestedSizeValue(String path, String sizeKey, float fallback) {
        JSONObject obj = getObject(path);
        if (obj == null) {
            return fallback;
        }
        return (float) obj.optDouble(sizeKey, fallback);
    }

    public Map<String, Float> getFloatMap(String objectName) {
        Map<String, Float> result = new HashMap<String, Float>();
        JSONObject obj = root.optJSONObject(objectName);
        if (obj == null) {
            return result;
        }
        Iterator<?> keys = obj.keys();
        while (keys.hasNext()) {
            String key = String.valueOf(keys.next());
            result.put(key, (float) obj.optDouble(key, 0.0));
        }
        return result;
    }

    public Map<String, Float> getNestedFloatMap(String path) {
        Map<String, Float> result = new HashMap<String, Float>();
        JSONObject obj = getObject(path);
        if (obj == null) {
            return result;
        }
        Iterator<?> keys = obj.keys();
        while (keys.hasNext()) {
            String key = String.valueOf(keys.next());
            result.put(key, (float) obj.optDouble(key, 0.0));
        }
        return result;
    }

    private void set(String path, String key, float value) {
        JSONObject obj = getOrCreateObject(path);
        try {
            obj.put(key, value);
        } catch (Throwable ignored) {
        }
    }

    private JSONObject getObject(String path) {
        String[] parts = path.split("\\.");
        JSONObject obj = root;
        for (String part : parts) {
            if (obj == null) {
                return null;
            }
            obj = obj.optJSONObject(part);
        }
        return obj;
    }

    private JSONObject getOrCreateObject(String path) {
        String[] parts = path.split("\\.");
        JSONObject obj = root;
        for (String part : parts) {
            JSONObject next = obj.optJSONObject(part);
            if (next == null) {
                next = new JSONObject();
                try {
                    obj.put(part, next);
                } catch (Throwable ignored) {
                }
            }
            obj = next;
        }
        return obj;
    }

    private static boolean getLunaBoolean(String id, boolean fallback) {
        try {
            Boolean value = LunaSettings.getBoolean(SDSCModPlugin.MOD_ID, id);
            return value == null ? fallback : value;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static float getLunaFloat(String id, float fallback) {
        try {
            Double value = LunaSettings.getDouble(SDSCModPlugin.MOD_ID, id);
            return value == null ? fallback : value.floatValue();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String getLunaString(String id, String fallback) {
        try {
            String value = LunaSettings.getString(SDSCModPlugin.MOD_ID, id);
            return value == null || value.trim().isEmpty() ? fallback : value.trim();
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
