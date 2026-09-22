package me.Plugins.SimpleFactions.Database;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JsonUtil {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static <T> T readJson(File file, Class<T> type) throws IOException {
        try (FileReader r = new FileReader(file)) {
            return GSON.fromJson(r, type);
        }
    }

    public static void writeJson(File file, Object data) throws IOException {
        try (FileWriter w = new FileWriter(file)) {
            GSON.toJson(data, w);
        }
    }
}
