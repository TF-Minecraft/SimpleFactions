package me.Plugins.SimpleFactions.Utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.SimpleFactions.enums.FactionModifiers;

public class ModifierMerger {

    private ModifierMerger() {
        // utility class
    }

    /**
     * Merges modifiers so that each FactionModifiers type
     * appears at most once, with amounts summed.
     */
    public static List<FactionModifier> merge(List<FactionModifier> modifiers) {
        return merge(null, modifiers);
    }

    public static List<FactionModifier> merge(me.Plugins.SimpleFactions.Objects.Faction owner, List<FactionModifier> modifiers) {

        Map<FactionModifiers, Double> totals = new LinkedHashMap<>();

        for (FactionModifier mod : modifiers) {
            if (mod == null) continue;

            totals.merge(
                mod.getType(),
                mod.resolve(owner),
                Double::sum
            );
        }

        List<FactionModifier> result = new ArrayList<>();

        for (Map.Entry<FactionModifiers, Double> entry : totals.entrySet()) {
            double amount = entry.getValue();
            if (amount < -100) amount = -100;
            if (amount != 0) {
                result.add(new FactionModifier(entry.getKey(), amount));
            }
        }

        return result;
    }
}
