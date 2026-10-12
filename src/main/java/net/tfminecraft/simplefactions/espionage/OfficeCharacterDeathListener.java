package net.tfminecraft.simplefactions.espionage;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.identity.death.CharacterDeaths;
import net.tfminecraft.tlibs.character.CharacterDeathEvent;

public final class OfficeCharacterDeathListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCharacterDeath(CharacterDeathEvent event) {
        try {
            CharacterDeaths.onDeath(event.getCharacter(), event.getDeathId(), event.getAt());
        } catch (Exception error) {
            var plugin = SimpleFactions.getInstance();
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Character death cleanup failed; disabling factions until the saved journal can recover", error);
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }
}
