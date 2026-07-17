package sdsc;

/**
 * Compatibility placeholder for saves that still reference the former
 * submarket listener. Per-submarket pricing cannot be stored on a hull spec:
 * hull specs are shared by every market, fleet, and tooltip in the campaign.
 *
 * Market-specific adjustments are instead calculated by SDSCTransactionGuard
 * at the point a player transaction supplies the required market and ship
 * context. Keeping no submarket references also prevents campaign objects
 * from being retained in a static cache.
 */
public class SDSCMarketListener {
    public static int repriceAllMarkets(String reason) {
        return 0;
    }

    public static int repriceSubmarket(Object submarket, String reason, boolean allowCreateCargo) {
        return 0;
    }
}