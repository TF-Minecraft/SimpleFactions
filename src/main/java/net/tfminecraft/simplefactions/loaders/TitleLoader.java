package net.tfminecraft.simplefactions.loaders;

import com.google.gson.*;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.tiers.Tier;
import net.tfminecraft.simplefactions.tiers.Title;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class TitleLoader {

    private static List<Title> titles = new ArrayList<>();
    private static final File inputFolder = new File("plugins/SimpleFactions/Input");

    public void reload() {
        loadAll();
        FactionManager.reloadTitles();
    }

    public void loadAll() {
        titles.clear();

        for (Tier tier : TierLoader.get()) {
            String filename = tier.getId().toLowerCase() + ".json";
            File file = new File(inputFolder, filename);

            if (!file.exists()) {
                continue;
            }

            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    String id = entry.getKey();
                    JsonObject data = entry.getValue().getAsJsonObject();
                    Title title = new Title(tier, id, data);
                    titles.add(title);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static List<Title> getTitles() {
        return titles;
    }

    public static Title getById(String id) {
        for (Title t : titles) {
            if (t.getId().equalsIgnoreCase(id)) return t;
        }
        return null;
    }
    
    public static Title getByProvince(int province) {
    	for(Title t : titles) {
    		if(t.getProvinces().contains(province)) return t;
    	}
    	return null;
    }
    
    public static Title getByTitle(Title t) {
    	for(Title title : titles) {
    		if(title.getTitles().contains(t.getId())) return title;
    	}
    	return null;
    }
    
    public static List<Title> getByTier(Tier tier){
    	List<Title> list = new ArrayList<>();
    	for(Title t : titles) {
    		if(t.getTier() == tier) list.add(t);
    	}
    	return list;
    }
    
    /** Writes one title's current state back to its tier file, keeping the other entries and their order. */
    public static boolean saveTitle(Title title) {
        return saveTitle(title, inputFolder);
    }

    static boolean saveTitle(Title title, File folder) {
        File file = new File(folder, title.getTier().getId().toLowerCase() + ".json");
        JsonObject root = new JsonObject();
        if (file.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                // Never overwrite a file we could not read: that would drop every other title in the tier.
                e.printStackTrace();
                return false;
            }
        }
        JsonObject entry = root.has(title.getId()) && root.get(title.getId()).isJsonObject()
                ? root.getAsJsonObject(title.getId())
                : new JsonObject();
        writeEntry(entry, title);
        root.add(title.getId(), entry);

        File tmp = new File(folder, file.getName() + ".tmp");
        try {
            folder.mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(root, writer);
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | JsonIOException e) {
            e.printStackTrace();
            tmp.delete();
            return false;
        }
    }

    static void writeEntry(JsonObject entry, Title title) {
        if (title.getName() != null) entry.addProperty("name", title.getName());
        if (title.getRgb() != null) entry.addProperty("rgb", title.getRgb());
        if (entry.has("title-complete") || title.isTitleComplete()) {
            entry.addProperty("title-complete", String.valueOf(title.isTitleComplete()));
        }
        if (entry.has("provinces") || !title.getProvinces().isEmpty()) {
            JsonArray provinceArray = new JsonArray();
            for (int provinceId : title.getProvinces()) provinceArray.add(provinceId);
            entry.add("provinces", provinceArray);
        }
        if (entry.has("titles") || !title.getTitles().isEmpty()) {
            JsonArray titleArray = new JsonArray();
            for (String titleId : title.getTitles()) titleArray.add(titleId);
            entry.add("titles", titleArray);
        }
    }

    //Create new
    
    public static Title createNewTitle(Tier tier, String id, String name, String rgb, List<Integer> provinces, List<String> usedTitles, boolean titleComplete) {
        File file = new File(inputFolder, tier.getId().toLowerCase() + ".json");
        JsonObject root = new JsonObject();

        // Load existing file content if exists
        if (file.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        JsonObject newTitle = new JsonObject();
        newTitle.addProperty("name", name);
        newTitle.addProperty("rgb", rgb);
        newTitle.addProperty("title-complete", String.valueOf(titleComplete));

        if (provinces != null && !provinces.isEmpty()) {
            JsonArray provinceArray = new JsonArray();
            for (int provinceId : provinces) {
                provinceArray.add(provinceId);
            }
            newTitle.add("provinces", provinceArray);
        }

        if (usedTitles != null && !usedTitles.isEmpty()) {
            JsonArray titleArray = new JsonArray();
            for (String titleId : usedTitles) {
                titleArray.add(titleId);
            }
            newTitle.add("titles", titleArray);
        }

        // Insert into the root JSON under the new ID
        root.add(id, newTitle);

        // Save the updated file
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(root, writer);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Optionally load it into the TitleLoader's memory now
        Title title = new Title(tier, id, newTitle);
        titles.add(title);
        return title;
    }

}

