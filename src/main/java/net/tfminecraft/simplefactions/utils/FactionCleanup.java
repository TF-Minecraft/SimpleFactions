package net.tfminecraft.simplefactions.utils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.inactivity.InactivityRules;
import net.tfminecraft.simplefactions.objects.Faction;

public class FactionCleanup {

    private static final File LOGIN_FILE = new File("plugins/SimpleFactions/Cache", "logins.json");
    private static Map<String, Integer> offlineDays;

    /**
     * Counts a day offline for members who are not on the server. Members who
     * reach {@link InactivityRules#DAYS_UNTIL_INACTIVE} stay in the guild and
     * are marked inactive. They are not kicked, and a lone inactive leader
     * does not dissolve the faction.
     */
    public static void advanceOfflineDays(List<Faction> factions) {
        try {
            if (!ensureLoaded()) return;
            Set<String> members = new HashSet<>();

            if (factions != null) {
                for (Faction faction : factions) {
                    if (faction == null) continue;
                    for (String member : faction.getMembers()) {
                        if (!InactivityRules.isTrackedMember(member)) continue;
                        String key = member.toLowerCase(Locale.ROOT);
                        offlineDays.putIfAbsent(key, 0);
                        members.add(key);
                    }
                }
            }

            for (Map.Entry<String, Integer> entry : new HashMap<>(offlineDays).entrySet()) {
                String name = entry.getKey();
                int before = entry.getValue() == null ? 0 : entry.getValue();
                if (isOnline(name)) {
                    offlineDays.put(name, 0);
                    continue;
                }
                int after = before + 1;
                offlineDays.put(name, after);
                if (members.contains(name)
                        && !InactivityRules.isInactive(before)
                        && InactivityRules.isInactive(after)) {
                    log(name + " is inactive after " + after + " days offline and stays in their guild");
                }
            }

            saveOfflineDays(offlineDays);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int daysOffline(String username) {
        if (username == null) return 0;
        if (!ensureLoaded()) return 0;
        Integer days = offlineDays.get(username.toLowerCase(Locale.ROOT));
        return days == null ? 0 : days;
    }

    public static void ping(String username) {
        if (username == null || username.isBlank()) return;
        try {
            if (!ensureLoaded()) return;
            offlineDays.put(username.toLowerCase(Locale.ROOT), 0);
            saveOfflineDays(offlineDays);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean ensureLoaded() {
        if (offlineDays != null) return true;
        try {
            Map<String, Integer> loaded = loadOfflineDays();
            if (loaded == null) return false;
            offlineDays = loaded;
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean isOnline(String name) {
        try {
            if (Bukkit.getServer() == null || name == null) return false;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player != null && player.getName().equalsIgnoreCase(name)) return true;
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    private static Map<String, Integer> loadOfflineDays() throws IOException {
        if (!LOGIN_FILE.exists()) {
            LOGIN_FILE.getParentFile().mkdirs();
            LOGIN_FILE.createNewFile();
            return new HashMap<>();
        }

        try (FileReader reader = new FileReader(LOGIN_FILE)) {
            Type mapType = new TypeToken<Map<String, Integer>>() {}.getType();
            Map<String, Integer> loaded = new Gson().fromJson(reader, mapType);
            return loaded == null ? new HashMap<>() : loaded;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void saveOfflineDays(Map<String, Integer> data) throws IOException {
        File parent = LOGIN_FILE.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        if (!LOGIN_FILE.exists()) {
            LOGIN_FILE.createNewFile();
        }
        try (FileWriter writer = new FileWriter(LOGIN_FILE)) {
            new Gson().toJson(data, writer);
        }
    }

    private static void log(String message) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin != null && plugin.getLogger() != null) {
            plugin.getLogger().log(Level.INFO, message);
        }
    }
}
