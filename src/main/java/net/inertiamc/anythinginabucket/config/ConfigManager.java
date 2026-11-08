package net.inertiamc.anythinginabucket.config;

import net.inertiamc.core.config.ConfigFile;
import net.inertiamc.core.config.Durations;
import net.inertiamc.core.config.Problems;
import net.inertiamc.core.effect.SoundSpec;
import net.inertiamc.core.storage.DatabaseSettings;
import net.inertiamc.anythinginabucket.AnythingInABucket;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** config.yml and database.yml, parsed into a snapshot that a reload swaps whole. */
public final class ConfigManager {

    /** The sounds config.yml names, each with its default. */
    static final Map<String, String> SOUNDS = Map.of(
            "catch", "item.bucket.fill_axolotl",
            "release", "item.bucket.empty_axolotl",
            "deny", "entity.villager.no");

    /** The Capture Bucket's crafting recipe: up to three rows, each letter an ingredient. */
    public record RecipeSpec(List<String> shape, Map<Character, Material> ingredients) {}

    /** Which bucket catches mobs ({@code catch-with}). */
    public enum CatchWith {
        CAPTURE_BUCKET("capture-bucket"), NORMAL_BUCKET("normal-bucket"), BOTH("both");

        private final String key;

        CatchWith(String key) {
            this.key = key;
        }

        /** Whether the Capture Bucket is enabled (crafted, given and able to catch). */
        public boolean capture() {
            return this != NORMAL_BUCKET;
        }

        /** Whether a normal bucket catches mobs while sneaking. */
        public boolean normal() {
            return this != CAPTURE_BUCKET;
        }

        static CatchWith of(String key) {
            for (CatchWith value : values()) if (value.key.equalsIgnoreCase(key == null ? "" : key.trim())) return value;
            return null;
        }
    }

    /**
     * @param catchWith         which bucket catches
     * @param giveBack          whether releasing gives the empty bucket back
     * @param dispensersRelease whether a dispenser releases the mob in a filled bucket
     * @param blacklist         entity type keys no one can catch ({@code wither})
     * @param disabledWorlds    world folder names where nothing is caught or released
     * @param recipe            the Capture Bucket's recipe, or null when the recipe or the Capture Bucket is disabled
     * @param copyProtection    whether copied buckets are refused; when off, a filled bucket's mob is kept after
     *                          release, so every copy can release it again
     * @param petsOwnerOnly     whether only the owner can catch a tamed mob
     * @param respectClaims     whether protection plugins are checked before a catch or release
     * @param resourcePack      the bucket resource pack offered to joining players
     */
    public record Settings(CatchWith catchWith, boolean giveBack, boolean dispensersRelease, Set<String> blacklist, Duration cooldown,
                           Set<String> disabledWorlds, RecipeSpec recipe, Map<String, SoundSpec> sounds,
                           boolean copyProtection, boolean petsOwnerOnly, boolean respectClaims, ResourcePack resourcePack) {}

    /** Whether joining players are offered the bucket resource pack, and where it is downloaded from. */
    public record ResourcePack(boolean send, String url) {}

    /** Empty means the newest version of the pack's Modrinth project ({@code HeadPack.MODRINTH_PROJECT}). */
    static final String PACK_URL = "";

    private final AnythingInABucket plugin;
    private final ConfigFile configFile;
    private final DatabaseSettings database;
    @Getter private volatile Settings settings;
    @Getter private volatile int problemCount;

    public ConfigManager(AnythingInABucket plugin) {
        this.plugin = plugin;
        this.configFile = new ConfigFile(plugin, "config.yml");
        Problems problems = new Problems();
        ConfigFile databaseFile = new ConfigFile(plugin, "database.yml");
        databaseFile.load();
        this.database = DatabaseSettings.fromFile(databaseFile.config(), "anythinginabucket", problems.in("database.yml"));
        load(problems);
    }

    public DatabaseSettings database() {
        return database;
    }

    public SoundSpec sound(String key) {
        return settings.sounds().getOrDefault(key, SoundSpec.SILENT);
    }

    /** Reloads config.yml. On a syntax error the previous settings are kept and the error is returned. */
    public List<String> reload() {
        return load(new Problems());
    }

    private List<String> load(Problems problems) {
        List<String> errors = new ArrayList<>();
        String error = configFile.load();
        if (error != null) errors.add(error);
        settings = settings(configFile.config(), ConfigManager::isMob, problems.in("config.yml"));
        problems.log(plugin.getLogger());
        problemCount = problems.size();
        return errors;
    }

    /** Whether a key such as {@code ender_dragon} names a living entity type. */
    static boolean isMob(String key) {
        for (EntityType type : EntityType.values()) {
            if (type.isAlive() && type.getKey() != null && type.getKey().getKey().equals(key)) return true;
        }
        return false;
    }

    static Settings settings(ConfigurationSection c, Predicate<String> isMob, Problems.InFile problems) {
        String rawCatchWith = c.getString("catch-with", "capture-bucket");
        CatchWith catchWith = CatchWith.of(rawCatchWith);
        if (catchWith == null) {
            problems.add("catch-with", "'" + rawCatchWith + "' is not capture-bucket, normal-bucket or both; using capture-bucket.");
            catchWith = CatchWith.CAPTURE_BUCKET;
        }
        boolean giveBack = c.getBoolean("give-bucket-back", true);
        Set<String> blacklist = new LinkedHashSet<>();
        for (String raw : c.getStringList("blacklist")) {
            String key = raw.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
            if (isMob.test(key)) blacklist.add(key);
            else problems.add("blacklist", "'" + raw + "' is not a mob; it is left out.");
        }
        Duration cooldown = Durations.read(c, "cooldown", Duration.ofSeconds(1), problems);
        if (cooldown.isNegative()) cooldown = Duration.ZERO;
        Set<String> worlds = new LinkedHashSet<>(c.getStringList("disabled-worlds"));
        RecipeSpec recipe = catchWith.capture() && c.getBoolean("capture-bucket.recipe.enabled", true)
                ? recipe(c.getConfigurationSection("capture-bucket.recipe"), problems) : null;

        Map<String, SoundSpec> sounds = new LinkedHashMap<>();
        ConfigurationSection soundSection = c.getConfigurationSection("sounds");
        for (Map.Entry<String, String> sound : SOUNDS.entrySet()) {
            SoundSpec spec = SoundSpec.read(soundSection, sound.getKey(), SoundSpec.of(sound.getValue()), problems);
            sounds.put(sound.getKey(), spec == null ? SoundSpec.SILENT : spec);
        }
        return new Settings(catchWith, giveBack, c.getBoolean("dispensers-release", true), Set.copyOf(blacklist), cooldown, Set.copyOf(worlds), recipe,
                Map.copyOf(sounds), c.getBoolean("copy-protection", true), c.getBoolean("pets-owner-only", true),
                c.getBoolean("respect-claims", true),
                new ResourcePack(c.getBoolean("resource-pack.send", false), c.getString("resource-pack.url", PACK_URL).trim()));
    }

    /** Returns null and reports a problem if the recipe is invalid; the Capture Bucket can still be given. */
    static RecipeSpec recipe(ConfigurationSection section, Problems.InFile problems) {
        String where = "capture-bucket.recipe";
        if (section == null) {
            problems.add(where, "is missing; there is no recipe.");
            return null;
        }
        List<String> shape = section.getStringList("shape");
        if (shape.isEmpty() || shape.size() > 3 || shape.stream().anyMatch(row -> row.isEmpty() || row.length() > 3)) {
            problems.add(where + ".shape", "must be 1 to 3 rows of 1 to 3 letters; there is no recipe.");
            return null;
        }
        Map<Character, Material> ingredients = new LinkedHashMap<>();
        ConfigurationSection given = section.getConfigurationSection("ingredients");
        for (String row : shape) {
            for (char letter : row.toCharArray()) {
                if (letter == ' ' || ingredients.containsKey(letter)) continue;
                String name = given == null ? null : given.getString(String.valueOf(letter));
                Material material = name == null ? null : Material.matchMaterial(name);
                if (material == null || material == Material.AIR) {
                    problems.add(where + ".ingredients." + letter, (name == null ? "is missing" : "'" + name + "' is not an item")
                            + "; there is no recipe.");
                    return null;
                }
                ingredients.put(letter, material);
            }
        }
        if (ingredients.isEmpty()) {
            problems.add(where + ".shape", "has no ingredients; there is no recipe.");
            return null;
        }
        return new RecipeSpec(List.copyOf(shape), Map.copyOf(ingredients));
    }
}
