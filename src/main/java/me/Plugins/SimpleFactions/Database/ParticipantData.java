package me.Plugins.SimpleFactions.Database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParticipantData {
    public String leader;
    public List<String> subjects = new ArrayList<>();
    public Map<String, Boolean> allies = new HashMap<>();
    public List<String> backers = new ArrayList<>();
    /** @deprecated v1 per-participant goals; omitted on v2 write when war has top-level goal. */
    public Map<String, String> warGoals = new HashMap<>();
    public boolean civilWar;
}
