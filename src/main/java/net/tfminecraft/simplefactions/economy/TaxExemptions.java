package net.tfminecraft.simplefactions.economy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterRef;

/**
 * Characters that helpers and staff play as NPCs or lore characters, whose earnings owe no citizen
 * tax. The exemption belongs to one character, so the account's other characters stay taxed. It
 * only applies while the owning account is online and holds {@link #PERMISSION}: losing the
 * permission (going off duty, demotion) taxes the character again without clearing the setting.
 */
public final class TaxExemptions {
    public static final String PERMISSION = "simplefactions.taxexempt";
    static final String FILE_NAME = "tax-exempt-characters.json";

    /** The saved file: lower-case character IDs. */
    static final class SavedState {
        List<String> characters = new ArrayList<>();
    }

    private static final Set<String> exempt = new HashSet<>();
    private static Path file;
    /** Set when the file could not be read, so it is never overwritten and nothing is exempt. */
    private static boolean blocked;
    private static Predicate<UUID> permitted = TaxExemptions::onlineWithPermission;

    private TaxExemptions() {}

    /** Uses {@code folder/tax-exempt-characters.json}. */
    public static synchronized void bind(Path folder) {
        file = folder == null ? null : folder.resolve(FILE_NAME);
    }

    public static synchronized void load() {
        exempt.clear();
        blocked = false;
        if (file == null || !Files.exists(file)) return;
        try {
            SavedState state = JsonUtil.readJson(file.toFile(), SavedState.class);
            if (state == null) throw new IOException(FILE_NAME + " is empty");
            if (state.characters != null) {
                for (String key : state.characters) {
                    String normalized = normalize(key);
                    if (normalized != null) exempt.add(normalized);
                }
            }
        } catch (IOException | RuntimeException error) {
            // Everyone stays taxed and the unreadable file is kept for staff.
            exempt.clear();
            blocked = true;
            log("Cannot read " + FILE_NAME + "; no character is tax exempt until it is fixed", error);
        }
    }

    /** Whether this earning owes no citizen tax. */
    public static synchronized boolean exempt(CharacterRef earner) {
        if (earner == null || exempt.isEmpty()) return false;
        return exempt.contains(Members.key(earner.characterId())) && permitted.test(earner.ownerId());
    }

    /** Whether the character is set exempt, whatever its owner's permission. */
    public static synchronized boolean isSet(String key) {
        return key != null && exempt.contains(key);
    }

    /**
     * Turns the character's exemption on or off and saves it before returning. Returns false when
     * it was already so. On a failed save nothing changes.
     */
    public static synchronized boolean set(String key, boolean on) throws IOException {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("No character");
        if (blocked) throw new IOException(FILE_NAME + " could not be read at startup");
        if (file == null) throw new IOException("Tax exemptions are not loaded");
        if (on == exempt.contains(key)) return false;
        Set<String> next = new HashSet<>(exempt);
        if (on) next.add(key);
        else next.remove(key);
        SavedState state = new SavedState();
        state.characters.addAll(next);
        state.characters.sort(null);
        Files.createDirectories(file.getParent());
        JsonUtil.writeJsonAtomic(file.toFile(), state);
        exempt.clear();
        exempt.addAll(next);
        return true;
    }

    /** Every exempt character key, sorted. */
    public static synchronized List<String> keys() {
        List<String> keys = new ArrayList<>(exempt);
        keys.sort(null);
        return keys;
    }

    /** Test hook: who counts as holding the permission. */
    static synchronized void setPermissionCheck(Predicate<UUID> check) {
        permitted = check == null ? TaxExemptions::onlineWithPermission : check;
    }

    /** Test hook: forget everything, including the bound file. */
    static synchronized void reset() {
        exempt.clear();
        blocked = false;
        file = null;
        permitted = TaxExemptions::onlineWithPermission;
    }

    private static boolean onlineWithPermission(UUID ownerId) {
        Player owner = Bukkit.getPlayer(ownerId);
        return owner != null && owner.hasPermission(PERMISSION);
    }

    private static String normalize(String key) {
        if (key == null) return null;
        try {
            return Members.key(UUID.fromString(key.trim()));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static void log(String message, Throwable error) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin != null) plugin.getLogger().log(Level.SEVERE, message, error);
    }
}
