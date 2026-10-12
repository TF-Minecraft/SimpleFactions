package net.tfminecraft.simplefactions.espionage;

import java.util.UUID;

import net.tfminecraft.simplefactions.identity.Members;

public class SpecialPositionAssignment {
    public UUID playerId;
    /** Account name at binding, kept for older saves; membership uses {@link #memberKey()}. */
    public String playerName;
    public String characterId;
    // Character binding time, independent of an automatic founder's immediate full aptitude.
    public long characterBoundAt;
    public int aptitude;
    public boolean automatic;
    // Epoch millis of a deliberate appointment; zero (founders, older saves) means fully established.
    public long appointedAt;
    // Private, voluntary penalties. Zero means sabotage is disabled.
    public int offenseReduction;
    public int defenseReduction;

    public boolean isHolder(UUID id) {
        return id != null && id.equals(playerId);
    }

    /** Member key of the bound character; null while no character is bound. */
    public String memberKey() {
        return Members.key(OfficeCharacters.characterUuid(characterId));
    }
}
