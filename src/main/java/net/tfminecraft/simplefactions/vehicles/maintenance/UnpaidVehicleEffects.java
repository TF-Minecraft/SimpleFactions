package net.tfminecraft.simplefactions.vehicles.maintenance;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What unpaid upkeep does to one vehicle type, from vehicles.yml {@code unpaid-effects}.
 *
 * @param hourlyDamagePercent  share of each part's max health lost per maintenance interval
 * @param minHealthPercent     remaining-health floor for every part
 * @param partMinHealthPercent floors for single parts, keyed by component type or weapon id
 * @param keepAfloat           a floating hull never decays far enough to sink
 * @param keepAloft            a balloon never decays far enough to lose lift
 * @param blockRepair          the vehicle cannot be repaired while unpaid
 */
public record UnpaidVehicleEffects(
        double hourlyDamagePercent,
        double minHealthPercent,
        Map<String, Double> partMinHealthPercent,
        boolean keepAfloat,
        boolean keepAloft,
        boolean blockRepair) {

    public static final UnpaidVehicleEffects DEFAULT =
            new UnpaidVehicleEffects(20, 3, Map.of(), true, true, true);

    public UnpaidVehicleEffects {
        Map<String, Double> parts = new HashMap<>();
        if (partMinHealthPercent != null) {
            partMinHealthPercent.forEach((part, floor) -> parts.put(part.toLowerCase(Locale.ROOT), floor));
        }
        partMinHealthPercent = Map.copyOf(parts);
    }

    public boolean decays() {
        return hourlyDamagePercent > 0;
    }
}
