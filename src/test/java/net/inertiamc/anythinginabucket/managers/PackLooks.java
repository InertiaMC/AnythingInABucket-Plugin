package net.inertiamc.anythinginabucket.managers;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Every look the resource pack in resourcepack/ has art for, read from its item definitions. */
final class PackLooks {

    private PackLooks() {}

    static Set<String> read() throws IOException {
        Set<String> looks = new HashSet<>();
        List<Path> items;
        try (Stream<Path> files = Files.list(Path.of("resourcepack/assets/minecraft/items"))) {
            items = files.filter(f -> f.toString().endsWith(".json")).toList();
        }
        for (Path item : items) {
            try (Reader in = Files.newBufferedReader(item)) {
                JsonElement model = JsonParser.parseReader(in).getAsJsonObject().get("model");
                if (!model.getAsJsonObject().has("cases")) continue;
                for (JsonElement c : model.getAsJsonObject().getAsJsonArray("cases")) {
                    looks.add(c.getAsJsonObject().get("when").getAsString());
                }
            }
        }
        return looks;
    }
}
