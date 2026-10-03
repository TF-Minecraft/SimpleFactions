package net.tfminecraft.simplefactions.espionage;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import net.tfminecraft.rpcharacters.permadeath.CharacterPermakillEvent;
import net.tfminecraft.simplefactions.SimpleFactions;

public final class OfficeCharacterDeathListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCharacterDeath(CharacterPermakillEvent event) {
        if (event.isCancelled()) return;
        // RPCharacters fires before committing death. Confirm the resulting status next tick.
        SimpleFactions.plugin.getServer().getScheduler().runTask(SimpleFactions.plugin,
                () -> EspionageService.characterDied(event.getPlayer(), event.getCharacter()));
    }
}
