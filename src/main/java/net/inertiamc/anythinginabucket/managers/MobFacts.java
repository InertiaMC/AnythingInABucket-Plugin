package net.inertiamc.anythinginabucket.managers;

import net.inertiamc.core.CompatUtil;
import net.inertiamc.anythinginabucket.util.Words;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Steerable;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TropicalFish;
import org.bukkit.inventory.AbstractHorseInventory;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.LlamaInventory;
import org.bukkit.inventory.Merchant;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a filled bucket's lore says about its mob, read once when it is caught. Each fact is a label key and a value; the
 * words come from messages.yml ({@code filled.labels.<label>}, {@code filled.words.<word>}).
 *
 * <p>Variants are read by method name on the mob's API interface: {@code Cat.Type}, {@code Wolf.Variant} and the like
 * changed from enums to interfaces inside the supported range, so a direct call could compile to the wrong bytecode.
 */
public final class MobFacts {

    /** One lore line: a label from {@code filled.labels}, and a value already in words. */
    public record Fact(String label, String value) {}

    /** The words a fact needs from messages.yml, by key under {@code filled.words}. */
    @FunctionalInterface
    public interface Dictionary {
        String word(String key, String... placeholders);
    }

    /** {@code label -> getter}, tried on every mob that has the getter. */
    private static final String[][] VARIANTS = {
            {"color", "getColor"},
            {"markings", "getStyle"},
            {"variant", "getCatType"},
            {"variant", "getRabbitType"},
            {"variant", "getFoxType"},
            {"variant", "getVariant"},
            {"variant", "getVillagerType"},
    };

    /** A villager's or a zombie villager's job. */
    private static final String[] PROFESSIONS = {"getProfession", "getVillagerProfession"};

    /** Where Bukkit's name is not the one the lore shows, by {@code mob:CONSTANT}. */
    private static final Map<String, String> SHOWN_NAMES = Map.of(
            "rabbit:THE_KILLER_BUNNY", "killer_bunny",
            "horse:WHITEFIELD", "white_field");

    /** The mobs that come in sizes. */
    private static final Set<String> SIZED = Set.of("slime", "magma_cube", "sulfur_cube");

    /** The mobs that wear a saddle: only they get the saddle line. */
    private static final Set<String> SADDLED = Set.of("horse", "donkey", "mule", "skeleton_horse", "zombie_horse", "camel",
            "camel_husk", "pig", "strider", "nautilus", "zombie_nautilus");

    /** Bukkit calls these ageable, but they are never babies. */
    private static final Set<String> NO_AGE = Set.of("piglin_brute", "slime", "magma_cube");

    /** The saddle slot, from 1.21.5. */
    private static final EquipmentSlot SADDLE_SLOT = slot("SADDLE");

    private static final String[] LEVELS = {"novice", "apprentice", "journeyman", "expert", "master"};

    private MobFacts() {}

    /** Call on the mob's own thread. The mod version writes the same lines in the same order, so keep them in step. */
    public static List<Fact> read(LivingEntity mob, Dictionary words) {
        List<Fact> facts = new ArrayList<>();
        String key = mob.getType().getKey().getKey();
        Attribute maxHealth = CompatUtil.attribute("MAX_HEALTH", "GENERIC_MAX_HEALTH");
        AttributeInstance max = maxHealth == null ? null : mob.getAttribute(maxHealth);
        String health = Words.number(mob.getHealth());
        facts.add(new Fact("health", max == null ? health : health + "/" + Words.number(max.getValue())));
        if (mob instanceof Ageable ageable && !NO_AGE.contains(key)) {
            facts.add(new Fact("age", words.word(ageable.isAdult() ? "adult" : "baby")));
        }
        if (mob instanceof Tameable tameable && tameable.isTamed() && tameable.getOwner() != null) {
            facts.add(new Fact("owner", owner(tameable, words)));
        }

        if (mob instanceof TropicalFish fish) {
            String body = Words.pretty(fish.getBodyColor().name());
            String pattern = Words.pretty(fish.getPatternColor().name()).toLowerCase(Locale.ROOT);
            facts.add(new Fact("color", fish.getBodyColor() == fish.getPatternColor() ? body
                    : words.word("two-colors", "first",body, "second", pattern)));
            facts.add(new Fact("variant", Words.pretty(fish.getPattern().name())));
        }
        boolean horse = mob instanceof AbstractHorse && !(mob instanceof Llama);
        for (String[] variant : VARIANTS) {
            // Any horse's getVariant is its kind (donkey, llama), not a look; an ocelot's cat type is a leftover.
            if (mob instanceof AbstractHorse && variant[1].equals("getVariant")) continue;
            if (key.equals("ocelot") && variant[1].equals("getCatType")) continue;
            String value = variant(mob, key, variant[1]);
            if (value != null && facts.stream().noneMatch(f -> f.label().equals(variant[0]))) {
                facts.add(new Fact(variant[0], value));
            }
        }
        // The same reads HeadKey uses to pick the bucket art.
        if (key.equals("panda")) {
            String gene = HeadKey.panda(mob);
            if (gene != null) facts.add(new Fact("personality", Words.pretty(gene)));
        }
        if (key.equals("copper_golem")) {
            String rust = HeadKey.copper(mob);
            if (rust != null) facts.add(new Fact("rust", rust.equals("unaffected") ? words.word("none") : Words.pretty(rust)));
        }
        if (SIZED.contains(key) && HeadKey.call(mob, "getSize") instanceof Integer size) {
            facts.add(new Fact("size", words.word(size <= 1 ? "small" : size == 2 ? "medium" : "big")));
        }
        for (String getter : PROFESSIONS) {
            String value = variant(mob, key, getter);
            if (value != null && facts.stream().noneMatch(f -> f.label().equals("profession"))) {
                facts.add(new Fact("profession", value));
            }
        }
        if (mob instanceof org.bukkit.entity.Villager villager) {
            int level = Math.max(1, Math.min(LEVELS.length, villager.getVillagerLevel()));
            facts.add(new Fact("level", words.word(LEVELS[level - 1])));
        }
        if (mob instanceof Merchant merchant) facts.add(new Fact("trades", String.valueOf(merchant.getRecipeCount())));
        if (mob instanceof Sheep sheep && sheep.isSheared()) facts.add(new Fact("sheared", words.word("yes-word")));

        if (SADDLED.contains(key)) facts.add(new Fact("saddle", words.word(saddled(mob) ? "yes-word" : "no-word")));
        String armour = armour(mob);
        if (armour != null) facts.add(new Fact("armour", armour));
        if (mob instanceof ChestedHorse chested && chested.isCarryingChest()) {
            int items = chestItems(chested.getInventory());
            facts.add(new Fact("chest", items == 1 ? words.word("item-one") : words.word("items", "count", String.valueOf(items))));
        }
        if (horse) {
            Attribute speed = CompatUtil.attribute("MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED");
            AttributeInstance instance = speed == null ? null : mob.getAttribute(speed);
            if (instance != null) {
                facts.add(new Fact("speed", words.word("speed", "value", Words.number(Words.blocksPerSecond(instance.getValue())))));
            }
            // Camels and camel husks dash instead of jumping.
            if (!key.startsWith("camel")) {
                double jump = Words.jumpHeight(((AbstractHorse) mob).getJumpStrength());
                facts.add(new Fact("jump", words.word("jump", "value", Words.number(jump))));
            }
        }
        return facts;
    }

    private static String owner(Tameable tameable, Dictionary words) {
        AnimalTamer owner = tameable.getOwner();
        String name = owner == null ? null : owner.getName();
        if (name == null && owner instanceof OfflinePlayer offline) name = offline.getName();
        return name == null ? words.word("unknown") : name;
    }

    /** The getter's value in words, or null when the mob has no such getter. */
    private static String variant(Entity mob, String key, String getter) {
        Class<?> api = mob.getType().getEntityClass();
        if (api == null) return null;
        try {
            Method method = api.getMethod(getter);
            if (method.getParameterCount() != 0) return null;
            Object value = method.invoke(mob);
            if (value == null) return null;
            if (value instanceof Keyed keyed) return Words.pretty(keyed.getKey().getKey());
            if (value instanceof Enum<?> constant) {
                return Words.pretty(SHOWN_NAMES.getOrDefault(key + ":" + constant.name(), constant.name()));
            }
            return null;
        } catch (NoSuchMethodException none) {
            return null;
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            return null;
        }
    }

    private static boolean saddled(LivingEntity mob) {
        if (mob instanceof Steerable steerable) return steerable.hasSaddle();
        if (mob instanceof AbstractHorse horse) return !empty(horse.getInventory().getSaddle());
        // A nautilus has neither: its saddle is only in the saddle slot.
        if (SADDLE_SLOT == null) return false;
        try {
            EntityEquipment equipment = mob.getEquipment();
            return equipment != null && !empty(equipment.getItem(SADDLE_SLOT));
        } catch (RuntimeException noSaddleSlot) {
            return false;
        }
    }

    private static EquipmentSlot slot(String name) {
        try {
            return EquipmentSlot.valueOf(name);
        } catch (IllegalArgumentException older) {
            return null;
        }
    }

    /** Horse armour, a llama's carpet or a wolf's armour, in words; null when it wears none. */
    private static String armour(LivingEntity mob) {
        ItemStack worn = null;
        if (mob instanceof AbstractHorse horse) {
            AbstractHorseInventory inventory = horse.getInventory();
            if (inventory instanceof HorseInventory horses) worn = horses.getArmor();
            else if (inventory instanceof LlamaInventory llamas) worn = llamas.getDecor();
        }
        if (empty(worn)) {
            try {
                EntityEquipment equipment = mob.getEquipment();
                if (equipment != null) worn = equipment.getItem(EquipmentSlot.BODY);
            } catch (RuntimeException noBodySlot) {
                worn = null;
            }
        }
        return empty(worn) ? null : Words.pretty(worn.getType().getKey().getKey());
    }

    /** What a donkey, mule or llama carries in its chest: every slot after the saddle and armour ones. */
    private static int chestItems(AbstractHorseInventory inventory) {
        ItemStack[] contents = inventory.getContents();
        int count = 0;
        for (int slot = 2; slot < contents.length; slot++) if (!empty(contents[slot])) count++;
        return count;
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }
}
