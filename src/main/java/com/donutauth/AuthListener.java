package com.donutauth;

import com.donutauth.lang.LangManager;
import com.donutauth.util.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class AuthListener implements Listener {

    private final DonutAuth plugin;
    private final AuthManager auth;
    private final LangManager lang;
    private final PremiumChecker premiumChecker;

    public AuthListener(DonutAuth plugin, AuthManager auth, LangManager lang, PremiumChecker premiumChecker) {
        this.plugin = plugin;
        this.auth = auth;
        this.lang = lang;
        this.premiumChecker = premiumChecker;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();
        UUID uuid = player.getUniqueId();

        event.joinMessage(null);
        auth.setJoinTimestamp(uuid);

        boolean premiumAutoLogin = plugin.getConfig().getBoolean("premium-auto-login", true);
        int sessionTimeout = plugin.getConfig().getInt("session-timeout", 5);

        plugin.getLogger().info("[LockAuth] Player joining: " + name + " (UUID: " + uuid + ")");
        plugin.getLogger().info("[LockAuth] Checking premium status for: " + name);

        premiumChecker.isPremium(name).thenAccept(isPremium -> {

            String storageKey = PremiumChecker.getStorageKey(name, isPremium);

            plugin.getLogger().info("[LockAuth] Result for " + name
                + ": premium=" + isPremium + " storageKey=" + storageKey);

            auth.setPlayerInfo(uuid, storageKey, isPremium);

            Scheduler.runTask(plugin, player, () -> {
                if (!player.isOnline()) return;

                if (isPremium && premiumAutoLogin) {
                    auth.setAuthenticated(uuid, true);
                    sendPremiumWelcome(player);
                    plugin.getLogger().info("[LockAuth] Premium auto-login: " + name
                        + " (key: " + storageKey + ")");
                    return;
                }

                if (auth.isRegistered(storageKey) && auth.hasValidSession(uuid, sessionTimeout)) {
                    auth.setAuthenticated(uuid, true);
                    sendSessionWelcome(player);
                    plugin.getLogger().info("[LockAuth] Session restored: " + name
                        + " (key: " + storageKey + ")");
                    return;
                }

                plugin.getLogger().info("[LockAuth] Authentication required for: " + name
                    + " (key: " + storageKey + ")");
                sendAuthWelcome(player, isPremium);
                startLoginTimeout(player);
            });
        });
    }

    private void sendPremiumWelcome(Player player) {
        Scheduler.runTaskLater(plugin, player, () -> {
            if (!player.isOnline()) return;

            player.sendMessage(Component.empty());
            player.sendMessage(lang.get("line"));
            player.sendMessage(Component.empty());
            player.sendMessage(lang.get("logo"));
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("  ").append(
                lang.get("join-premium", "{player}", player.getName())));
            player.sendMessage(Component.empty());
            player.sendMessage(lang.get("line"));

            showTitle(player, "title-premium", "title-premium-sub");
            player.sendActionBar(lang.get("actionbar-authenticated"));
        }, 10L);
    }

    private void sendSessionWelcome(Player player) {
        Scheduler.runTaskLater(plugin, player, () -> {
            if (!player.isOnline()) return;

            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("  ").append(lang.get("prefix"))
                .append(lang.get("login-success")));
            player.sendMessage(Component.text("  ").append(
                lang.get("login-welcome", "{player}", player.getName())));

            player.sendActionBar(lang.get("actionbar-authenticated"));
        }, 10L);
    }

    private void sendAuthWelcome(Player player, boolean isPremium) {
        Scheduler.runTaskLater(plugin, player, () -> {
            if (!player.isOnline()) return;

            String storageKey = auth.getStorageKey(player.getUniqueId());
            boolean registered = auth.isRegistered(storageKey);

            player.sendMessage(Component.empty());
            player.sendMessage(lang.get("line"));
            player.sendMessage(Component.empty());
            player.sendMessage(lang.get("logo"));
            player.sendMessage(Component.empty());

            if (isPremium) {
                player.sendMessage(Component.text("  ").append(lang.get("premium-detected")));
            } else {
                player.sendMessage(Component.text("  ").append(lang.get("crack-detected")));
            }
            player.sendMessage(Component.empty());

            if (registered) {
                player.sendMessage(Component.text("  ").append(
                    lang.get("join-registered", "{player}", player.getName())));
                player.sendMessage(Component.text("  ").append(lang.get("join-registered-instruction")));
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("    ").append(lang.get("join-registered-command")));
                showTitle(player, "title-registered", "title-registered-sub");
            } else {
                player.sendMessage(Component.text("  ").append(
                    lang.get("join-new", "{player}", player.getName())));
                player.sendMessage(Component.text("  ").append(lang.get("join-new-instruction")));
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("    ").append(lang.get("join-new-command")));
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("  ").append(lang.get("join-new-hint")));
                showTitle(player, "title-new", "title-new-sub");
            }

            player.sendMessage(Component.empty());
            player.sendMessage(lang.get("line"));
            player.sendMessage(Component.empty());
        }, 10L);
    }

    private void showTitle(Player player, String titleKey, String subKey) {
        Title.Times times = Title.Times.times(
            Duration.ofMillis(300), Duration.ofMillis(3000), Duration.ofMillis(500));
        player.showTitle(Title.title(lang.get(titleKey), lang.get(subKey), times));
    }

    private void startLoginTimeout(Player player) {
        int timeoutSeconds = plugin.getConfig().getInt("login-timeout", 120);
        if (timeoutSeconds <= 0) return;

        Scheduler.runTaskLater(plugin, player, () -> {
            if (player.isOnline() && !auth.isAuthenticated(player.getUniqueId())) {
                player.kick(lang.get("kick-login-timeout"));
            }
        }, timeoutSeconds * 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!auth.isAuthenticated(player.getUniqueId())) {
            event.quitMessage(null);
        }
        auth.cleanup(player.getUniqueId());
    }

    // === Block all actions ===

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        if (auth.isAuthenticated(event.getPlayer().getUniqueId())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                to.getYaw(), to.getPitch()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (auth.isAuthenticated(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        Scheduler.runTask(plugin, player, () ->
            player.sendMessage(lang.get("prefix").append(lang.get("blocked-chat"))));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (auth.isAuthenticated(event.getPlayer().getUniqueId())) return;
        String cmd = event.getMessage().toLowerCase().split(" ")[0];
        List<String> allowed = plugin.getConfig().getStringList("allowed-commands");
        for (String a : allowed) {
            if (cmd.equals(a.toLowerCase())) return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(lang.get("prefix").append(lang.get("blocked-command")));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!auth.isAuthenticated(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!auth.isAuthenticated(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && !auth.isAuthenticated(p.getUniqueId()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageOther(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && !auth.isAuthenticated(p.getUniqueId()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p && !auth.isAuthenticated(p.getUniqueId()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (!auth.isAuthenticated(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        if (!auth.isAuthenticated(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!auth.isAuthenticated(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
}