package net.inertiamc.anythinginabucket.managers;

import org.bukkit.Keyed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The name the bucket resource pack uses for a mob's look: {@code villager/desert/librarian}, {@code sheep/red},
 * {@code tropical_fish/kob/orange/white}, or the plain type ({@code creeper}) for a mob with one look. A filled bucket
 * carries it as its first custom model data string and the plain type as its second, so a look the pack does not know
 * falls back to the mob's default art.
 *
 * <p>{@link #of} is pure so it can be tested without a server. {@link #read} feeds it from the live mob by method name,
 * since the variant types changed from enums to interfaces within the supported versions. The resource pack uses the
 * same names, so keep the two in step.
 */
public final class HeadKey {

    private static final Map<String, String> HORSE_COLOURS = Map.of("dark_brown", "darkbrown");
    private static final Map<String, String> HORSE_MARKINGS = Map.of("whitefield", "white_field");
    private static final Map<String, String> RABBITS = Map.of(
            "black_and_white", "white_splotched", "salt_and_pepper", "salt", "the_killer_bunny", "caerbannog");
    private static final Map<String, String> PARROTS = Map.of("red", "red_blue", "cyan", "yellow_blue", "gray", "grey");

    private HeadKey() {}

    /**
     * @param type     the entity type key ({@code villager})
     * @param value    a getter's value as a lower-case key ({@code getColor} to {@code red}), or null when there is none
     * @param name     the mob's custom name, plain, or null
     */
    public static String of(String type, Function<String, String> value, String name) {
        String variant = switch (type) {
            case "villager", "zombie_villager" -> {
                String biome = value.apply("getVillagerType");
                String job = value.apply("getProfession");
                if (biome == null) yield null;
                yield job == null || job.equals("none") ? biome : biome + "/" + job;
            }
            case "horse" -> {
                String coat = value.apply("getColor");
                String marks = value.apply("getStyle");
                if (coat == null) yield null;
                coat = HORSE_COLOURS.getOrDefault(coat, coat);
                marks = marks == null ? "none" : HORSE_MARKINGS.getOrDefault(marks, marks);
                yield marks.equals("none") ? coat : coat + "/" + marks;
            }
            case "sheep", "shulker" -> notDefault(value.apply("getColor"), type.equals("sheep") ? "white" : null);
            case "llama", "trader_llama" -> notDefault(value.apply("getColor"), "creamy");
            case "cat" -> value.apply("getCatType");
            case "wolf", "frog", "pig", "cow", "chicken", "zombie_nautilus" -> value.apply("getVariant");
            case "rabbit" -> {
                if ("Toast".equals(name)) yield "toast";
                String kind = value.apply("getRabbitType");
                yield notDefault(kind == null ? null : RABBITS.getOrDefault(kind, kind), "brown");
            }
            case "fox" -> notDefault(value.apply("getFoxType"), "red");
            case "parrot" -> {
                String colour = value.apply("getVariant");
                yield notDefault(colour == null ? null : PARROTS.getOrDefault(colour, colour), "red_blue");
            }
            case "panda" -> notDefault(value.apply("panda"), "normal");
            case "axolotl" -> notDefault(value.apply("getVariant"), "lucy");
            case "mooshroom" -> notDefault(value.apply("getVariant"), "red");
            case "copper_golem" -> notDefault(value.apply("copper"), "unaffected");
            case "tropical_fish" -> {
                String pattern = value.apply("getPattern");
                String body = value.apply("getBodyColor");
                String colour = value.apply("getPatternColor");
                yield pattern == null || body == null || colour == null ? null : pattern + "/" + body + "/" + colour;
            }
            default -> null;
        };
        return variant == null ? type : type + "/" + variant;
    }

    private static String notDefault(String value, String fallback) {
        return value == null || value.equals(fallback) ? null : value;
    }

    /** Call on the mob's own thread. */
    public static String read(LivingEntity mob) {
        String name = mob.getCustomName();
        return of(mob.getType().getKey().getKey(), getter -> switch (getter) {
            case "panda" -> panda(mob);
            case "copper" -> copper(mob);
            default -> raw(mob, getter);
        }, name);
    }

    /** A getter's value as a lower-case key: a Keyed's key, or an enum's name. */
    static String raw(Entity mob, String getter) {
        Object value = call(mob, getter);
        if (value instanceof Keyed keyed) return keyed.getKey().getKey();
        if (value instanceof Enum<?> constant) return constant.name().toLowerCase(Locale.ROOT);
        return null;
    }

    /** Looks the getter up on the API interface (as MobFacts does) rather than the server's implementation class. */
    static Object call(Object target, String getter) {
        try {
            Class<?> api = target instanceof Entity entity && entity.getType().getEntityClass() != null
                    ? entity.getType().getEntityClass() : target.getClass();
            Method method = api.getMethod(getter);
            return method.getParameterCount() == 0 ? method.invoke(target) : null;
        } catch (ReflectiveOperationException | RuntimeException none) {
            return null;
        }
    }

    /** The gene a panda shows: its main gene, unless that is recessive and the hidden gene differs (then normal). */
    static String panda(Entity mob) {
        Object main = call(mob, "getMainGene");
        Object hidden = call(mob, "getHiddenGene");
        if (!(main instanceof Enum<?> gene)) return null;
        Object recessive = call(main, "isRecessive");
        if (Boolean.TRUE.equals(recessive) && main != hidden) return "normal";
        return gene.name().toLowerCase(Locale.ROOT);
    }

    /** A copper golem's weathering, from whichever getter this server names it by. */
    static String copper(Entity mob) {
        Class<?> api = mob.getType().getEntityClass();
        if (api == null) return null;
        for (Method method : api.getMethods()) {
            if (method.getParameterCount() != 0 || !method.getReturnType().isEnum()) continue;
            for (Object constant : method.getReturnType().getEnumConstants()) {
                if (!((Enum<?>) constant).name().equals("OXIDIZED")) continue;
                Object value = call(mob, method.getName());
                return value instanceof Enum<?> state ? state.name().toLowerCase(Locale.ROOT) : null;
            }
        }
        return null;
    }
}
