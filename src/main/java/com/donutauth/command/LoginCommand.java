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

public class LoginCommand implements CommandExecutor {

    private final DonutAuth plugin;
    private final AuthManager auth;
    private final LangManager lang;

    public LoginCommand(DonutAuth plugin, AuthManager auth, LangManager lang) {
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
        plugin.getLogger().info("[LockAuth] /login by " + player.getName()
            + " storageKey=" + storageKey
            + " premium=" + auth.isPremium(player.getUniqueId()));

        if (!auth.isRegistered(storageKey)) {
            player.sendMessage(Component.empty());
            player.sendMessage(lang.get("line"));
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("  ").append(lang.get("prefix"))
                .append(lang.get("login-not-registered")));
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("  ").append(lang.get("login-not-registered-hint")));
            player.sendMessage(Component.empty());
            player.sendMessage(lang.get("line"));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(lang.get("prefix").append(lang.get("login-usage")));
            return true;
        }

        int maxAttempts = plugin.getConfig().getInt("max-login-attempts", 5);
        int attempts = auth.getFailedAttempts(player.getUniqueId());

        if (attempts >= maxAttempts) {
            auth.resetFailedAttempts(player.getUniqueId());
            player.kick(lang.get("kick-too-many-attempts"));
            return true;
        }

        String password = args[0];
        boolean success = auth.checkPassword(storageKey, password);

        if (success) {
            auth.setAuthenticated(player.getUniqueId(), true);
            auth.resetFailedAttempts(player.getUniqueId());

            player.sendMessage(Component.empty());
            player.sendMessage(lang.get("line"));
            player.sendMessage(Component.empty());
            player.sendMessage(lang.get("logo"));
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("  ").append(lang.get("login-success")));
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("  ").append(
                lang.get("login-welcome", "{player}", player.getName())));
            player.sendMessage(Component.empty());
            player.sendMessage(lang.get("line"));

            Title.Times times = Title.Times.times(
                Duration.ofMillis(300), Duration.ofMillis(3000), Duration.ofMillis(500));
            player.showTitle(Title.title(
                lang.get("title-login-success"), lang.get("title-login-sub"), times));

            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.4f);
            player.sendActionBar(lang.get("actionbar-authenticated"));

            plugin.getLogger().info("[LockAuth] Login: " + storageKey + " (display=" + player.getName() + ")");

        } else {
            auth.addFailedAttempt(player.getUniqueId());
            int remaining = maxAttempts - attempts - 1;

            player.sendMessage(Component.empty());
            player.sendMessage(lang.get("prefix").append(lang.get("login-wrong-password")));

            if (remaining > 1) {
                player.sendMessage(Component.text("  ").append(
                    lang.get("login-attempts-remaining",
                        "{remaining}", String.valueOf(remaining))));
            } else {
                player.sendMessage(Component.text("  ").append(lang.get("login-last-attempt")));
            }

            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f);
        }

        return true;
    }
}