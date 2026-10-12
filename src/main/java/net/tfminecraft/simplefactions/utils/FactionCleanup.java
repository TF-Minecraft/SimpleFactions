package net.tfminecraft.simplefactions.utils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.inactivity.InactivityRules;
import net.tfminecraft.simplefactions.objects.Faction;

public class FactionCleanup {

    private static final File LOGIN_FILE = new File("plugins/SimpleFactions/Cache", "logins.json");
    private static Map<String, Integer> offlineDays;

    /**
     * Counts a day offline for members (keyed by character) whose character is
     * not being played. Members who
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
                        offlineDays.putIfAbsent(member, 0);
                        members.add(member);
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
                    log(Members.display(name) + " (" + name + ") is inactive after " + after + " days offline and stays in their guild");
                }
            }

            saveOfflineDays(offlineDays);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Pure day snapshot; recovery applies this exact map instead of incrementing it again. */
    public static Map<String, Integer> planOfflineDays(List<Faction> factions) throws IOException {
        if (!ensureLoaded()) throw new IOException("Cannot read offline days");
        Map<String, Integer> result = new HashMap<>(offlineDays);
        for (Faction faction : factions) {
            for (String member : faction.getMembers()) {
                if (InactivityRules.isTrackedMember(member)) result.putIfAbsent(member, 0);
            }
        }
        result.replaceAll((member, days) -> isOnline(member) ? 0 : Math.addExact(days == null ? 0 : days, 1));
        return result;
    }

    public static void restoreOfflineDays(Map<String, Integer> values) throws IOException {
        net.tfminecraft.simplefactions.database.DurableJson.write(LOGIN_FILE.toPath(), values);
        offlineDays = new HashMap<>(values);
    }

    public static int daysOffline(String member) {
        if (member == null) return 0;
        if (!ensureLoaded()) return 0;
        Integer days = offlineDays.get(member);
        return days == null ? 0 : days;
    }

    public static void ping(String member) {
        if (member == null || member.isBlank()) return;
        try {
            if (!ensureLoaded()) return;
            offlineDays.put(member, 0);
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

    private static boolean isOnline(String member) {
        return Bukkit.getServer() != null && Members.isOnline(member);
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
