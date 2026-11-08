package net.inertiamc.anythinginabucket.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Message keys and permissions resolve by name at runtime, so a missing one is a blank message or a silent default,
 * never a compile error. This checks the Java against messages.yml and plugin.yml both ways.
 */
public class KeyContractTest {

    private static final Pattern MESSAGE_CALL = Pattern.compile(
            "(?:sendMessage|sendRawMessage|getRawMessage|getStringList|deny)\\((?:[a-zA-Z().]+, )?\"([a-z][a-z0-9-]*(?:\\.[a-z0-9-]+)+)\"");
    private static final Pattern TERNARY_KEY = Pattern.compile("\\? \"([a-z]+\\.[a-z0-9-]+)\" : \"([a-z]+\\.[a-z0-9-]+)\"");
    private static final Pattern PERMISSION = Pattern.compile("\"(aiab\\.[a-z.]+[a-z])\"");
    /** Read by core under these names: the error keys and the reload reply. */
    private static final Set<String> CORE_READS = Set.of("error.usage", "error.no-permission", "error.only-players",
            "error.player-unknown", "error.player-offline", "error.invalid-number", "command.reload-error",
            "command.reload-problems", "command.reload-success", "command.reload-restart");
    /** Built from a fact's label or word: {@code "filled.labels." + label}, {@code "filled.words." + word}. */
    private static final Set<String> BUILT = Set.of("filled.labels.health", "filled.labels.age", "filled.labels.owner",
            "filled.labels.color", "filled.labels.markings", "filled.labels.variant", "filled.labels.profession",
            "filled.labels.level", "filled.labels.trades", "filled.labels.sheared", "filled.labels.saddle",
            "filled.labels.armour", "filled.labels.chest", "filled.labels.speed", "filled.labels.jump",
            "filled.words.adult", "filled.words.baby", "filled.words.yes-word", "filled.words.no-word", "filled.words.unknown",
            "filled.words.item-one", "filled.words.items", "filled.words.speed", "filled.words.jump",
            "filled.words.novice", "filled.words.apprentice", "filled.words.journeyman", "filled.words.expert",
            "filled.words.master", "filled.words.two-colors", "filled.labels.personality", "filled.labels.rust",
            "filled.labels.size", "filled.words.none", "filled.words.small", "filled.words.medium", "filled.words.big");

    private static String sources() throws IOException {
        StringBuilder all = new StringBuilder();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                all.append(Files.readString(file, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return all.toString();
    }

    private static Set<String> leafKeys(ConfigurationSection section) {
        Set<String> out = new TreeSet<>();
        for (String key : section.getKeys(true)) if (!section.isConfigurationSection(key)) out.add(key);
        out.remove("config-version");
        out.remove("prefix");
        return out;
    }

    @Test
    void everyMessageTheCodeSendsExistsAndEveryMessageIsSent() throws IOException {
        String code = sources();
        Set<String> used = new TreeSet<>(CORE_READS);
        used.addAll(BUILT);
        Matcher call = MESSAGE_CALL.matcher(code);
        while (call.find()) used.add(call.group(1));
        Matcher ternary = TERNARY_KEY.matcher(code);
        while (ternary.find()) {
            used.add(ternary.group(1));
            used.add(ternary.group(2));
        }
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(new File("src/main/resources/messages.yml"));
        Set<String> file = leafKeys(messages);
        // config.yml reads with the same method names; only keys under a messages.yml section are messages.
        used.removeIf(key -> !messages.isConfigurationSection(key.substring(0, key.indexOf('.'))));
        Set<String> missing = new TreeSet<>(used);
        missing.removeAll(file);
        Set<String> unused = new TreeSet<>(file);
        unused.removeAll(used);
        assertEquals(Set.of(), missing, "keys the code sends that messages.yml lacks");
        assertEquals(Set.of(), unused, "keys in messages.yml nothing sends");
    }

    @Test
    void everyBuiltKeyIsOneTheCodeCanMake() throws IOException {
        String code = sources();
        for (String key : BUILT) {
            String last = key.substring(key.lastIndexOf('.') + 1);
            assertTrue(code.contains("\"" + last + "\""), key);
        }
    }

    /** plugin.yml's permissions: its keys hold dots, so they are read with another path separator. */
    public static Set<String> declaredPermissions() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().pathSeparator('/');
        try {
            yaml.load(new File("src/main/resources/plugin.yml"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new TreeSet<>(yaml.getConfigurationSection("permissions").getKeys(false));
    }

    @Test
    void everyPermissionIsDeclaredAndEveryDeclaredOneChecked() throws IOException {
        Set<String> code = new TreeSet<>();
        Matcher matcher = PERMISSION.matcher(sources());
        while (matcher.find()) code.add(matcher.group(1));
        Set<String> declared = declaredPermissions();
        declared.remove("aiab.admin");
        assertEquals(declared, code);
    }
}
