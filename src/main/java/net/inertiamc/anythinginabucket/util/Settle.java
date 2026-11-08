package net.inertiamc.anythinginabucket.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Tells a held right click apart from a new one after a catch or release. A held click repeats every 4 ticks (200 ms),
 * so without this a repeat would release the mob onto the block behind it right after a catch, or catch it again right
 * after a release. Clicks that keep coming at the repeat's pace are ignored; the first click after a gap works at once.
 */
public final class Settle {

    /** Longer than the 200 ms repeat plus a tick of lag, shorter than letting go and clicking again. */
    public static final long GAP = 300;

    private final LongSupplier clock;
    /** When each player's held click was last seen, tracked only after a catch or release. */
    private final Map<UUID, Long> held = new ConcurrentHashMap<>();

    public Settle() {
        this(System::currentTimeMillis);
    }

    public Settle(LongSupplier clock) {
        this.clock = clock;
    }

    /** The player caught or released something with this click. */
    public void acted(UUID player) {
        held.put(player, clock.getAsLong());
    }

    /** Called for every right click. Returns true while the same held click is still repeating. */
    public boolean repeat(UUID player) {
        Long last = held.get(player);
        if (last == null) return false;
        long now = clock.getAsLong();
        if (now - last < GAP) {
            held.put(player, now);
            return true;
        }
        held.remove(player, last);
        return false;
    }

    public void clear(UUID player) {
        held.remove(player);
    }

    public void clearAll() {
        held.clear();
    }
}
