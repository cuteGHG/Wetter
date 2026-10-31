package de.cuteghg.wetter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
        if (cameras) {
            send(ex, 200, "{\"integration\":\"not_connected\",\"cameras\":[],\"message\":\"No live camera provider is connected. No placeholder streams are shown.\"}");
            return;
        }
        try {
            var snapshot = Bukkit.getScheduler().callSyncMethod(this, this::statusJson).get(3, TimeUnit.SECONDS);
            send(ex, 200, snapshot);
        } catch (Exception e) {
            send(ex, 503, "{\"error\":\"server_snapshot_unavailable\"}");
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
            .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (var out = ex.getResponseBody()) { out.write(bytes); }
    }
}
