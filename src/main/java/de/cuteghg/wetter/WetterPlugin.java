package de.cuteghg.wetter;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.*;

public final class WetterPlugin extends JavaPlugin implements Listener, TabCompleter {
    private static final String PREFIX = ChatColor.AQUA + "Wetter " + ChatColor.DARK_GRAY + "» " + ChatColor.GRAY;
    private static final int[] CAMERA_SLOTS = {18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44};
    private final Map<UUID, Map<Integer, CameraEntry>> openMenus = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        Objects.requireNonNull(getCommand("wetter")).setExecutor(this::onCommand);
        Objects.requireNonNull(getCommand("wetter")).setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Wetter Ingame-GUI aktiviert.");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) { sender.sendMessage(PREFIX + "Nur Spieler können die GUI öffnen."); return true; }
            openMenu(player, 0);
            return true;
        }
        if (!sender.hasPermission("wetter.admin")) { sender.sendMessage(PREFIX + "Dafür hast du keine Rechte."); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("help")) {
            sender.sendMessage(ChatColor.AQUA + "Wetter-Befehle:");
            sender.sendMessage(ChatColor.GRAY + "/wetter " + ChatColor.WHITE + "GUI öffnen");
            sender.sendMessage(ChatColor.GRAY + "/wetter add <Kameraname> " + ChatColor.WHITE + "Kamera öffentlich hinzufügen");
            sender.sendMessage(ChatColor.GRAY + "/wetter remove <Kameraname> " + ChatColor.WHITE + "Öffentliche Kamera entfernen");
            sender.sendMessage(ChatColor.GRAY + "/wetter list " + ChatColor.WHITE + "Öffentliche Kameras anzeigen");
            sender.sendMessage(ChatColor.GRAY + "/wetter reload " + ChatColor.WHITE + "Konfiguration neu laden");
            return true;
        }
        if (sub.equals("reload")) { reloadConfig(); sender.sendMessage(PREFIX + "Konfiguration neu geladen."); return true; }
        if (sub.equals("list")) {
            List<CameraEntry> entries = cameras();
            sender.sendMessage(PREFIX + "Öffentliche Kameras (" + entries.size() + "):");
            for (CameraEntry entry : entries) sender.sendMessage(ChatColor.GRAY + "• " + ChatColor.WHITE + entry.name + ChatColor.DARK_GRAY + " (" + entry.world + ")");
            return true;
        }
        if ((sub.equals("add") || sub.equals("remove")) && args.length >= 2) {
            String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
            if (sub.equals("add")) addCamera(sender, name); else removeCamera(sender, name);
            return true;
        }
        sender.sendMessage(PREFIX + "Nutze /wetter help für alle Befehle.");
        return true;
    }

    private void addCamera(CommandSender sender, String name) {
        if (name.isBlank()) { sender.sendMessage(PREFIX + "Bitte einen Kameranamen angeben."); return; }
        Plugin sc = Bukkit.getPluginManager().getPlugin("SecurityCams");
        if (sc == null || !sc.isEnabled()) { sender.sendMessage(PREFIX + "SecurityCams ist nicht installiert oder aktiviert."); return; }
        try {
            Class<?> storage = Class.forName("me.skilleeed.securitycams.models.CamStorage", true, sc.getClass().getClassLoader());
            List<?> all = (List<?>) storage.getMethod("getAllCams").invoke(null);
            for (Object owned : all) {
                Object cam = owned.getClass().getMethod("cam").invoke(owned);
                UUID owner = (UUID) owned.getClass().getMethod("owner").invoke(owned);
                String actual = (String) cam.getClass().getMethod("getDisplayName").invoke(cam);
                if (actual != null && actual.equalsIgnoreCase(name)) {
                    int id = (Integer) cam.getClass().getMethod("getId").invoke(cam);
                    Object loc = cam.getClass().getMethod("getCamLocation").invoke(cam);
                    String world = String.valueOf(loc.getClass().getMethod("world").invoke(loc));
                    String key = owner + "_" + id;
                    if (getConfig().contains("cameras." + key)) { sender.sendMessage(PREFIX + "Diese Kamera ist bereits öffentlich."); return; }
                    getConfig().set("cameras." + key + ".owner", owner.toString());
                    getConfig().set("cameras." + key + ".id", id);
                    getConfig().set("cameras." + key + ".name", actual);
                    getConfig().set("cameras." + key + ".world", world);
                    saveConfig();
                    sender.sendMessage(PREFIX + ChatColor.GREEN + actual + ChatColor.GRAY + " ist jetzt öffentlich.");
                    refreshOpenMenus();
                    return;
                }
            }
            sender.sendMessage(PREFIX + "Keine SecurityCams-Kamera mit dem Namen '" + name + "' gefunden.");
        } catch (ReflectiveOperationException | ClassCastException ex) {
            getLogger().warning("SecurityCams camera lookup failed: " + ex);
            sender.sendMessage(PREFIX + "Kameradaten konnten nicht aus SecurityCams gelesen werden.");
        }
    }

    private void removeCamera(CommandSender sender, String name) {
        String matched = null;
        for (String key : getConfig().getConfigurationSection("cameras") == null ? Set.<String>of() : getConfig().getConfigurationSection("cameras").getKeys(false)) {
            if (getConfig().getString("cameras." + key + ".name", "").equalsIgnoreCase(name)) { matched = key; break; }
        }
        if (matched == null) { sender.sendMessage(PREFIX + "Diese Kamera ist nicht in der öffentlichen Liste."); return; }
        getConfig().set("cameras." + matched, null);
        saveConfig();
        sender.sendMessage(PREFIX + ChatColor.GREEN + name + ChatColor.GRAY + " wurde aus der öffentlichen Liste entfernt.");
        refreshOpenMenus();
    }

    private List<CameraEntry> cameras() {
        List<CameraEntry> result = new ArrayList<>();
        if (getConfig().getConfigurationSection("cameras") == null) return result;
        for (String key : getConfig().getConfigurationSection("cameras").getKeys(false)) {
            String owner = getConfig().getString("cameras." + key + ".owner", "");
            String name = getConfig().getString("cameras." + key + ".name", "Kamera");
            String world = getConfig().getString("cameras." + key + ".world", "Unbekannt");
            int id = getConfig().getInt("cameras." + key + ".id", -1);
            try { result.add(new CameraEntry(UUID.fromString(owner), id, name, world)); } catch (IllegalArgumentException ignored) { }
        }
        result.sort(Comparator.comparing(e -> e.name.toLowerCase(Locale.ROOT)));
        return result;
    }

    private void openMenu(Player player, int page) {
        List<CameraEntry> all = cameras();
        int maxPage = Math.max(0, (all.size() - 1) / CAMERA_SLOTS.length);
        page = Math.max(0, Math.min(page, maxPage));
        WetterHolder holder = new WetterHolder(page);
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Wetter " + ChatColor.DARK_GRAY + "• " + ChatColor.GRAY + "Übersicht");
        holder.inventory = inv;
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        inv.setItem(4, item(Material.PLAYER_HEAD, ChatColor.GREEN + "" + ChatColor.BOLD + "ONLINE-SPIELER",
            List.of(ChatColor.GRAY + "Gerade online: " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers())));
        World world = player.getWorld();
        String weather = world.hasStorm() ? (world.isThundering() ? "Gewitter" : "Regen") : "Klar";
        Material weatherIcon = world.isThundering() ? Material.LIGHTNING_ROD : world.hasStorm() ? Material.WATER_BUCKET : Material.SUNFLOWER;
        inv.setItem(11, item(weatherIcon, ChatColor.YELLOW + "WETTER",
            List.of(ChatColor.GRAY + "Aktuell: " + ChatColor.WHITE + weather)));
        long ticks = (world.getTime() + 6000L) % 24000L;
        int hour = (int)(ticks / 1000L), minute = (int)((ticks % 1000L) * 60L / 1000L);
        inv.setItem(13, item(Material.CLOCK, ChatColor.GOLD + "UHRZEIT",
            List.of(ChatColor.GRAY + "Weltzeit: " + ChatColor.WHITE + String.format("%02d:%02d", hour, minute))));
        Map<Integer, CameraEntry> slots = new HashMap<>();
        int start = page * CAMERA_SLOTS.length;
        for (int i = 0; i < CAMERA_SLOTS.length && start + i < all.size(); i++) {
            CameraEntry entry = all.get(start + i);
            int slot = CAMERA_SLOTS[i];
            inv.setItem(slot, item(Material.ENDER_EYE, ChatColor.AQUA + "" + ChatColor.BOLD + entry.name,
                List.of(ChatColor.GRAY + "Welt: " + ChatColor.WHITE + entry.world,
                        ChatColor.GRAY + "Status: " + ChatColor.GREEN + "Öffentlich",
                        ChatColor.YELLOW + "Klicken zum Ansehen")));
            slots.put(slot, entry);
        }
        if (page > 0) inv.setItem(45, item(Material.ARROW, ChatColor.YELLOW + "Vorherige Seite", List.of()));
        if (page < maxPage) inv.setItem(53, item(Material.ARROW, ChatColor.YELLOW + "Nächste Seite", List.of()));
        inv.setItem(49, item(Material.BARRIER, ChatColor.RED + "Schließen", List.of()));
        openMenus.put(player.getUniqueId(), slots);
        player.openInventory(inv);
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof WetterHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 45) { openMenu(player, holder.page - 1); return; }
        if (slot == 53) { openMenu(player, holder.page + 1); return; }
        CameraEntry entry = openMenus.getOrDefault(player.getUniqueId(), Map.of()).get(slot);
        if (entry != null) watchCamera(player, entry);
    }

    private void watchCamera(Player player, CameraEntry entry) {
        Plugin sc = Bukkit.getPluginManager().getPlugin("SecurityCams");
        if (sc == null || !sc.isEnabled()) { player.sendMessage(PREFIX + "SecurityCams ist nicht verfügbar."); return; }
        try {
            Field field = sc.getClass().getDeclaredField("playerCamsManager");
            field.setAccessible(true);
            Object manager = field.get(sc);
            Method connect = manager.getClass().getMethod("connectToCam", Player.class, UUID.class, int.class);
            // SecurityCams checks this permission before moving the player into the selected camera.
            var attachment = player.addAttachment(this, "securitycams.connectTo.others", true);
            player.closeInventory();
            connect.invoke(manager, player, entry.owner, entry.id);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                try { player.removeAttachment(attachment); } catch (IllegalArgumentException ignored) { }
            }, 2L);
        } catch (ReflectiveOperationException ex) {
            getLogger().warning("Could not open SecurityCams view: " + ex);
            player.sendMessage(PREFIX + "Die Kameraansicht konnte nicht geöffnet werden. Prüfe die SecurityCams-Version.");
        }
    }

    private void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof WetterHolder holder) openMenu(player, holder.page);
        }
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("wetter.admin")) return List.of();
        if (args.length == 1) return List.of("add", "remove", "list", "reload", "help").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }

    private record CameraEntry(UUID owner, int id, String name, String world) { }
    private static final class WetterHolder implements InventoryHolder {
        private final int page;
        private Inventory inventory;
        private WetterHolder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
