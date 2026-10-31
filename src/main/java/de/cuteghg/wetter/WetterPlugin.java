package de.cuteghg.wetter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class WetterPlugin extends JavaPlugin {
    private HttpServer http;

    @Override public void onEnable() {
        saveDefaultConfig();
        if (getConfig().getBoolean("http.enabled", true)) startHttp();
        getLogger().info("Wetter dashboard bridge enabled.");
    }

    @Override public void onDisable() {
        if (http != null) http.stop(1);
    }

    private void startHttp() {
        try {
            String host = getConfig().getString("http.host", "127.0.0.1");
            int port = getConfig().getInt("http.port", 8765);
            http = HttpServer.create(new InetSocketAddress(host, port), 32);
            http.setExecutor(Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "Wetter-HTTP"); t.setDaemon(true); return t;
            }));
            http.createContext("/api/status", exchange -> handle(exchange, false));
            http.createContext("/api/cameras", exchange -> handle(exchange, true));
            http.start();
            getLogger().info("Read-only API listening on " + host + ":" + port);
        } catch (IOException e) {
            getLogger().severe("Could not start dashboard API: " + e.getMessage());
        }
    }

    private void handle(HttpExchange ex, boolean cameras) throws IOException {
        String origin = getConfig().getString("http.allowed-origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); ex.close(); return; }
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"error\":\"method_not_allowed\"}"); return; }
        try {
            String snapshot = Bukkit.getScheduler().callSyncMethod(this,
                cameras ? this::camerasJson : this::statusJson).get(3, TimeUnit.SECONDS);
            send(ex, 200, snapshot);
        } catch (Exception e) {
            getLogger().warning("Could not create API snapshot: " + e.getMessage());
            send(ex, 503, "{\"error\":\"server_snapshot_unavailable\"}");
        }
    }

    private String camerasJson() {
        Plugin securityCams = Bukkit.getPluginManager().getPlugin("SecurityCams");
        if (securityCams == null || !securityCams.isEnabled()) {
            return "{\"integration\":\"not_installed\",\"liveViewAvailable\":false,\"cameras\":[],"
                + "\"message\":\"SecurityCams is not installed or not enabled.\"}";
        }
        try {
            ClassLoader loader = securityCams.getClass().getClassLoader();
            Class<?> storage = Class.forName("me.skilleeed.securitycams.models.CamStorage", true, loader);
            Method getAllCams = storage.getMethod("getAllCams");
            Method getOwnerName = storage.getMethod("getOwnerName", UUID.class);
            List<?> ownedCams = (List<?>) getAllCams.invoke(null);
            StringBuilder items = new StringBuilder("[");
            boolean first = true;
            for (Object owned : ownedCams) {
                Object cam = owned.getClass().getMethod("cam").invoke(owned);
                UUID owner = (UUID) owned.getClass().getMethod("owner").invoke(owned);
                int id = (Integer) cam.getClass().getMethod("getId").invoke(cam);
                String name = (String) cam.getClass().getMethod("getDisplayName").invoke(cam);
                Object camLocation = cam.getClass().getMethod("getCamLocation").invoke(cam);
                String world = (String) camLocation.getClass().getMethod("world").invoke(camLocation);
                String ownerName = (String) getOwnerName.invoke(null, owner);
                if (!first) items.append(',');
                first = false;
                items.append("{\"id\":").append(id)
                    .append(",\"name\":\"").append(escape(name == null ? "Kamera " + id : name)).append("\"")
                    .append(",\"owner\":\"").append(escape(ownerName == null ? "Unbekannt" : ownerName)).append("\"")
                    .append(",\"world\":\"").append(escape(world == null ? "" : world)).append("\"}");
            }
            items.append(']');
            return "{\"integration\":\"securitycams\",\"liveViewAvailable\":false,"
                + "\"cameraCount\":" + ownedCams.size() + ",\"cameras\":" + items
                + ",\"message\":\"Kameraliste verbunden. SecurityCams 3.1.0 stellt keinen Browser-Livestream bereit.\"}";
        } catch (ReflectiveOperationException | ClassCastException e) {
            getLogger().warning("SecurityCams metadata adapter failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return "{\"integration\":\"error\",\"liveViewAvailable\":false,\"cameras\":[],"
                + "\"message\":\"SecurityCams wurde erkannt, aber die Kameradaten konnten nicht gelesen werden.\"}";
        }
    }

    private String statusJson() {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        StringBuilder worlds = new StringBuilder("[");
        boolean first = true;
        for (World w : Bukkit.getWorlds()) {
            if (!first) worlds.append(',');
            first = false;
            long ticks = w.getTime();
            String weather = w.isThundering() ? "thunder" : w.hasStorm() ? "rain" : "clear";
            worlds.append("{\"name\":\"").append(escape(w.getName())).append("\",")
                .append("\"weather\":\"").append(weather).append("\",")
                .append("\"timeTicks\":").append(ticks).append(',')
                .append("\"fullTime\":").append(w.getFullTime()).append('}');
        }
        worlds.append(']');
        return "{\"online\":" + online + ",\"maxPlayers\":" + max
            + ",\"motd\":\"" + escape(Bukkit.getMotd()) + "\""
            + ",\"serverVersion\":\"" + escape(Bukkit.getVersion()) + "\""
            + ",\"sampledAt\":\"" + Instant.now() + "\""
            + ",\"worlds\":" + worlds + "}";
    }

    private static String escape(String s) {
        return s.replace("\\\\", "\\\\\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (var out = ex.getResponseBody()) { out.write(bytes); }
    }
}
