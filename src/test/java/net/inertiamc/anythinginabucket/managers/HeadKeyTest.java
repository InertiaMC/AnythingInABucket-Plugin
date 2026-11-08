package net.inertiamc.anythinginabucket.managers;

import org.bukkit.DyeColor;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Llama;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.TropicalFish;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bucket resource pack names looks the way HeadKey does: every look the plugin can name is in the pack. */
class HeadKeyTest {

    private static Set<String> heads;

    @BeforeAll
    static void readThePacksHeads() throws IOException {
        heads = PackLooks.read();
    }

    private static String key(String type, Map<String, String> values) {
        return HeadKey.of(type, values::get, null);
    }

    private static String lower(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    private static void inPack(List<String> keys) {
        List<String> missing = keys.stream().filter(k -> !heads.contains(k)).toList();
        assertEquals(List.of(), missing, "looks the plugin names that the pack has no head for");
    }

    @Test
    void aMobWithOneLookIsItsType() {
        assertEquals("creeper", key("creeper", Map.of()));
        assertTrue(heads.contains("creeper"));
    }

    @Test
    void villagersAreBiomeThenJob() {
        assertEquals("villager/desert/librarian", key("villager", Map.of("getVillagerType", "desert", "getProfession", "librarian")));
        assertEquals("villager/taiga", key("villager", Map.of("getVillagerType", "taiga", "getProfession", "none")));
        assertEquals("zombie_villager/swamp/cleric", key("zombie_villager", Map.of("getVillagerType", "swamp", "getProfession", "cleric")));
        List<String> all = new ArrayList<>();
        for (String biome : List.of("desert", "jungle", "plains", "savanna", "snow", "swamp", "taiga")) {
            for (String job : List.of("none", "armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher",
                    "leatherworker", "librarian", "mason", "nitwit", "shepherd", "toolsmith", "weaponsmith")) {
                all.add(key("villager", Map.of("getVillagerType", biome, "getProfession", job)));
                all.add(key("zombie_villager", Map.of("getVillagerType", biome, "getProfession", job)));
            }
        }
        inPack(all);
    }

    @Test
    void everyHorseCoatAndMarking() {
        assertEquals("horse/darkbrown/white_field", key("horse", Map.of("getColor", "dark_brown", "getStyle", "whitefield")));
        assertEquals("horse/white", key("horse", Map.of("getColor", "white", "getStyle", "none")));
        List<String> all = new ArrayList<>();
        for (Horse.Color coat : Horse.Color.values())
            for (Horse.Style marks : Horse.Style.values())
                all.add(key("horse", Map.of("getColor", lower(coat), "getStyle", lower(marks))));
        inPack(all);
    }

    @Test
    void theDefaultLookIsThePlainType() {
        assertEquals("sheep", key("sheep", Map.of("getColor", "white")));
        assertEquals("llama", key("llama", Map.of("getColor", "creamy")));
        assertEquals("rabbit", key("rabbit", Map.of("getRabbitType", "brown")));
        assertEquals("axolotl", key("axolotl", Map.of("getVariant", "lucy")));
        assertEquals("shulker", key("shulker", Map.of()));
    }

    @Test
    void namesTheGameUsesForItsTextures() {
        assertEquals("rabbit/salt", key("rabbit", Map.of("getRabbitType", "salt_and_pepper")));
        assertEquals("rabbit/caerbannog", key("rabbit", Map.of("getRabbitType", "the_killer_bunny")));
        assertEquals("rabbit/toast", HeadKey.of("rabbit", Map.of("getRabbitType", "brown")::get, "Toast"));
        assertEquals("parrot/yellow_blue", key("parrot", Map.of("getVariant", "cyan")));
        assertEquals("parrot/grey", key("parrot", Map.of("getVariant", "gray")));
        assertEquals("panda", key("panda", Map.of("panda", "normal")));
        assertEquals("copper_golem/oxidized", key("copper_golem", Map.of("copper", "oxidized")));
    }

    @Test
    void everyVariantOfEveryEnumTheApiHas() {
        List<String> all = new ArrayList<>();
        for (DyeColor c : DyeColor.values()) {
            all.add(key("sheep", Map.of("getColor", lower(c))));
            all.add(key("shulker", Map.of("getColor", lower(c))));
        }
        for (Rabbit.Type t : Rabbit.Type.values()) all.add(key("rabbit", Map.of("getRabbitType", lower(t))));
        for (Fox.Type t : Fox.Type.values()) all.add(key("fox", Map.of("getFoxType", lower(t))));
        for (Parrot.Variant v : Parrot.Variant.values()) all.add(key("parrot", Map.of("getVariant", lower(v))));
        for (Axolotl.Variant v : Axolotl.Variant.values()) all.add(key("axolotl", Map.of("getVariant", lower(v))));
        for (MushroomCow.Variant v : MushroomCow.Variant.values()) all.add(key("mooshroom", Map.of("getVariant", lower(v))));
        for (Llama.Color c : Llama.Color.values()) {
            all.add(key("llama", Map.of("getColor", lower(c))));
            all.add(key("trader_llama", Map.of("getColor", lower(c))));
        }
        for (Panda.Gene g : Panda.Gene.values()) all.add(key("panda", Map.of("panda", lower(g))));
        inPack(all);
    }

    @Test
    void everyTropicalFishPatternAndColourPair() {
        assertEquals("tropical_fish/kob/orange/white",
                key("tropical_fish", Map.of("getPattern", "kob", "getBodyColor", "orange", "getPatternColor", "white")));
        List<String> all = new ArrayList<>();
        for (TropicalFish.Pattern p : TropicalFish.Pattern.values())
            for (DyeColor body : DyeColor.values())
                for (DyeColor colour : DyeColor.values())
                    all.add(key("tropical_fish", Map.of("getPattern", lower(p), "getBodyColor", lower(body), "getPatternColor", lower(colour))));
        assertEquals(3072, all.size());
        inPack(all);
    }

    @Test
    void anUnreadableVariantFallsBackToThePlainType() {
        assertEquals("villager", key("villager", Map.of()));
        assertEquals("tropical_fish", key("tropical_fish", Map.of("getPattern", "kob")));
    }
}
