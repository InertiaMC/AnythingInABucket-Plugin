package net.inertiamc.anythinginabucket.config;

import net.inertiamc.core.config.ConfigStandard;
import net.inertiamc.core.config.Problems;
import net.inertiamc.core.storage.DatabaseSettings;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The files the plugin ships must parse with no problem and follow the config standard. */
class ShippedConfigTest {

    private static final Predicate<String> IS_MOB = Set.of("wither", "ender_dragon", "warden", "elder_guardian", "horse")::contains;
    /** U+2014, written as a number so this file has none. */
    private static final String EM_DASH = String.valueOf((char) 0x2014);

    private Problems problems;

    @BeforeEach
    void captureProblems() {
        problems = new Problems();
    }

    private List<String> warnings() {
        return problems.list().stream().map(Problems.Problem::toString).toList();
    }

    static YamlConfiguration resource(String path) {
        return YamlConfiguration.loadConfiguration(new File("src/main/resources/" + path));
    }

    @Test
    void shippedFilesFollowTheConfigStandard() {
        assertEquals(List.of(), ConfigStandard.check(Path.of("src/main/resources")));
    }

    @Test
    void theShippedSettingsLoadWithNoProblems() {
        ConfigManager.Settings s = ConfigManager.settings(resource("config.yml"), IS_MOB, problems.in("config.yml"));
        assertEquals(List.of(), warnings());
        assertEquals(ConfigManager.CatchWith.CAPTURE_BUCKET, s.catchWith());
        assertEquals(Set.of("wither", "ender_dragon", "warden", "elder_guardian"), s.blacklist());
        assertEquals(Duration.ofSeconds(1), s.cooldown());
        assertEquals(Set.of(), s.disabledWorlds());
        assertTrue(s.giveBack());
        assertTrue(s.dispensersRelease(), "dispensers let mobs out unless an owner turns it off");
        assertNotNull(s.recipe());
        assertEquals(List.of(" e ", "ebe", " l "), s.recipe().shape());
        assertEquals(Map.of('e', Material.ENDER_PEARL, 'b', Material.BUCKET, 'l', Material.LEAD), s.recipe().ingredients());
        assertEquals(ConfigManager.SOUNDS.keySet(), s.sounds().keySet());
        assertTrue(s.copyProtection() && s.petsOwnerOnly() && s.respectClaims(), "every guard ships on");
        assertFalse(s.resourcePack().send(), "the bucket pack is offered only when an owner turns it on");
        assertEquals(ConfigManager.PACK_URL, s.resourcePack().url());
    }

    /** A config without the resource-pack section loads with the pack off and the Modrinth default URL. */
    @Test
    void anOldConfigGetsTheHeadPackOff() {
        ConfigManager.Settings s = ConfigManager.settings(new YamlConfiguration(), IS_MOB, problems.in("config.yml"));
        assertFalse(s.resourcePack().send());
        assertEquals("", s.resourcePack().url());
    }

    /** The copy, pet and claim guards can be turned off, and a config without them keeps them on. */
    @Test
    void theGuardsCanBeSwitchedOffAndDefaultToOn() throws Exception {
        YamlConfiguration off = new YamlConfiguration();
        off.loadFromString("copy-protection: false\npets-owner-only: false\nrespect-claims: false");
        ConfigManager.Settings s = ConfigManager.settings(off, IS_MOB, problems.in("config.yml"));
        assertFalse(s.copyProtection());
        assertFalse(s.petsOwnerOnly());
        assertFalse(s.respectClaims());
        ConfigManager.Settings old = ConfigManager.settings(new YamlConfiguration(), IS_MOB, problems.in("config.yml"));
        assertTrue(old.copyProtection() && old.petsOwnerOnly() && old.respectClaims());
    }

    @Test
    void wrongEntriesAreLeftOutAndReported() throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString("""
                catch-with: spoon
                blacklist: [ wither, dragonn ]
                cooldown: 5
                capture-bucket: { recipe: { enabled: true, shape: [ "ab" ], ingredients: { a: bucket, b: nope } } }
                """);
        ConfigManager.Settings s = ConfigManager.settings(c, IS_MOB, problems.in("config.yml"));
        assertEquals(ConfigManager.CatchWith.CAPTURE_BUCKET, s.catchWith());
        assertEquals(Set.of("wither"), s.blacklist());
        assertNull(s.recipe());
        assertEquals(4, problems.size(), warnings().toString());
    }

    @Test
    void normalBucketsOnlySwitchTheCaptureBucketAndItsRecipeOff() throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString("""
                catch-with: normal-bucket
                capture-bucket: { recipe: { enabled: true, shape: [ 'b' ], ingredients: { b: bucket } } }
                """);
        ConfigManager.Settings s = ConfigManager.settings(c, IS_MOB, problems.in("config.yml"));
        assertFalse(s.catchWith().capture());
        assertTrue(s.catchWith().normal());
        assertNull(s.recipe(), "no recipe for a bucket that does not catch");
        assertEquals(List.of(), warnings());
    }

    @Test
    void bothBucketsCatch() throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString("catch-with: both");
        ConfigManager.CatchWith both = ConfigManager.settings(c, IS_MOB, problems.in("config.yml")).catchWith();
        assertTrue(both.capture() && both.normal());
    }

    @Test
    void aRecipeCanBeSwitchedOff() throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString("capture-bucket: { recipe: { enabled: false } }");
        assertNull(ConfigManager.settings(c, IS_MOB, problems.in("config.yml")).recipe());
        assertEquals(List.of(), warnings());
    }

    @Test
    void aRecipeShapeMustFitTheGrid() throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString("shape: [ 'BBBB' ]\ningredients: { B: bucket }");
        assertNull(ConfigManager.recipe(c, problems.in("config.yml")));
        assertEquals(1, problems.size());
    }

    @Test
    void everyEntityTypeKeyIsKnownAtTheFloor() {
        assertTrue(ConfigManager.isMob("horse"));
        assertTrue(ConfigManager.isMob("ender_dragon"));
        assertFalse(ConfigManager.isMob("boat"));
        assertFalse(ConfigManager.isMob("dragonn"));
    }

    @Test
    void theShippedDatabaseIsSqlite() {
        DatabaseSettings settings = DatabaseSettings.fromFile(resource("database.yml"), "anythinginabucket", problems.in("database.yml"));
        assertEquals(List.of(), warnings());
        assertFalse(settings.mysql());
        assertEquals("anythinginabucket_", settings.tablePrefix());
    }

    @Test
    void noShippedTextHasAnEmDash() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (Files.readString(file, StandardCharsets.UTF_8).contains(EM_DASH)) found.add(file.toString());
            }
        }
        assertEquals(List.of(), found);
    }

    /** Display colours belong in the message text, not in config settings. */
    @Test
    void noColourIsASetting() {
        for (String key : resource("config.yml").getKeys(true)) assertFalse(key.endsWith("color"), key);
    }

}
