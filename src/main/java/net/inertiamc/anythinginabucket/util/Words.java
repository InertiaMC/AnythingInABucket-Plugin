package net.inertiamc.anythinginabucket.util;

import java.util.Locale;

/** Readable game names and horse stats for lore. No Bukkit dependency. */
public final class Words {

    private Words() {}

    /**
     * A game key in words, first letter a capital: {@code ender_dragon} is "Ender dragon", {@code WHITE_DOTS} is
     * "White dots", {@code minecraft:all_black} is "All black".
     */
    public static String pretty(String key) {
        if (key == null || key.isBlank()) return "";
        String plain = key.substring(key.indexOf(':') + 1).replace('_', ' ').trim().toLowerCase(Locale.ROOT);
        return plain.isEmpty() ? "" : Character.toUpperCase(plain.charAt(0)) + plain.substring(1);
    }

    /** One decimal, without a trailing {@code .0}: 18.0 is "18", 7.25 is "7.3". */
    public static String number(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        if (rounded == Math.rint(rounded)) return String.valueOf((long) rounded);
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    /** A horse's movement speed attribute as blocks a second. */
    public static double blocksPerSecond(double speedAttribute) {
        return speedAttribute * 42.16;
    }

    /** Approximate jump height in blocks for a horse's jump strength (the Minecraft Wiki's fitted formula). */
    public static double jumpHeight(double strength) {
        return -0.1817584952 * strength * strength * strength + 3.689713992 * strength * strength
                + 2.128599134 * strength - 0.343930367;
    }
}
