package me.Plugins.SimpleFactions.Utils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;

public class FactionCleanup {

    private static final int MAX_DAYS_OFFLINE = 21; // Customize this
    private static final File LOGIN_FILE = new File("plugins/SimpleFactions/Cache", "logins.json");


    public static void kickInactiveMembers(List<Faction> factions) {
        Map<String, Integer> offlineDays;
        try {
            offlineDays = loadOfflineDays();
            if (offlineDays == null) return;

            // Add missing members before incrementing
            for (Faction faction : factions) {
                for (String member : faction.getMembers()) {
                    String name = member.toLowerCase();
                    if(name.startsWith("dummy_")) continue;
                    offlineDays.putIfAbsent(name, 0); // If not tracked yet, start at 0
                }
            }

            // Increment all days by 1
            for (Map.Entry<String, Integer> entry : offlineDays.entrySet()) {
                offlineDays.put(entry.getKey(), entry.getValue() + 1);
            }

            for (Faction faction : new ArrayList<>(factions)) {
                List<String> members = new ArrayList<>(faction.getMembers()); // Avoid ConcurrentModificationException

                for (String member : members) {
                    if(member.startsWith("dummy_")) continue;
                    int daysOffline = offlineDays.getOrDefault(member.toLowerCase(), 0);
                    if(!faction.getMembers().contains(member)) continue;
                    if (daysOffline >= MAX_DAYS_OFFLINE) {
                        if (faction.getLeader().equalsIgnoreCase(member)) {
                            if (faction.getMembers().size() == 1) {
                                FactionManager.deleteFaction(faction);
                            }
                            continue;
                        }
                        if(!faction.canBeCleanKicked(member)) continue;
                        faction.forceRemoveMember(member);
                        System.out.println("Kicked " + member + " from faction " + faction.getName() + " (offline for " + daysOffline + " days)");
                    }
                }
            }

            // Save updated offlineDays back to file
            saveOfflineDays(offlineDays);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Map<String, Integer> loadOfflineDays() throws IOException {
        if (!LOGIN_FILE.exists()) {
            LOGIN_FILE.createNewFile();
            return new HashMap<>(); // Return empty map instead of null
        }

        try (FileReader reader = new FileReader(LOGIN_FILE)) {
            Type mapType = new TypeToken<Map<String, Integer>>() {}.getType();
            return new Gson().fromJson(reader, mapType);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void saveOfflineDays(Map<String, Integer> data) throws IOException {
        if (!LOGIN_FILE.exists()) {
            LOGIN_FILE.createNewFile();
        }
        try (FileWriter writer = new FileWriter(LOGIN_FILE)) {
            new Gson().toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void ping(String username) {
        try {
            Map<String, Integer> offlineDays = loadOfflineDays();
            if (offlineDays == null) {
                offlineDays = new HashMap<>();
            }

            offlineDays.put(username.toLowerCase(), 0); // Reset to 0

            saveOfflineDays(offlineDays);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

