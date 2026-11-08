package net.inertiamc.anythinginabucket.commands.sub;

import net.inertiamc.core.SchedulerUtil;
import net.inertiamc.core.TabCompleteUtil;
import net.inertiamc.anythinginabucket.AnythingInABucket;
import net.inertiamc.anythinginabucket.util.PermissionNodes;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** {@code /bucket give <player|*> [amount]}: stamped Capture Buckets, joined into the players' stacks. */
public final class SubGive extends AbstractBucketSub {

    public SubGive(AnythingInABucket plugin) {
        super(plugin);
    }

    @Override public String name() { return "give"; }
    @Override public String permission() { return PermissionNodes.GIVE; }
    @Override public String usage() { return "/bucket give <player> [amount]"; }
    @Override public String description() { return "Give Capture Buckets."; }
    @Override public String example() { return "/bucket give Steve 3"; }
    @Override public boolean hideWhenNoPermission() { return true; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 2) return usage(sender);
        int amount = 1;
        if (args.length == 2) {
            Integer parsed = parseInt(sender, args[1], 1, 64);
            if (parsed == null) return true;
            amount = parsed;
        }
        List<Player> targets = new ArrayList<>();
        if (args[0].equals("*")) {
            targets.addAll(plugin.getServer().getOnlinePlayers());
        } else {
            Player target = online(sender, args[0]);
            if (target == null) return true;
            targets.add(target);
        }
        int count = amount;
        String amountText = String.valueOf(count);
        for (Player target : targets) {
            SchedulerUtil.runForEntity(plugin, target, () -> {
                plugin.getItems().giveBuckets(target, count);
                messages().sendMessage(target, count == 1 ? "give.received-one" : "give.received", "amount", amountText);
            });
        }
        String who = args[0].equals("*") ? messages().getRawMessage("give.everyone") : targets.get(0).getName();
        messages().sendMessage(sender, count == 1 ? "give.given-one" : "give.given", "amount", amountText, "player", who);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>(onlineNames());
            names.add("*");
            return TabCompleteUtil.filter(args[0], names);
        }
        if (args.length == 2) return TabCompleteUtil.filter(args[1], "1", "4", "16");
        return List.of();
    }
}
