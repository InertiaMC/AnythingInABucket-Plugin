package net.inertiamc.anythinginabucket.managers;

import net.inertiamc.core.ChatUtil;
import net.inertiamc.core.item.ItemTokens;
import net.inertiamc.anythinginabucket.AnythingInABucket;
import net.inertiamc.anythinginabucket.config.MessageManager;
import net.inertiamc.anythinginabucket.util.Words;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The two items: the empty Capture Bucket ({@code anythinginabucket:capture-bucket}) and a filled bucket
 * ({@code anythinginabucket:filled}, the id its mob is kept under, and {@code anythinginabucket:mob}, the mob's type). Both
 * carry an item code, so a copy catches or releases nothing. The keys are stored on items, so renaming one breaks every
 * existing item.
 */
public final class BucketItems {

    private final AnythingInABucket plugin;
    private final NamespacedKey bucketKey;
    private final NamespacedKey filledKey;
    private final NamespacedKey mobKey;
    private final ItemTokens tokens;

    public BucketItems(AnythingInABucket plugin, ItemTokens tokens) {
        this.plugin = plugin;
        this.bucketKey = new NamespacedKey(plugin, "capture-bucket");
        this.filledKey = new NamespacedKey(plugin, "filled");
        this.mobKey = new NamespacedKey(plugin, "mob");
        this.tokens = tokens;
    }

    public ItemTokens tokens() {
        return tokens;
    }

    private MessageManager messages() {
        return plugin.getMessageManager();
    }

    /** An unstamped Capture Bucket: the crafting recipe's result, stamped when it is taken. */
    public ItemStack bucket(int amount) {
        ItemStack item = new ItemStack(Material.BUCKET, Math.max(1, Math.min(16, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(messages().getRawMessage("bucket.name"));
        meta.setLore(messages().getStringList("bucket.lore"));
        meta.getPersistentDataContainer().set(bucketKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** A new stamped stack of Capture Buckets. */
    public ItemStack stampedBucket(int amount, UUID player) {
        return tokens.stamp(bucket(amount), player);
    }

    /** Gives stamped Capture Buckets in stacks of 16. Call on the player's thread. */
    public void giveBuckets(org.bukkit.entity.Player player, int amount) {
        for (int left = amount; left > 0; left -= 16) {
            tokens.give(player, stampedBucket(Math.min(16, left), player.getUniqueId()));
        }
    }

    public boolean isBucket(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta() != null
                && item.getItemMeta().getPersistentDataContainer().has(bucketKey, PersistentDataType.BYTE);
    }

    /**
     * A new stamped filled bucket. {@code mob} is the type's key, {@code head} its look for the bucket resource pack
     * ({@link HeadKey}), {@code name} the mob's own name or null, {@code facts} its lore lines.
     */
    public ItemStack filled(UUID id, String mob, String head, String name, String catcher, List<MobFacts.Fact> facts, UUID player) {
        Material egg = Material.matchMaterial(mob.toUpperCase(Locale.ROOT) + "_SPAWN_EGG");
        ItemStack item = new ItemStack(egg == null ? Material.BUCKET : egg);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String kind = Words.pretty(mob);
        String title = name == null || ChatUtil.strip(name).isBlank()
                ? messages().getRawMessage("filled.name", "mob", kind)
                : messages().getRawMessage("filled.name-named", "mob", kind, "name", ChatUtil.strip(name));
        meta.setDisplayName(title);
        List<String> lore = new ArrayList<>();
        for (String line : messages().getStringList("filled.lore", "player", catcher, "mob", kind)) {
            if (!line.trim().equals("{facts}")) {
                lore.add(line);
                continue;
            }
            for (MobFacts.Fact fact : facts) {
                lore.add(messages().getRawMessage("filled.fact",
                        "label", messages().getRawMessage("filled.labels." + fact.label()), "value", fact.value()));
            }
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (egg == null) meta.setEnchantmentGlintOverride(true);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(filledKey, PersistentDataType.STRING, id.toString());
        data.set(mobKey, PersistentDataType.STRING, mob);
        headMarks(meta, head.equals(mob) ? List.of(mob) : List.of(head, mob), BucketNumbers.of(head, mob));
        item.setItemMeta(meta);
        return tokens.stamp(item, player);
    }

    /**
     * Sets what the bucket resource pack reads. From 1.21.4: the custom model data strings (exact look, then plain type)
     * and the look's number as the first float, which ViaBackwards passes to older clients as their single number.
     * Before 1.21.4: only that number. The 1.21.4 component is newer than the 1.21 API this compiles against, so it is
     * set by reflection.
     */
    static void headMarks(ItemMeta meta, List<String> strings, Integer number) {
        try {
            Object component = ItemMeta.class.getMethod("getCustomModelDataComponent").invoke(meta);
            component.getClass().getMethod("setStrings", List.class).invoke(component, strings);
            if (number != null) component.getClass().getMethod("setFloats", List.class).invoke(component, List.of(number.floatValue()));
            for (java.lang.reflect.Method set : ItemMeta.class.getMethods()) {
                if (set.getName().equals("setCustomModelDataComponent") && set.getParameterCount() == 1) {
                    set.invoke(meta, component);
                    return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException olderServer) {
            if (number != null) meta.setCustomModelData(number);
        }
    }

    /** The filled bucket's id, or null when the item is not one. */
    public UUID filledOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        String id = meta == null ? null : meta.getPersistentDataContainer().get(filledKey, PersistentDataType.STRING);
        if (id == null) return null;
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException broken) {
            return null;
        }
    }

    /** The filled bucket's mob type key, or null. */
    public String mobOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
    }

    /** One of this plugin's items: a Capture Bucket or a filled bucket. */
    public boolean ours(ItemStack item) {
        return isBucket(item) || filledOf(item) != null;
    }
}
