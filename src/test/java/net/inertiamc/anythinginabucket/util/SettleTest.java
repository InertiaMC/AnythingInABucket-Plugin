package net.inertiamc.anythinginabucket.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettleTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final Settle settle = new Settle(now::get);
    private final UUID steve = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();

    @Test
    void aHeldClicksRepeatsAreIgnoredForAsLongAsItIsHeld() {
        assertFalse(settle.repeat(steve), "the click that catches");
        settle.acted(steve);
        for (int i = 0; i < 10; i++) {
            now.addAndGet(200);
            assertTrue(settle.repeat(steve), "repeat " + i + " of the held click");
        }
        now.addAndGet(Settle.GAP);
        assertFalse(settle.repeat(steve), "let go and clicked again");
        assertFalse(settle.repeat(steve), "and every click after it works too");
    }

    @Test
    void aQuickSecondClickWorks() {
        settle.acted(steve);
        now.addAndGet(350);
        assertFalse(settle.repeat(steve));
    }

    @Test
    void theSameTicksOtherEventIsTheSameClick() {
        settle.acted(steve);
        assertTrue(settle.repeat(steve), "interact-at and interact in one tick");
    }

    @Test
    void itIsPerPlayerAndClearedOnQuit() {
        settle.acted(steve);
        assertFalse(settle.repeat(alex));
        settle.clear(steve);
        assertFalse(settle.repeat(steve));
    }
}
