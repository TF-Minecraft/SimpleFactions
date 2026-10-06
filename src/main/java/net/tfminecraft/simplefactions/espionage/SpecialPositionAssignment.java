package net.tfminecraft.simplefactions.espionage;

import java.util.UUID;

public class SpecialPositionAssignment {
    public UUID playerId;
    public String playerName;
    public String characterId;
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
}
