package net.inertiamc.anythinginabucket.commands.sub;

import net.inertiamc.core.command.HelpRenderer;
import net.inertiamc.core.util.Paginator;
import net.inertiamc.anythinginabucket.AnythingInABucket;
import net.inertiamc.anythinginabucket.util.PermissionNodes;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public final class SubHelp extends AbstractBucketSub {

    private static final int PAGE_SIZE = 10;

    /** One help line; the example is shown on hover. */
    public record Line(String permission, String usage, String description, String example) {}

    /** One command form per line. */
    public static final List<Line> LINES = List.of(
            new Line(PermissionNodes.HELP, "/bucket help [page]", "Show this help page.", "/bucket help 2"),
            new Line(PermissionNodes.GIVE, "/bucket give <player> [amount]", "Give Capture Buckets (* for everyone online).", "/bucket give Steve 3"),
            new Line(PermissionNodes.ADMIN_INFO, "/bucket admin info", "The filled bucket in your hand: its mob, code and who caught it.", "/bucket admin info"),
            new Line(PermissionNodes.ADMIN_RELOAD, "/bucket admin reload", "Reload every config file.", "/bucket admin reload"));

    public SubHelp(AnythingInABucket plugin) {
        super(plugin);
    }

    @Override public String name() { return "help"; }
    @Override public String permission() { return PermissionNodes.HELP; }
    @Override public String usage() { return "/bucket help [page]"; }
    @Override public String description() { return "Show this help page."; }
    @Override public String example() { return "/bucket help 2"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        send(plugin, sender, args, 0);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) return List.of();
        int visible = 0;
        for (Line line : lines(plugin)) if (sender.hasPermission(line.permission())) visible++;
        List<String> pages = new ArrayList<>();
        for (int page = 1; page <= Paginator.pages(visible, PAGE_SIZE); page++) pages.add(String.valueOf(page));
        return pages;
    }

    /** Also used to answer an unknown subcommand, hence the page argument index parameter. */
    public static void send(AnythingInABucket plugin, CommandSender sender, String[] args, int pageArgIndex) {
        HelpRenderer help = new HelpRenderer()
                .title("&#E07A5F&lBUCKET &8» &fCommands")
                .pageColor("&#E07A5F")
                .linesPerPage(PAGE_SIZE);
        for (Line line : lines(plugin)) help.line(line.permission(), text(line), line.example());
        help.send(sender, args, pageArgIndex);
    }

    /** Leaves out {@code give} while the Capture Bucket is disabled ({@code catch-with: normal-bucket}). */
    static List<Line> lines(AnythingInABucket plugin) {
        if (plugin.getConfigManager().getSettings().catchWith().capture()) return LINES;
        return LINES.stream().filter(line -> !line.permission().equals(PermissionNodes.GIVE)).toList();
    }

    /** Formats a line as {@code &f/bucket give &c<player> &8[amount] &7- Give Capture Buckets...}. */
    static String text(Line line) {
        StringBuilder out = new StringBuilder();
        String last = "";
        for (String word : line.usage().split(" ")) {
            String colour = word.startsWith("<") ? "&c" : word.startsWith("[") ? "&8" : "&f";
            if (!colour.equals(last)) out.append(colour);
            last = colour;
            out.append(word).append(' ');
        }
        return out.append("&7- ").append(line.description()).toString();
    }
}
