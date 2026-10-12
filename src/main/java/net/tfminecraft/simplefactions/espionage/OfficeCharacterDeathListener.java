package net.tfminecraft.simplefactions.espionage;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import net.tfminecraft.tlibs.character.CharacterDeathEvent;
import net.tfminecraft.tlibs.character.Characters;

public final class OfficeCharacterDeathListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCharacterDeath(CharacterDeathEvent event) {
        var ref = event.getCharacter();
        String name = Characters.info(ref.characterId()).filter(info -> info.ref().equals(ref))
                .map(net.tfminecraft.tlibs.character.CharacterInfo::name).orElse("your character");
        EspionageService.characterDied(ref, event.getDeathId(), event.getAt(), name);
    }
}
