package net.inertiamc.anythinginabucket.managers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Before 1.21.4 the pack picks the art by number: every look has one, and no two looks share one. */
class BucketNumbersTest {

    @Test
    void everyLookInThePackHasItsOwnNumber() throws IOException {
        Map<String, Integer> numbers = BucketNumbers.read(Files.newBufferedReader(Path.of("src/main/resources/numbers.txt")));
        Set<String> heads = PackLooks.read();
        assertEquals(List.of(), heads.stream().filter(h -> !numbers.containsKey(h)).toList(), "looks with no number");
        assertEquals(numbers.size(), new HashSet<>(numbers.values()).size(), "a number used twice");
        assertTrue(numbers.values().stream().allMatch(n -> n > 0 && n < 1 << 24), "a number a float cannot hold exactly");
    }

    @Test
    void theFileIsReadAsNumberThenLook() throws IOException {
        Map<String, Integer> numbers = BucketNumbers.read(new StringReader("# a comment\n4100001 sheep\n4100002 sheep/red\n"));
        assertEquals(Map.of("sheep", 4100001, "sheep/red", 4100002), numbers);
    }

    @Test
    void aLookNewerThanThePackTakesItsTypesNumber() {
        assertTrue(BucketNumbers.of("sheep", "sheep") != null, "the bundled numbers.txt was not found");
        assertEquals(BucketNumbers.of("sheep", "sheep"), BucketNumbers.of("sheep/not-a-colour", "sheep"));
        assertNull(BucketNumbers.of("not-a-mob", "not-a-mob"));
    }
}
