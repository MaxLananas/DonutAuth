package com.donutauth;

import com.donutauth.command.DonutAuthCommand;
import com.donutauth.command.LoginCommand;
import com.donutauth.command.RegisterCommand;
import com.donutauth.lang.LangManager;
import com.donutauth.util.Scheduler;
import org.bukkit.plugin.java.JavaPlugin;

public class DonutAuth extends JavaPlugin {

    private AuthManager authManager;
    private LangManager langManager;
    private PremiumChecker premiumChecker;

    @Override
    public void onEnable() {
        getLogger().info("========================================");
        getLogger().info("  LockAuth v" + getDescription().getVersion());
        getLogger().info("  Premium & Crack Authentication");
        getLogger().info("  Scheduler: " + (Scheduler.isFolia() ? "Folia" : "Paper/Bukkit"));
        getLogger().info("========================================");

        saveDefaultConfig();

        authManager = new AuthManager(getDataFolder(), getLogger());
        authManager.load();

        langManager = new LangManager(this);
        langManager.load(getConfig().getString("language", "en"));

        int premiumTimeout = getConfig().getInt("premium-check-timeout", 3000);
        premiumChecker = new PremiumChecker(getLogger(), premiumTimeout);

        RegisterCommand regCmd = new RegisterCommand(this, authManager, langManager);
        LoginCommand loginCmd = new LoginCommand(this, authManager, langManager);
        DonutAuthCommand adminCmd = new DonutAuthCommand(this, authManager, langManager);

        getCommand("register").setExecutor(regCmd);
        getCommand("login").setExecutor(loginCmd);
        getCommand("lockauth").setExecutor(adminCmd);
        getCommand("lockauth").setTabCompleter(adminCmd);

        getServer().getPluginManager().registerEvents(
            new AuthListener(this, authManager, langManager, premiumChecker), this);

        // Start ActionBar task
        startActionBarTask();

        getLogger().info("[LockAuth] Ready! Language: " + langManager.getCurrentLang());
    }

    @Override
    public void onDisable() {
        if (authManager != null) authManager.save();
        getLogger().info("[LockAuth] Disabled. Data saved.");
    }

    public void reload() {
        reloadConfig();
        langManager.load(getConfig().getString("language", "en"));
        premiumChecker.clearCache();
        startActionBarTask();
        getLogger().info("[LockAuth] Reloaded! Language: " + langManager.getCurrentLang());
    }

    private void startActionBarTask() {
        int interval = getConfig().getInt("actionbar-interval", 30);
        ActionBarTask task = new ActionBarTask(this, authManager, langManager);
        Scheduler.runGlobalTimer(this, task, 40L, interval);
    }

    public AuthManager getAuthManager() { return authManager; }
    public LangManager getLangManager() { return langManager; }
    public PremiumChecker getPremiumChecker() { return premiumChecker; }
}