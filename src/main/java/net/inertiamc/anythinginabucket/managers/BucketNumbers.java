package net.inertiamc.anythinginabucket.managers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Look numbers from {@code numbers.txt}. Before 1.21.4 an item has a single custom model data number and no strings, so
 * the resource pack picks the art by this number on those versions, including older clients joining a newer server
 * through ViaVersion.
 */
public final class BucketNumbers {

    private static final Map<String, Integer> NUMBERS = load();

    private BucketNumbers() {}

    /** The look's number, else its type's, else null (a mob the pack has no art for). */
    public static Integer of(String head, String mob) {
        Integer n = NUMBERS.get(head);
        return n != null ? n : NUMBERS.get(mob);
    }

    static Map<String, Integer> read(Reader in) throws IOException {
        Map<String, Integer> found = new HashMap<>();
        BufferedReader lines = new BufferedReader(in);
        for (String line; (line = lines.readLine()) != null; ) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split(" ");
            found.put(parts[1], Integer.parseInt(parts[0]));
        }
        return found;
    }

    private static Map<String, Integer> load() {
        try (InputStream in = BucketNumbers.class.getResourceAsStream("/numbers.txt")) {
            return in == null ? Map.of() : Collections.unmodifiableMap(read(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException broken) {
            return Map.of();
        }
    }
}
