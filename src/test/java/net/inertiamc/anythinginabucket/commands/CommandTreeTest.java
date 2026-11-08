package net.inertiamc.anythinginabucket.commands;

import net.inertiamc.anythinginabucket.commands.sub.SubHelp;
import net.inertiamc.anythinginabucket.config.KeyContractTest;
import net.inertiamc.anythinginabucket.util.PermissionNodes;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Permission nodes are granted by exact name, so a command must keep its node even if it moves. */
class CommandTreeTest {

    /** Every help line's node, pinned. */
    private static final Map<String, String> NODES = Map.of(
            "/bucket help [page]", "aiab.command.help",
            "/bucket give <player> [amount]", "aiab.command.give",
            "/bucket admin info", "aiab.command.admin.info",
            "/bucket admin reload", "aiab.command.admin.reload");

    @Test
    void everyHelpLineKeepsItsNode() {
        assertEquals(NODES.size(), SubHelp.LINES.size());
        for (SubHelp.Line line : SubHelp.LINES) {
            assertEquals(NODES.get(line.usage()), line.permission(), line.usage());
            assertTrue(line.example().startsWith(line.usage().split(" <| \\[")[0]), line.example());
        }
    }

    @Test
    void everyNodeIsDeclared() {
        Set<String> declared = KeyContractTest.declaredPermissions();
        for (String node : NODES.values()) assertTrue(declared.contains(node), node);
        assertTrue(declared.contains(PermissionNodes.ADMIN));
        assertTrue(declared.contains(PermissionNodes.CATCH));
        assertTrue(declared.contains(PermissionNodes.RELEASE));
        assertTrue(declared.contains(PermissionNodes.BYPASS_OWNER));
    }

    @Test
    void helpLinesAreOneFormEach() {
        for (SubHelp.Line line : SubHelp.LINES) {
            assertFalse(line.usage().contains("|"), line.usage());
            assertFalse(line.usage().contains(" -"), line.usage());
        }
    }
}
