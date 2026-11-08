package net.inertiamc.anythinginabucket.commands.sub.admin;

import net.inertiamc.core.SchedulerUtil;
import net.inertiamc.anythinginabucket.AnythingInABucket;
import net.inertiamc.anythinginabucket.commands.sub.AbstractBucketSub;
import net.inertiamc.anythinginabucket.models.StoredMob;
import net.inertiamc.anythinginabucket.util.PermissionNodes;
import net.inertiamc.anythinginabucket.util.Words;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.logging.Level;

/** {@code /bucket admin info}: the filled bucket in the player's main hand, as a status box. */
public final class SubAdminInfo extends AbstractBucketSub {

    public SubAdminInfo(AnythingInABucket plugin) {
        super(plugin);
    }

    @Override public String name() { return "info"; }
    @Override public String permission() { return PermissionNodes.ADMIN_INFO; }
    @Override public String usage() { return "/bucket admin info"; }
    @Override public String description() { return "The filled bucket in your hand: its mob, code and who caught it."; }
    @Override public String example() { return "/bucket admin info"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return true;
        ItemStack held = player.getInventory().getItemInMainHand();
        UUID id = plugin.getItems().filledOf(held);
        if (id == null) {
            messages().sendMessage(player, "info.none");
            return true;
        }
        String code = plugin.getItems().tokens().token(held);
        int left = code == null ? 0 : plugin.getItems().tokens().ledger().left(code);
        plugin.getStorage().afterWrites(() -> {
            StoredMob mob;
            try {
                mob = plugin.getStorage().database().mob(id);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not read filled bucket " + id + ".", e);
                mob = null;
            }
            StoredMob found = mob;
            String catcher = found == null ? null : plugin.getServer().getOfflinePlayer(found.caughtBy()).getName();
            SchedulerUtil.runForEntity(plugin, player, () -> send(player, id, code, left, found, catcher));
        });
        return true;
    }

    private void send(Player player, UUID id, String code, int left, StoredMob mob, String catcher) {
        if (!player.isOnline()) return;
        String none = messages().getRawMessage("info.nothing");
        String state = messages().getRawMessage(left > 0 ? "info.works" : "info.copy");
        for (String line : messages().getStringList("info.box",
                "mob", mob == null ? Words.pretty(plugin.getItems().mobOf(player.getInventory().getItemInMainHand())) : Words.pretty(mob.type()),
                "id", id.toString(),
                "code", code == null ? none : code,
                "state", state,
                "stored", mob == null ? messages().getRawMessage("info.empty") : messages().getRawMessage("info.stored", "format", mob.format(), "version", String.valueOf(mob.dataVersion())),
                "player", catcher == null ? none : catcher,
                "caught", mob == null ? none : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(mob.caughtAt())))) {
            player.sendMessage(line);
        }
    }
}
