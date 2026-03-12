package com.donutauth.command;

import com.donutauth.AuthManager;
import com.donutauth.DonutAuth;
import com.donutauth.lang.LangManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class DonutAuthCommand implements CommandExecutor, TabCompleter {

    private final DonutAuth plugin;
    private final AuthManager auth;
    private final LangManager lang;

    public DonutAuthCommand(DonutAuth plugin, AuthManager auth, LangManager lang) {
        this.plugin = plugin;
        this.auth = auth;
        this.lang = lang;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("lockauth.admin")) {
            sender.sendMessage(lang.get("prefix").append(lang.get("admin-no-permission")));
            return true;
        }

        if (args.length == 0) {
            sendInfo(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(lang.get("prefix").append(lang.get("admin-reloaded")));
            }
            case "info" -> sendInfo(sender);
            case "debug" -> {
                auth.debugAccounts();
                sender.sendMessage(lang.get("prefix").append(
                    Component.text("Account dump sent to console.", NamedTextColor.YELLOW)));
            }
            default -> sender.sendMessage(lang.get("prefix").append(
                Component.text("/lockauth <reload|info|debug>")));
        }

        return true;
    }

    private void sendInfo(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(lang.get("line"));
        sender.sendMessage(Component.empty());
        sender.sendMessage(lang.get("logo"));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("  ").append(
            lang.get("admin-info-header",
                "{version}", plugin.getDescription().getVersion())));
        sender.sendMessage(Component.text("  ").append(
            lang.get("admin-info-accounts",
                "{count}", String.valueOf(auth.getAccountCount()))));

        // Show premium vs crack breakdown
        sender.sendMessage(Component.text("  ").append(
            Component.text("Premium accounts: ", NamedTextColor.GRAY))
            .append(Component.text(String.valueOf(auth.getPremiumAccountCount()), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("  ").append(
            Component.text("Crack accounts: ", NamedTextColor.GRAY))
            .append(Component.text(String.valueOf(auth.getCrackAccountCount()), NamedTextColor.YELLOW)));

        sender.sendMessage(Component.text("  ").append(
            lang.get("admin-info-online-auth",
                "{count}", String.valueOf(auth.getAuthenticatedCount()))));
        sender.sendMessage(Component.text("  ").append(
            lang.get("admin-info-language",
                "{lang}", lang.getCurrentLang())));
        sender.sendMessage(Component.text("  ").append(
            lang.get("admin-info-premium",
                "{status}", String.valueOf(
                    plugin.getConfig().getBoolean("premium-auto-login", true)))));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("  Available: " + String.join(", ",
            lang.getAvailableLanguages()), NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.empty());
        sender.sendMessage(lang.get("line"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return Arrays.asList("reload", "info", "debug");
        return List.of();
    }
}