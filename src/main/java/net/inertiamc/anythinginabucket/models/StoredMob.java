package net.inertiamc.anythinginabucket.models;

import java.util.UUID;

/**
 * A caught mob as the database keeps it.
 *
 * @param id          the filled bucket's id, under {@code anythinginabucket:filled} on the item; never changes, unlike its code
 * @param type        the entity type's key ({@code horse})
 * @param format      how {@code data} is written: {@link #PAPER} or {@link #SNAPSHOT}
 * @param data        the mob itself
 * @param dataVersion the game's data version when it was caught
 * @param bucket      what releasing gives back: {@link #CAPTURE_BUCKET} or {@link #PLAIN_BUCKET}
 * @param caughtBy    who caught it
 * @param caughtAt    when, in epoch milliseconds
 */
public record StoredMob(UUID id, String type, String format, String data, int dataVersion, String bucket,
                        UUID caughtBy, long caughtAt) {

    /** Paper's own entity bytes in Base64, upgraded by the game when a newer version reads them. */
    public static final String PAPER = "paper";
    /** Bukkit's entity snapshot text, read as it is. */
    public static final String SNAPSHOT = "snapshot";

    public static final String CAPTURE_BUCKET = "capture";
    public static final String PLAIN_BUCKET = "plain";
}
