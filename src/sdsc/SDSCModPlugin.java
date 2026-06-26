package sdsc;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.loading.specs.g;
import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class SDSCModPlugin extends BaseModPlugin {
    public static final String MOD_ID = "stat_derived_ship_costs";
    private static final Logger LOG = Global.getLogger(SDSCModPlugin.class);

    private static boolean listenerRegistered = false;
    private static boolean lastApplyAttempted = false;

    @Override
    public void onApplicationLoad() throws Exception {
        registerLunaListener();
        runPricingPass("application-load");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        registerSectorListener();
        runPricingPass(newGame ? "new-game-load" : "game-load");
        SDSCMarketListener.repriceAllMarkets(newGame ? "new-game-load" : "game-load");
    }

    @Override
    public void onNewGameAfterEconomyLoad() {
        registerSectorListener();
        runPricingPass("new-game-after-economy-load");
        SDSCMarketListener.repriceAllMarkets("new-game-after-economy-load");
    }

    @Override
    public void onDevModeF8Reload() {
        registerSectorListener();
        runPricingPass("devmode-reload");
        SDSCMarketListener.repriceAllMarkets("devmode-reload");
    }

    private static void registerLunaListener() {
        if (listenerRegistered) {
            return;
        }
        try {
            if (!LunaSettings.hasSettingsListenerOfClass(SettingsListener.class)) {
                LunaSettings.addSettingsListener(new SettingsListener());
            }
            listenerRegistered = true;
        } catch (Throwable ex) {
            LOG.warn("Unable to register LunaLib settings listener; continuing with static settings.", ex);
        }
    }

    private static void registerSectorListener() {
        try {
            SectorAPI sector = Global.getSector();
            if (sector == null) {
                return;
            }
            ListenerManagerAPI listeners = sector.getListenerManager();
            if (listeners != null && !listeners.hasListenerOfClass(SDSCMarketListener.class)) {
                listeners.addListener(new SDSCMarketListener(), true);
                LOG.info("Registered Stat-Derived Ship Costs submarket listener.");
            }
            if (!hasTransactionGuard(sector)) {
                sector.addListener(new SDSCTransactionGuard());
                LOG.info("Registered Stat-Derived Ship Costs transaction guard.");
            }
        } catch (Throwable ex) {
            LOG.warn("Unable to register Stat-Derived Ship Costs campaign listeners.", ex);
        }
    }

    private static boolean hasTransactionGuard(SectorAPI sector) {
        if (sector == null || sector.getAllListeners() == null) {
            return false;
        }
        for (CampaignEventListener listener : sector.getAllListeners()) {
            if (listener instanceof SDSCTransactionGuard) {
                return true;
            }
        }
        return false;
    }

    static void runPricingPass(String reason) {
        try {
            SDSCSettings settings = SDSCSettings.load();
            SDSCFormula formula = new SDSCFormula(settings);
            List<SDSCFormula.HullValueResult> results = evaluateAllHulls(formula);

            boolean apply = settings.enablePricingHooks && !"Report Only".equalsIgnoreCase(settings.pricingMode);
            int applied = 0;
            int failed = 0;
            if (apply) {
                lastApplyAttempted = true;
                for (SDSCFormula.HullValueResult result : results) {
                    if (!result.marketHull) {
                        continue;
                    }
                    if (applyHullValue(result.spec, result.computedHullValue)) {
                        applied++;
                    } else {
                        failed++;
                    }
                }
            } else {
                lastApplyAttempted = false;
            }

            if (settings.debugReports || "Report Only".equalsIgnoreCase(settings.pricingMode)) {
                writeReport(results, reason, settings.pricingMode, applied, failed);
            }

            LOG.info("Stat-Derived Ship Costs pass complete: reason=" + reason
                    + ", mode=" + settings.pricingMode
                    + ", hulls=" + results.size()
                    + ", applied=" + applied
                    + ", failed=" + failed);
        } catch (Throwable ex) {
            LOG.error("Stat-Derived Ship Costs pricing pass failed during " + reason, ex);
        }
    }

    private static List<SDSCFormula.HullValueResult> evaluateAllHulls(SDSCFormula formula) {
        Collection<ShipHullSpecAPI> specs = Global.getSettings().getAllShipHullSpecs();
        List<ShipHullSpecAPI> sorted = new ArrayList<ShipHullSpecAPI>(specs);
        sorted.sort(Comparator.comparing(ShipHullSpecAPI::getHullId));

        List<SDSCFormula.HullValueResult> results = new ArrayList<SDSCFormula.HullValueResult>();
        for (ShipHullSpecAPI spec : sorted) {
            results.add(formula.estimateHull(spec));
        }
        return results;
    }

    static boolean applyHullValue(ShipHullSpecAPI spec, int value) {
        if (spec instanceof g) {
            ((g) spec).setBaseValue((float) value);
            return true;
        }
        LOG.warn("Unable to set base value for hull " + spec.getHullId()
                + " using unsupported implementation " + spec.getClass().getName());
        return false;
    }

    private static void writeReport(List<SDSCFormula.HullValueResult> results, String reason,
                                    String pricingMode, int applied, int failed) {
        StringBuilder out = new StringBuilder();
        out.append("# Stat-Derived Ship Costs hull report\n");
        out.append("# reason=").append(reason)
                .append(", mode=").append(pricingMode)
                .append(", applyAttempted=").append(lastApplyAttempted)
                .append(", applied=").append(applied)
                .append(", failed=").append(failed)
                .append("\n");
        out.append("hull_id,hull_name,size,market_hull,vanilla_base,computed_hull,ratio,source_mod,notes\n");
        for (SDSCFormula.HullValueResult r : results) {
            float ratio = r.vanillaBaseValue <= 0f ? 0f : r.computedHullValue / r.vanillaBaseValue;
            out.append(csv(r.hullId)).append(',')
                    .append(csv(r.hullName)).append(',')
                    .append(csv(r.sizeKey)).append(',')
                    .append(r.marketHull).append(',')
                    .append(Math.round(r.vanillaBaseValue)).append(',')
                    .append(r.computedHullValue).append(',')
                    .append(String.format(java.util.Locale.US, "%.3f", ratio)).append(',')
                    .append(csv(r.sourceModId)).append(',')
                    .append(csv(r.notes))
                    .append('\n');
        }

        try {
            Global.getSettings().writeTextFileToCommon("sdsc_hull_value_report.csv", out.toString());
            LOG.info("Wrote hull valuation report to saves/common/sdsc_hull_value_report.csv");
        } catch (Throwable ex) {
            LOG.warn("Unable to write hull valuation report.", ex);
        }
    }

    private static String csv(String value) {
        if (value == null) {
            value = "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    public static class SettingsListener implements LunaSettingsListener {
        @Override
        public void settingsChanged(String modID) {
            if (MOD_ID.equals(modID)) {
                runPricingPass("lunalib-settings-changed");
                SDSCMarketListener.repriceAllMarkets("lunalib-settings-changed");
            }
        }
    }
}
