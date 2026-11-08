package net.inertiamc.anythinginabucket.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordsTest {

    @Test
    void keysReadAsWords() {
        assertEquals("Ender dragon", Words.pretty("ender_dragon"));
        assertEquals("White dots", Words.pretty("WHITE_DOTS"));
        assertEquals("All black", Words.pretty("minecraft:all_black"));
        assertEquals("Horse", Words.pretty("horse"));
        assertEquals("", Words.pretty(null));
        assertEquals("", Words.pretty("  "));
    }

    @Test
    void numbersKeepOneDecimalAtMost() {
        assertEquals("18", Words.number(18.0));
        assertEquals("7.3", Words.number(7.25));
        assertEquals("0.5", Words.number(0.5));
        assertEquals("30", Words.number(29.96));
    }

    @Test
    void horseNumbersMatchTheGame() {
        // The slowest and fastest natural horses: 0.1125 and 0.3375 speed.
        assertEquals("4.7", Words.number(Words.blocksPerSecond(0.1125)));
        assertEquals("14.2", Words.number(Words.blocksPerSecond(0.3375)));
        // Jump strength 0.4 to 1.0 is about 1.1 to 5.3 blocks.
        double low = Words.jumpHeight(0.4), high = Words.jumpHeight(1.0);
        assertTrue(low > 1.0 && low < 1.2, String.valueOf(low));
        assertTrue(high > 5.2 && high < 5.4, String.valueOf(high));
    }
}
