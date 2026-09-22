package me.Plugins.SimpleFactions.mercenary.stat;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

/** Stands in for MythicLib and the health attribute. */
final class RecordingStatApplier implements MercenaryStatApplier {
    boolean available = true;
    final List<MercenaryStatPlan> applications = new ArrayList<>();
    int strips;

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void apply(Player player, MercenaryStatPlan plan) {
        applications.add(plan);
    }

    @Override
    public void strip(Player player) {
        strips++;
    }
}
