package net.inertiamc.anythinginabucket.managers;

import net.inertiamc.core.ChatUtil;
import net.inertiamc.core.SchedulerUtil;
import net.inertiamc.anythinginabucket.AnythingInABucket;
import net.inertiamc.anythinginabucket.config.ConfigManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Offers joining players the bucket resource pack ({@code resource-pack.send}). With an empty {@code url} the newest
 * version of the Modrinth project is used, with the SHA-1 Modrinth reports; with a custom link the file is downloaded
 * once to compute its SHA-1. This runs off the server thread at startup and on each reload. Nothing is offered until
 * Modrinth answers, and a custom link whose download fails is offered without a hash.
 */
public final class HeadPack {

    /** A fixed id, so a rejoin or reload replaces the pack instead of adding a second copy. */
    static final UUID PACK_ID = UUID.nameUUIDFromBytes("anythinginabucket:heads".getBytes(StandardCharsets.UTF_8));
    /** The pack's Modrinth project, used when resource-pack.url is empty. The mod version uses the same project. */
    static final String MODRINTH_PROJECT = "anythinginabucket-resource-pack";
    private static final String MODRINTH_VERSIONS =
            "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT + "/version?include_changelog=false";
    private static final int MAX_BYTES = 50 * 1024 * 1024;

    /** The offered link and its SHA-1 (null means no hash check), for the url setting it was resolved from. */
    private record Found(String setting, String url, byte[] sha1) {}

    private final AnythingInABucket plugin;
    private volatile Found found;

    public HeadPack(AnythingInABucket plugin) {
        this.plugin = plugin;
    }

    /** Resolves the pack again, at startup and after a reload. */
    public void refresh() {
        ConfigManager.ResourcePack pack = plugin.getConfigManager().getSettings().resourcePack();
        if (!pack.send()) return;
        String url = pack.url();
        SchedulerUtil.runAsync(plugin, () -> {
            if (url.isBlank()) {
                try {
                    found = newestOnModrinth();
                    plugin.getLogger().info("Offering the bucket resource pack from Modrinth to players as they join.");
                } catch (IOException | RuntimeException e) {
                    found = null;
                    plugin.getLogger().warning("Could not find the bucket resource pack on Modrinth, so it is not offered"
                            + " until a reload or restart reaches it: " + e.getMessage());
                }
                return;
            }
            try {
                found = new Found(url, url, sha1(url));
                plugin.getLogger().info("Offering the bucket resource pack to players as they join.");
            } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
                found = new Found(url, url, null);
                plugin.getLogger().log(Level.WARNING, "Could not download the bucket resource pack from " + url
                        + " to check it; players are offered it without a check. " + e.getMessage());
            }
        });
    }

    /** Call on the player's own thread (the join event). */
    public void offer(Player player) {
        ConfigManager.ResourcePack pack = plugin.getConfigManager().getSettings().resourcePack();
        Found now = found;
        if (!pack.send() || now == null || !now.setting().equals(pack.url())) return;
        String prompt = ChatUtil.color(plugin.getMessageManager().getRawMessage("resource-pack.prompt"));
        player.addResourcePack(PACK_ID, now.url(), now.sha1(), prompt, false);
    }

    /** The newest version's primary file (Modrinth lists versions newest first). */
    private static Found newestOnModrinth() throws IOException {
        HttpURLConnection connection = open(MODRINTH_VERSIONS);
        if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
        JsonArray versions;
        try (InputStream in = connection.getInputStream()) {
            versions = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
        }
        if (versions.isEmpty()) throw new IOException("the project has no versions yet");
        JsonArray files = versions.get(0).getAsJsonObject().getAsJsonArray("files");
        JsonObject file = null;
        for (JsonElement f : files) {
            if (file == null || f.getAsJsonObject().get("primary").getAsBoolean()) file = f.getAsJsonObject();
        }
        if (file == null) throw new IOException("its newest version has no file");
        return new Found("", file.get("url").getAsString(), hex(file.getAsJsonObject("hashes").get("sha1").getAsString()));
    }

    private static byte[] hex(String text) {
        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        // Modrinth asks API clients to send an identifying User-Agent.
        connection.setRequestProperty("User-Agent", "InertiaMC/AnythingInABucket (github.com/InertiaMC/AnythingInABucket-Plugin)");
        return connection;
    }

    private static byte[] sha1(String url) throws IOException, NoSuchAlgorithmException {
        HttpURLConnection connection = open(url);
        if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream in = connection.getInputStream()) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            for (int n; (n = in.read(buffer)) > 0; ) {
                total += n;
                if (total > MAX_BYTES) throw new IOException("larger than 50 MB");
                digest.update(buffer, 0, n);
            }
        }
        return digest.digest();
    }
}
