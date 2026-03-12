package com.donutauth.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class LangManager {

    private static final String[] LANGUAGES = {
        "en", "fr", "es", "de", "pt", "it", "nl", "pl", "ru", "tr"
    };

    private final JavaPlugin plugin;
    private final Logger logger;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<String, String> messages = new HashMap<>();
    private String currentLang;

    // Custom branding from config
    private String namePart1 = "LOCK";
    private String nameColor1 = "gold";
    private String namePart2 = "AUTH";
    private String nameColor2 = "white";
    private String displayName = "LockAuth";

    public LangManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load(String language) {
        this.currentLang = language.toLowerCase();

        // Load branding from config
        FileConfiguration config = plugin.getConfig();
        this.displayName = config.getString("display-name", "LockAuth");
        this.namePart1 = config.getString("name-part1", "LOCK");
        this.nameColor1 = config.getString("name-color1", "gold");
        this.namePart2 = config.getString("name-part2", "AUTH");
        this.nameColor2 = config.getString("name-color2", "white");

        // Extract all language files
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) langDir.mkdirs();

        for (String lang : LANGUAGES) {
            File langFile = new File(langDir, lang + ".yml");
            if (!langFile.exists()) {
                plugin.saveResource("lang/" + lang + ".yml", false);
            }
        }

        // Load English as fallback
        messages.clear();
        loadLanguageFile("en");

        // Override with selected language
        if (!currentLang.equals("en")) {
            loadLanguageFile(currentLang);
        }

        logger.info("[LockAuth] Language loaded: " + currentLang
            + " (" + messages.size() + " keys)"
            + " | Branding: " + namePart1 + namePart2);
    }

    private void loadLanguageFile(String lang) {
        File externalFile = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
        YamlConfiguration config;

        if (externalFile.exists()) {
            config = YamlConfiguration.loadConfiguration(externalFile);
        } else {
            InputStream stream = plugin.getResource("lang/" + lang + ".yml");
            if (stream == null) {
                logger.warning("[LockAuth] Language file not found: " + lang + ".yml");
                return;
            }
            config = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        }

        for (String key : config.getKeys(false)) {
            String value = config.getString(key);
            if (value != null) {
                messages.put(key, value);
            }
        }
    }

    /**
     * Get raw MiniMessage string with branding placeholders replaced
     */
    public String getRaw(String key) {
        String raw = messages.getOrDefault(key, "<red>Missing: " + key + "</red>");
        return replaceBranding(raw);
    }

    /**
     * Replace {name}, {name1}, {name2}, {color1}, {color2} in any string
     */
    private String replaceBranding(String raw) {
        return raw
            .replace("{name}", displayName)
            .replace("{name1}", namePart1)
            .replace("{name2}", namePart2)
            .replace("{color1}", nameColor1)
            .replace("{color2}", nameColor2);
    }

    /**
     * Get parsed Component
     */
    public Component get(String key) {
        return mm.deserialize(getRaw(key));
    }

    /**
     * Get parsed Component with additional placeholders
     */
    public Component get(String key, String... replacements) {
        String raw = getRaw(key);
        for (int i = 0; i < replacements.length - 1; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        return mm.deserialize(raw);
    }

    /**
     * Build the prefix component dynamically from config
     */
    public Component prefix() {
        return Component.empty()
            .append(Component.text(namePart1, resolveColor(nameColor1), TextDecoration.BOLD))
            .append(Component.text(namePart2, resolveColor(nameColor2), TextDecoration.BOLD))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY));
    }

    /**
     * Build the logo component dynamically from config
     */
    public Component logo() {
        return Component.empty()
            .append(Component.text("       "))
            .append(Component.text(namePart1, resolveColor(nameColor1), TextDecoration.BOLD))
            .append(Component.text(namePart2, resolveColor(nameColor2), TextDecoration.BOLD));
    }

    /**
     * Build kick message with branding
     */
    public Component kickMessage(String key) {
        String raw = getRaw(key);
        // Replace the hardcoded DONUT/SMP in kick messages with custom branding
        return mm.deserialize(raw);
    }

    /**
     * Resolve a color name to NamedTextColor
     */
    private NamedTextColor resolveColor(String colorName) {
        return switch (colorName.toLowerCase()) {
            case "black" -> NamedTextColor.BLACK;
            case "dark_blue" -> NamedTextColor.DARK_BLUE;
            case "dark_green" -> NamedTextColor.DARK_GREEN;
            case "dark_aqua" -> NamedTextColor.DARK_AQUA;
            case "dark_red" -> NamedTextColor.DARK_RED;
            case "dark_purple" -> NamedTextColor.DARK_PURPLE;
            case "gold" -> NamedTextColor.GOLD;
            case "gray" -> NamedTextColor.GRAY;
            case "dark_gray" -> NamedTextColor.DARK_GRAY;
            case "blue" -> NamedTextColor.BLUE;
            case "green" -> NamedTextColor.GREEN;
            case "aqua" -> NamedTextColor.AQUA;
            case "red" -> NamedTextColor.RED;
            case "light_purple" -> NamedTextColor.LIGHT_PURPLE;
            case "yellow" -> NamedTextColor.YELLOW;
            case "white" -> NamedTextColor.WHITE;
            default -> NamedTextColor.GOLD;
        };
    }

    public String getCurrentLang() { return currentLang; }
    public String[] getAvailableLanguages() { return LANGUAGES; }
    public String getDisplayName() { return displayName; }
    public String getNamePart1() { return namePart1; }
    public String getNamePart2() { return namePart2; }
}