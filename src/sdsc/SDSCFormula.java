package sdsc;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SDSCFormula {
    private static final Set<String> FALLBACK_D_MOD_IDS = new HashSet<String>(Arrays.asList(
            "andrada_mods",
            "comp_armor",
            "comp_hull",
            "comp_storage",
            "comp_structure",
            "damaged_deck",
            "damaged_mounts",
            "defective_manufactory",
            "degraded_drive_field",
            "degraded_engines",
            "degraded_life_support",
            "degraded_shields",
            "destroyed_mounts",
            "erratic_injector",
            "faulty_auto",
            "faulty_grid",
            "fragile_subsystems",
            "glitched_sensors",
            "ill_advised",
            "increased_maintenance",
            "malfunctioning_comms",
            "unstable_coils"
    ));

    private final SDSCSettings settings;
    private final Map<String, Float> weights;
    private final Map<String, Float> mountMult;
    private final Map<String, Float> slotBase;
    private final Map<String, Float> shieldGeometryMult;
    private final Map<String, Float> systemIdValueMult;

    public SDSCFormula(SDSCSettings settings) {
        this.settings = settings;
        this.weights = settings.getFloatMap("weights");
        this.mountMult = settings.getFloatMap("mountMult");
        this.slotBase = settings.getFloatMap("slotBase");
        this.shieldGeometryMult = settings.getFloatMap("shieldGeometryMult");
        this.systemIdValueMult = settings.getNestedFloatMap("calibration.systemIdValueMult");
    }

    public HullValueResult estimateHull(ShipHullSpecAPI spec) {
        return estimateHull(spec, -1);
    }

    public HullValueResult estimateMemberHull(FleetMemberAPI member) {
        return estimateHull(member.getHullSpec(), countMemberDMods(member));
    }

    public HullValueResult estimateHull(ShipHullSpecAPI spec, int visibleDMods) {
        return estimateHull(spec, visibleDMods, true);
    }

    private HullValueResult estimateHull(ShipHullSpecAPI spec, int visibleDMods, boolean includeModules) {
        String sizeKey = sizeKey(spec.getHullSize());
        float floor = settings.getSizeValue("hullSizeFloor", sizeKey, 0f);
        float value = floor;

        value += spec.getHitpoints() * weight("hitpoints");
        value += spec.getArmorRating() * settings.getSizeValue("armorSizeMult", sizeKey, 0f);
        value += spec.getFluxCapacity() * weight("maxFlux");
        value += spec.getFluxDissipation() * weight("fluxDissipation");
        value += safeOrdnancePoints(spec) * weight("ordnancePoints");
        value += spec.getFighterBays() * weight("fighterBay");

        ShipHullSpecAPI.EngineSpecAPI engine = spec.getEngineSpec();
        if (engine != null) {
            value += engine.getMaxSpeed() * settings.getSizeValue("speedSizeMult", sizeKey, 0f);
            value += engine.getAcceleration() * weight("acceleration");
            value += engine.getDeceleration() * weight("deceleration");
            value += engine.getMaxTurnRate() * weight("maxTurnRate");
            value += engine.getTurnAcceleration() * weight("turnAcceleration");
        }

        value += weaponSlotValue(spec);
        value += defenseValue(spec, sizeKey);

        value += spec.getCargo() * weight("cargo");
        value += spec.getFuel() * weight("fuel");
        value += Math.max(0f, spec.getMaxCrew() - spec.getMinCrew()) * weight("crewRange");
        value -= spec.getFuelPerLY() * weight("fuelPerLightYearPenalty");
        value -= spec.getSuppliesPerMonth() * weight("suppliesPerMonthPenalty");
        value -= spec.getSuppliesToRecover() * weight("suppliesToRecoverPenalty");

        value *= settings.getNestedSizeValue("calibration.hullSizeValueMult", sizeKey, 1f);

        if (spec.getBuiltInMods().contains("civgrade")) {
            value *= settings.get("calibration", "civgradeValueMult", 1f);
        }

        if (spec.isPhase() || spec.getShieldType() == ShieldAPI.ShieldType.PHASE) {
            value *= settings.getNestedSizeValue("calibration.phaseCloakValueMult", sizeKey, 1f);
        }

        String systemId = safe(spec.getShipSystemId());
        if (!systemId.isEmpty() && systemIdValueMult.containsKey(systemId)) {
            value *= systemIdValueMult.get(systemId);
        }

        value *= settings.hullValueMult;

        int builtInDMods = builtInDModCount(spec);
        boolean exactDMods = visibleDMods >= 0;
        int pricedDMods = exactDMods ? Math.max(builtInDMods, visibleDMods) : pricedDModCount(spec, builtInDMods);
        if (pricedDMods > 0) {
            value *= dModConditionMult(pricedDMods, sizeKey);
        }

        float finalFloor = pricedDMods > 0
                ? floor * settings.get("condition", "damagedHullFloorFraction", 0.02f)
                : floor;
        int computed = Math.max(Math.round(finalFloor), roundTo100(value));
        if (includeModules) {
            computed += moduleHullValue(spec);

        }
        return new HullValueResult(spec, sizeKey, computed, isMarketHull(spec), marketNotes(spec, builtInDMods, pricedDMods, exactDMods));
    }

    private int moduleHullValue(ShipHullSpecAPI spec) {
        return moduleHullValue(canonicalVariant(spec));
    }

    private int moduleHullValue(ShipVariantAPI variant) {
        if (variant == null) {
            return 0;
        }

        Set<String> seenVariantIds = new HashSet<String>();
        int total = 0;
        if (variant.getModuleSlots() != null) {
            for (String slotId : variant.getModuleSlots()) {
                ShipVariantAPI module = variant.getModuleVariant(slotId);
                if (module != null && module.getHullSpec() != null
                        && seenVariantIds.add(module.getHullVariantId())) {
                    total += estimateHull(module.getHullSpec(), -1, false).computedHullValue;
                }
            }
        }
        if (variant.getStationModules() != null) {
            for (String variantId : variant.getStationModules().values()) {
                ShipVariantAPI module = Global.getSettings().getVariant(variantId);
                if (module != null && module.getHullSpec() != null
                        && seenVariantIds.add(module.getHullVariantId())) {
                    total += estimateHull(module.getHullSpec(), -1, false).computedHullValue;
                }
            }
        }
        return total;
    }
    private boolean hasModules(ShipVariantAPI variant) {
        return variant != null && ((variant.getModuleSlots() != null && !variant.getModuleSlots().isEmpty())
                || (variant.getStationModules() != null && !variant.getStationModules().isEmpty()));
    }
    private ShipVariantAPI canonicalVariant(ShipHullSpecAPI spec) {
        if (spec == null || Global.getSettings() == null) {
            return null;
        }
        try {
            String codexVariantId = safe(spec.getCodexVariantId());
            if (!codexVariantId.isEmpty()) {
                ShipVariantAPI variant = Global.getSettings().getVariant(codexVariantId);
                if (hasModules(variant)) {
                    return variant;
                }
            }

            List<String> variantIds = Global.getSettings().getHullIdToVariantListMap().get(spec.getHullId());
            if (variantIds == null) {
                return null;
            }
            for (String variantId : variantIds) {
                ShipVariantAPI variant = Global.getSettings().getVariant(variantId);
                if (hasModules(variant)) {

                    return variant;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
    private float defenseValue(ShipHullSpecAPI spec, String sizeKey) {
        ShipHullSpecAPI.ShieldSpecAPI shield = spec.getShieldSpec();
        ShieldAPI.ShieldType type = spec.getShieldType();
        if (shield != null && shield.getType() != null) {
            type = shield.getType();
        }
        if (type == null || type == ShieldAPI.ShieldType.NONE) {
            return 0f;
        }
        if (type == ShieldAPI.ShieldType.PHASE) {
            if (!"phasecloak".equals(safe(spec.getShipDefenseId()))) {
                return 0f;
            }
            float phaseCost = shield == null ? 0.06f : shield.getPhaseCost();
            float phaseUpkeep = shield == null ? 0.06f : shield.getPhaseUpkeep();
            float phaseCostFactor = clamp(0.06f / Math.max(0.01f, phaseCost), 0.6f, 2.0f);
            float phaseUpkeepFactor = clamp(0.06f / Math.max(0.01f, phaseUpkeep), 0.6f, 2.0f);
            return settings.getSizeValue("phaseCloakSizeBase", sizeKey, 0f) * phaseCostFactor * phaseUpkeepFactor;
        }

        float base = settings.getSizeValue("shieldSizeBase", sizeKey, 0f);
        float arc = shield == null ? 0f : shield.getArc();
        float upkeep = shield == null ? 0f : shield.getUpkeepCost();
        float efficiency = shield == null ? 1f : shield.getFluxPerDamageAbsorbed();
        float dissipation = Math.max(1f, spec.getFluxDissipation());

        float arcFactor = clamp(arc / 180f, 0.25f, 2.0f);
        float efficiencyFactor = clamp(1.0f / Math.max(0.1f, efficiency), 0.45f, 2.5f);
        float upkeepFactor = clamp(1.25f - upkeep / dissipation, 0.5f, 1.25f);
        float geometry = valueOrDefault(shieldGeometryMult, type.name(), 1f);

        return base * arcFactor * efficiencyFactor * upkeepFactor * geometry;
    }

    private float weaponSlotValue(ShipHullSpecAPI spec) {
        float total = 0f;
        for (WeaponSlotAPI slot : spec.getAllWeaponSlotsCopy()) {
            if (slot == null || !slot.isWeaponSlot() || slot.isDecorative() || slot.isSystemSlot()) {
                continue;
            }
            String size = slot.getSlotSize() == null ? "" : slot.getSlotSize().name();
            String type = slot.getWeaponType() == null ? "" : slot.getWeaponType().name();
            float base = valueOrDefault(slotBase, size, 0f);
            float mount = valueOrDefault(mountMult, type, 1f);
            float arcFactor = clamp(slot.getArc() / 180f, 0.5f, 1.25f);
            if (slot.isHardpoint()) {
                arcFactor *= 0.95f;
            }
            if (slot.isTurret()) {
                arcFactor *= 1.05f;
            }
            total += base * mount * arcFactor;
        }
        return total;
    }

    private boolean isMarketHull(ShipHullSpecAPI spec) {
        if (spec == null) {
            return false;
        }
        String hullId = safe(spec.getHullId()).toLowerCase(Locale.ROOT);
        if (hullId.isEmpty() || "shuttlepod".equals(hullId)) {
            return false;
        }
        if (spec.getHullSize() == ShipAPI.HullSize.FIGHTER) {
            return false;
        }
        if (hasMarketToken(spec, "hide_in_codex") && !spec.isDHull()) {
            return false;
        }
        String[] excluded = {
                "module",
                "station_module",
                "station",
                "unboardable",
                "no_dealer",
                "no_sell",
                "restricted",
                "monster",
                "module_hull_bar_only",
                "threat",
                "dweller",
                "omega"
        };
        for (String token : excluded) {
            if (hasMarketToken(spec, token)) {
                return false;
            }
        }
        return spec.getBaseValue() > 0f;
    }

    private String marketNotes(ShipHullSpecAPI spec, int builtInDMods, int pricedDMods, boolean exactDMods) {
        List<String> notes = new ArrayList<String>();
        if (!isMarketHull(spec)) {
            notes.add("not-applied");
        }
        if (spec.isDHull()) {
            notes.add("d-hull");
        }
        if (builtInDMods > 0) {
            notes.add("built-in-dmods=" + builtInDMods);
        }
        if (exactDMods && pricedDMods > 0) {
            notes.add("member-dmods=" + pricedDMods);
        } else if (pricedDMods > builtInDMods) {
            notes.add("priced-dmods=" + pricedDMods);
        }
        if (spec.isPhase()) {
            notes.add("phase");
        }
        if (spec.getBuiltInMods().contains("civgrade")) {
            notes.add("civgrade");
        }
        if (hasMarketToken(spec, "restricted")) {
            notes.add("restricted");
        }
        if (hasMarketToken(spec, "no_sell")) {
            notes.add("no-sell");
        }
        return String.join(";", notes);
    }

    private float dModConditionMult(int dModCount, String sizeKey) {
        float perMod = settings.get("condition", "dModDiscountPerMod", 0.20f)
                * settings.getNestedSizeValue("condition.dModDiscountPerModSizeMult", sizeKey, 1f);
        float baseFloor = settings.get("condition", "dModDiscountFloor", 0.05f);
        float floor = settings.getNestedSizeValue("condition.dModDiscountFloorBySize", sizeKey, baseFloor);
        return Math.max(floor, 1f - Math.max(0, dModCount) * perMod);
    }

    private int pricedDModCount(ShipHullSpecAPI spec, int builtInDMods) {
        int count = Math.max(0, builtInDMods);
        if (spec != null && spec.isDHull()) {
            int minimum = Math.round(settings.get("condition", "dHullMinimumDModCount", 1f));
            count = Math.max(count, minimum);
        }
        return count;
    }

    private int builtInDModCount(ShipHullSpecAPI spec) {
        int count = 0;
        if (spec == null || spec.getBuiltInMods() == null) {
            return count;
        }
        for (String modId : spec.getBuiltInMods()) {
            if (isDMod(modId)) {
                count++;
            }
        }
        return count;
    }

    public int countMemberDMods(FleetMemberAPI member) {
        if (member == null) {
            return 0;
        }
        return countDMods(member.getVariant(), member.getHullSpec());
    }

    public int countDMods(ShipVariantAPI variant, ShipHullSpecAPI spec) {
        Set<String> ids = new HashSet<String>();
        if (spec != null) {
            addDModCandidates(ids, spec.getBuiltInMods());
        }
        if (variant != null) {
            addDModCandidates(ids, variant.getHullMods());
            addDModCandidates(ids, variant.getPermaMods());
        }

        int count = 0;
        for (String id : ids) {
            if (isDMod(id)) {
                count++;
            }
        }
        if (count == 0 && variant != null && variant.hasDMods()) {
            return 1;
        }
        return count;
    }

    private void addDModCandidates(Set<String> ids, Iterable<String> source) {
        if (ids == null || source == null) {
            return;
        }
        for (String id : source) {
            String safeId = safe(id);
            if (!safeId.isEmpty()) {
                ids.add(safeId);
            }
        }
    }

    private boolean isDMod(String modId) {
        String id = safe(modId);
        if (id.isEmpty()) {
            return false;
        }
        try {
            HullModSpecAPI mod = Global.getSettings().getHullModSpec(id);
            if (mod != null && mod.hasTag("dmod")) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return FALLBACK_D_MOD_IDS.contains(id);
    }

    private boolean hasMarketToken(ShipHullSpecAPI spec, String expected) {
        if (spec == null || expected == null) {
            return false;
        }
        String token = expected.trim().toLowerCase(Locale.ROOT);
        if (spec.getHints() != null) {
            for (Object hint : spec.getHints()) {
                if (token.equals(safe(String.valueOf(hint)).trim().toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        if (spec.getTags() != null) {
            for (String tag : spec.getTags()) {
                if (token.equals(safe(tag).trim().toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return token.equals(safe(spec.getLogisticsNAReason()).trim().toLowerCase(Locale.ROOT));
    }

    private int safeOrdnancePoints(ShipHullSpecAPI spec) {
        try {
            return spec.getOrdnancePoints(null);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private float weight(String key) {
        return valueOrDefault(weights, key, 0f);
    }

    private static String sizeKey(ShipAPI.HullSize size) {
        if (size == null) {
            return "FRIGATE";
        }
        return size.name();
    }

    private static float valueOrDefault(Map<String, Float> map, String key, float fallback) {
        Float value = map.get(key);
        return value == null ? fallback : value;
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

    public static class HullValueResult {
        public final ShipHullSpecAPI spec;
        public final String hullId;
        public final String hullName;
        public final String sizeKey;
        public final float vanillaBaseValue;
        public final int computedHullValue;
        public final boolean marketHull;
        public final String notes;
        public final String sourceModId;

        public HullValueResult(ShipHullSpecAPI spec, String sizeKey, int computedHullValue,
                               boolean marketHull, String notes) {
            this.spec = spec;
            this.hullId = spec.getHullId();
            this.hullName = spec.getHullNameWithDashClass();
            this.sizeKey = sizeKey;
            this.vanillaBaseValue = spec.getBaseValue();
            this.computedHullValue = computedHullValue;
            this.marketHull = marketHull;
            this.notes = notes;
            ModSpecAPI source = spec.getSourceMod();
            this.sourceModId = source == null ? "" : source.getId();
        }
    }
}
