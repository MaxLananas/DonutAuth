package com.donutauth;

import com.donutauth.lang.LangManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Periodic ActionBar task for unauthenticated players.
 */
public class ActionBarTask implements Runnable {

    private final DonutAuth plugin;
    private final AuthManager auth;
    private final LangManager lang;

    public ActionBarTask(DonutAuth plugin, AuthManager auth, LangManager lang) {
        this.plugin = plugin;
        this.auth = auth;
        this.lang = lang;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (auth.isAuthenticated(player.getUniqueId())) {
                continue;
            }

            boolean registered = auth.isRegisteredByUUID(player.getUniqueId());
            Component message;

            if (registered) {
                message = lang.get("actionbar-login");
            } else {
                message = lang.get("actionbar-register");
            }

            player.sendActionBar(message);
        }
    }
}