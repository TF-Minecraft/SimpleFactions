package net.tfminecraft.simplefactions.vehicles.battle;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/** Keeps the optional event listener out of older VehicleFramework class loading. */
public final class ServerVehicleUseRegistration {
    private ServerVehicleUseRegistration() {}

    public static void register(PluginManager plugins, Plugin plugin) {
        register(plugins, plugin, "net.tfminecraft.vehicleframework.events.ServerVehicleUseEvent");
    }

    static void register(PluginManager plugins, Plugin plugin, String eventClass) {
        register(plugins, plugin, eventClass, "net.tfminecraft.simplefactions.vehicles.battle.ServerVehicleUseListener");
    }

    static void register(PluginManager plugins, Plugin plugin, String eventClass, String listenerClass) {
        try {
            Class.forName(eventClass);
        } catch (ClassNotFoundException | LinkageError missing) {
            return;
        }
        try {
            Listener listener = (Listener) Class.forName(listenerClass)
                    .getConstructor().newInstance();
            plugins.registerEvents(listener, plugin);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not register server vehicle battle access", error);
        }
    }
}
