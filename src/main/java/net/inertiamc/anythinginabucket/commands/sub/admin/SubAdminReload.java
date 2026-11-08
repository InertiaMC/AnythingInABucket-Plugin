package net.inertiamc.anythinginabucket.commands.sub.admin;

import net.inertiamc.core.command.ReloadReport;
import net.inertiamc.anythinginabucket.AnythingInABucket;
import net.inertiamc.anythinginabucket.commands.sub.AbstractBucketSub;
import net.inertiamc.anythinginabucket.util.PermissionNodes;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /bucket admin reload}: reloads the config and messages and re-adds the recipe. database.yml is only read at
 * startup.
 */
public final class SubAdminReload extends AbstractBucketSub {

    public SubAdminReload(AnythingInABucket plugin) {
        super(plugin);
    }

    @Override public String name() { return "reload"; }
    @Override public String permission() { return PermissionNodes.ADMIN_RELOAD; }
    @Override public String usage() { return "/bucket admin reload"; }
    @Override public String description() { return "Reload every config file."; }
    @Override public String example() { return "/bucket admin reload"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        List<String> errors = plugin.reload();
        ReloadReport.send(sender, messages(), errors, plugin.getConfigManager().getProblemCount());
        return true;
    }
}
