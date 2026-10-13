package net.tfminecraft.simplefactions.vehicles.owners;

import java.util.UUID;

import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.Characters;
import net.tfminecraft.vfbuilders.events.VehicleConstructEvent;

/** Who built a vehicle, as a character, for checking the registration-fee payer before stamping. */
public final class ConstructionStamp {
    private static Boolean characterApi;

    private ConstructionStamp() {}

    /** Whether VFBuilders 2.2.0 or later reports the constructor's character (rule 11 probe, cached). */
    static boolean characterApi() {
        if (characterApi == null) characterApi = hasCharacter(VehicleConstructEvent.class);
        return characterApi;
    }

    static boolean hasCharacter(Class<?> event) {
        try {
            event.getMethod("getConstructorCharacterId");
            return true;
        } catch (ReflectiveOperationException | LinkageError missing) {
            return false;
        }
    }

    static void resetProbe() {
        characterApi = null;
    }

    /**
     * The constructor's character: VFBuilders' capture when it has one, else the
     * account's reviewed heir (stations from before 2.2.0, builds started without a
     * current character). Null when neither is known.
     */
    public static UUID constructor(VehicleConstructEvent event) {
        UUID character = characterApi() ? event.getConstructorCharacterId() : null;
        if (character != null) return character;
        UUID account = event.getConstructorUuid();
        if (account == null) return null;
        return Characters.legacyHeir(account).filter(ref -> account.equals(ref.ownerId()))
                .map(CharacterRef::characterId).orElse(null);
    }
}
