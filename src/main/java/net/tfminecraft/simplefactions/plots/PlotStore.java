package net.tfminecraft.simplefactions.plots;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.tfminecraft.simplefactions.database.JsonUtil;

/** Plots, each character's intrigue settings, retarget cooldowns and messages waiting for a character. */
public final class PlotStore {
    public List<Plot> plots = new ArrayList<>();
    public Map<String, IntrigueSettings> characters = new LinkedHashMap<>();
    /** "master character|target character" to the epoch millis the master may target them again. */
    public Map<String, Long> cooldowns = new LinkedHashMap<>();
    public Map<String, List<String>> notices = new LinkedHashMap<>();
    /** "participant character|target character" pairs that may never plot again: the target survived. */
    public java.util.Set<String> bans = new java.util.LinkedHashSet<>();
    private transient Path file;

    public static PlotStore load(Path file) throws IOException {
        PlotStore store = Files.exists(file) ? JsonUtil.GSON.fromJson(Files.readString(file), PlotStore.class) : new PlotStore();
        if (store == null) throw new IOException("Empty plot file: " + file);
        if (store.plots == null) store.plots = new ArrayList<>();
        if (store.characters == null) store.characters = new LinkedHashMap<>();
        if (store.cooldowns == null) store.cooldowns = new LinkedHashMap<>();
        if (store.notices == null) store.notices = new LinkedHashMap<>();
        if (store.bans == null) store.bans = new java.util.LinkedHashSet<>();
        store.plots.removeIf(plot -> plot == null || plot.code == null || plot.master == null || plot.phase == null);
        store.characters.values().removeIf(java.util.Objects::isNull);
        store.characters.values().forEach(settings -> {
            if (settings.investigators == null) settings.investigators = new ArrayList<>();
        });
        store.file = file;
        return store;
    }

    public void save() throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path pending = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(pending, JsonUtil.GSON.toJson(this));
        try { Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) {
            Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public IntrigueSettings settings(String characterId) {
        return characters.computeIfAbsent(characterId, id -> new IntrigueSettings());
    }

    /** Settings without recording a character that never changed them. */
    public IntrigueSettings peek(String characterId) {
        IntrigueSettings settings = characters.get(characterId);
        return settings == null ? new IntrigueSettings() : settings;
    }

    public Plot plot(String code) {
        for (Plot plot : plots) if (plot.code.equalsIgnoreCase(code)) return plot;
        return null;
    }

    /** The active plot this character leads or has joined; informed characters are not part of one. */
    public Plot plotOf(String characterId) {
        for (Plot plot : plots) if (plot.phase.active() && plot.involves(characterId)) return plot;
        return null;
    }

    public List<Plot> against(String characterId) {
        return plots.stream().filter(plot -> plot.phase.active() && plot.isTarget(characterId)).toList();
    }
}
