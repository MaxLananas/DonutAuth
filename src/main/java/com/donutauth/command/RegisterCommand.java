package com.donutauth.command;

import com.donutauth.AuthManager;
import com.donutauth.DonutAuth;
import com.donutauth.lang.LangManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public class RegisterCommand implements CommandExecutor {

    private final DonutAuth plugin;
    private final AuthManager auth;
    private final LangManager lang;

    public RegisterCommand(DonutAuth plugin, AuthManager auth, LangManager lang) {
        this.plugin = plugin;
        this.auth = auth;
        this.lang = lang;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only command.");
            return true;
        }

        if (auth.isAuthenticated(player.getUniqueId())) {
            player.sendMessage(lang.get("prefix").append(lang.get("login-already-authenticated")));
            return true;
        }

        // Check if premium check is done
        if (!auth.isPremiumCheckDone(player.getUniqueId())) {
            player.sendMessage(lang.get("prefix").append(
                Component.text("Please wait, verifying your account...")));
            return true;
        }

        String storageKey = auth.getStorageKey(player.getUniqueId());
        if (storageKey == null) {
            player.sendMessage(lang.get("prefix").append(
                Component.text("Please wait, verifying your account...")));
            return true;
        }

        // Debug log
        plugin.getLogger().info("[LockAuth] /register by " + player.getName()
            + " storageKey=" + storageKey
            + " premium=" + auth.isPremium(player.getUniqueId()));

        if (auth.isRegistered(storageKey)) {
            player.sendMessage(Component.empty());
            player.sendMessage(lang.get("line"));
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("  ").append(lang.get("prefix"))
                .append(lang.get("register-already-registered")));
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("  ").append(lang.get("register-already-registered-hint")));
            player.sendMessage(Component.empty());
            player.sendMessage(lang.get("line"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(lang.get("prefix").append(lang.get("register-usage")));
            return true;
        }

        String password = args[0];
        String confirm = args[1];

        int minLen = plugin.getConfig().getInt("password.min-length", 4);
        int maxLen = plugin.getConfig().getInt("password.max-length", 32);

        if (password.length() < minLen) {
            player.sendMessage(lang.get("prefix").append(
                lang.get("register-too-short", "{min}", String.valueOf(minLen))));
            return true;
        }
        if (password.length() > maxLen) {
            player.sendMessage(lang.get("prefix").append(
                lang.get("register-too-long", "{max}", String.valueOf(maxLen))));
            return true;
        }
        if (!password.equals(confirm)) {
            player.sendMessage(Component.empty());
            player.sendMessage(lang.get("prefix").append(lang.get("register-password-mismatch")));
            player.sendMessage(Component.text("  ").append(lang.get("register-password-mismatch-hint")));
            return true;
        }

        boolean isPremium = auth.isPremium(player.getUniqueId());
        boolean success = auth.register(storageKey, password, isPremium, player.getName());

        if (!success) {
            player.sendMessage(lang.get("prefix").append(lang.get("register-error")));
            return true;
        }

        auth.setAuthenticated(player.getUniqueId(), true);

        // Success message
        player.sendMessage(Component.empty());
        player.sendMessage(lang.get("line"));
        player.sendMessage(Component.empty());
        player.sendMessage(lang.get("logo"));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  ").append(lang.get("register-success")));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  ").append(
            lang.get("register-welcome", "{player}", player.getName())));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  ").append(lang.get("register-next-time")));
        player.sendMessage(Component.text("  ").append(lang.get("register-next-command")));
        player.sendMessage(Component.empty());

        // Show account type stored
        if (isPremium) {
            player.sendMessage(Component.text("  ").append(lang.get("premium-detected")));
        } else {
            player.sendMessage(Component.text("  ").append(lang.get("crack-detected")));
        }

        player.sendMessage(Component.empty());
        player.sendMessage(lang.get("line"));

        Title.Times times = Title.Times.times(
            Duration.ofMillis(300), Duration.ofMillis(3000), Duration.ofMillis(500));
        player.showTitle(Title.title(
            lang.get("title-register-success"), lang.get("title-register-sub"), times));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        player.sendActionBar(lang.get("actionbar-authenticated"));

        // Log with storage key visible
        plugin.getLogger().info("[LockAuth] Registered: " + storageKey
            + " (premium=" + isPremium + ", display=" + player.getName() + ")");

        return true;
    }
}