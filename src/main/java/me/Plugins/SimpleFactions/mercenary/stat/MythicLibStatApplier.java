package me.Plugins.SimpleFactions.mercenary.stat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.SharedStat;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierType;

/**
 * Mana and mana regen ride on MythicLib stat modifiers against MMOCore's stats;
 * max health is a plain attribute modifier under a plugin key so stripping it is
 * idempotent even after a restart lost the in-memory bookkeeping.
 */
public final class MythicLibStatApplier implements MercenaryStatApplier {
    private static final String KEY_PREFIX = "simplefactions_mercenary_";
    private static final NamespacedKey HEALTH_KEY =
            new NamespacedKey("simplefactions", "mercenary_max_health");

    private final Map<UUID, List<StatModifier>> registered = new HashMap<>();

    @Override
    public boolean isAvailable() {
        return pluginEnabled("MythicLib") && pluginEnabled("MMOCore");
    }

    @Override
    public void apply(Player player, MercenaryStatPlan plan) {
        MMOPlayerData data = MMOPlayerData.getOrNull(player);
        if (data != null) {
            List<StatModifier> modifiers = new ArrayList<>();
            if (plan.maxMana() > 0) {
                modifiers.add(register(data, SharedStat.MAX_MANA, plan.maxMana()));
            }
            if (plan.manaRegen() > 0) {
                modifiers.add(register(data, SharedStat.MANA_REGENERATION, plan.manaRegen()));
            }
            registered.put(player.getUniqueId(), modifiers);
        }
        applyHealth(player, plan.maxHealth());
    }

    @Override
    public void strip(Player player) {
        List<StatModifier> modifiers = registered.remove(player.getUniqueId());
        if (modifiers != null && !modifiers.isEmpty()) {
            MMOPlayerData data = MMOPlayerData.getOrNull(player);
            if (data != null) {
                for (StatModifier modifier : modifiers) {
                    modifier.unregister(data);
                }
            }
        }
        removeHealth(player);
    }

    private static StatModifier register(MMOPlayerData data, String stat, double amount) {
        StatModifier modifier = new StatModifier(
                KEY_PREFIX + stat.toLowerCase(), stat, amount, ModifierType.FLAT);
        modifier.register(data);
        return modifier;
    }

    private static void applyHealth(Player player, double amount) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) return;
        removeHealth(player);
        if (amount <= 0) return;
        attribute.addModifier(new AttributeModifier(
                HEALTH_KEY, amount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
    }

    private static void removeHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) return;
        for (AttributeModifier modifier : new ArrayList<>(attribute.getModifiers())) {
            if (HEALTH_KEY.equals(modifier.getKey())) {
                attribute.removeModifier(modifier);
            }
        }
    }

    private static boolean pluginEnabled(String name) {
        return Bukkit.getServer() != null && Bukkit.getPluginManager().isPluginEnabled(name);
    }
}
