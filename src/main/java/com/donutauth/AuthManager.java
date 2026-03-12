package com.donutauth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class AuthManager {

    private final File dataFile;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final SecureRandom random = new SecureRandom();

    // Session state
    private final Map<UUID, Boolean> authenticated = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> premiumStatus = new ConcurrentHashMap<>();
    private final Map<UUID, String> storageKeys = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionTimestamps = new ConcurrentHashMap<>();

    // Track if premium check is done
    private final Map<UUID, Boolean> premiumCheckDone = new ConcurrentHashMap<>();

    // Persistent accounts: storageKey -> PlayerData
    private Map<String, PlayerData> accounts = new ConcurrentHashMap<>();

    public static class PlayerData {
        public String salt;
        public String hash;
        public long registeredAt;
        public long lastLogin;
        public boolean isPremium;
        public String displayName;

        public PlayerData() {}

        public PlayerData(String salt, String hash, boolean isPremium, String displayName) {
            this.salt = salt;
            this.hash = hash;
            this.isPremium = isPremium;
            this.displayName = displayName;
            this.registeredAt = System.currentTimeMillis();
            this.lastLogin = System.currentTimeMillis();
        }
    }

    public AuthManager(File dataFolder, Logger logger) {
        this.logger = logger;
        if (!dataFolder.exists()) dataFolder.mkdirs();
        this.dataFile = new File(dataFolder, "accounts.json");
    }

    // === Load / Save ===

    public void load() {
        if (!dataFile.exists()) {
            logger.info("[LockAuth] No existing data, fresh start.");
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, PlayerData>>() {}.getType();
            Map<String, PlayerData> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                accounts.putAll(loaded);
                // Log crack vs premium count
                long crackCount = accounts.keySet().stream()
                    .filter(k -> k.startsWith(".")).count();
                long premiumCount = accounts.size() - crackCount;
                logger.info("[LockAuth] Loaded " + accounts.size() + " account(s) ("
                    + premiumCount + " premium, " + crackCount + " crack).");
            }
        } catch (Exception e) {
            logger.severe("[LockAuth] Load error: " + e.getMessage());
        }
    }

    public void save() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(dataFile), StandardCharsets.UTF_8)) {
            gson.toJson(accounts, writer);
        } catch (Exception e) {
            logger.severe("[LockAuth] Save error: " + e.getMessage());
        }
    }

    // === Storage Key Management ===

    public void setPlayerInfo(UUID uuid, String storageKey, boolean isPremium) {
        storageKeys.put(uuid, storageKey);
        premiumStatus.put(uuid, isPremium);
        premiumCheckDone.put(uuid, true);

        logger.info("[LockAuth] Player info set: uuid=" + uuid
            + " key=" + storageKey
            + " premium=" + isPremium);
    }

    public String getStorageKey(UUID uuid) {
        return storageKeys.get(uuid);
    }

    public boolean isPremium(UUID uuid) {
        return premiumStatus.getOrDefault(uuid, false);
    }

    public boolean isPremiumCheckDone(UUID uuid) {
        return premiumCheckDone.getOrDefault(uuid, false);
    }

    // === Registration ===

    public boolean isRegistered(String storageKey) {
        if (storageKey == null) return false;
        return accounts.containsKey(storageKey);
    }

    public boolean isRegisteredByUUID(UUID uuid) {
        String key = storageKeys.get(uuid);
        return key != null && accounts.containsKey(key);
    }

    public boolean register(String storageKey, String password, boolean isPremium, String displayName) {
        if (storageKey == null) return false;
        if (accounts.containsKey(storageKey)) return false;

        String salt = generateSalt();
        String hash = hashPassword(password, salt);
        accounts.put(storageKey, new PlayerData(salt, hash, isPremium, displayName));
        save();

        logger.info("[LockAuth] Account created: " + storageKey
            + " (premium=" + isPremium
            + ", display=" + displayName + ")");

        return true;
    }

    // === Authentication ===

    public boolean checkPassword(String storageKey, String password) {
        if (storageKey == null) return false;
        PlayerData data = accounts.get(storageKey);
        if (data == null) return false;
        String hash = hashPassword(password, data.salt);
        if (hash.equals(data.hash)) {
            data.lastLogin = System.currentTimeMillis();
            save();
            return true;
        }
        return false;
    }

    // === Session ===

    public void setAuthenticated(UUID uuid, boolean value) {
        if (value) {
            authenticated.put(uuid, true);
            sessionTimestamps.put(uuid, System.currentTimeMillis());
        } else {
            authenticated.remove(uuid);
        }
    }

    public boolean isAuthenticated(UUID uuid) {
        return authenticated.getOrDefault(uuid, false);
    }

    public boolean hasValidSession(UUID uuid, int timeoutMinutes) {
        if (timeoutMinutes <= 0) return false;
        Long lastSession = sessionTimestamps.get(uuid);
        if (lastSession == null) return false;
        long elapsed = System.currentTimeMillis() - lastSession;
        return elapsed < (long) timeoutMinutes * 60 * 1000;
    }

    // === Join timestamp ===

    public void setJoinTimestamp(UUID uuid) {
        joinTimestamps.put(uuid, System.currentTimeMillis());
    }

    public long getJoinTimestamp(UUID uuid) {
        return joinTimestamps.getOrDefault(uuid, System.currentTimeMillis());
    }

    // === Anti-bruteforce ===

    public int getFailedAttempts(UUID uuid) {
        return failedAttempts.getOrDefault(uuid, 0);
    }

    public void addFailedAttempt(UUID uuid) {
        failedAttempts.merge(uuid, 1, Integer::sum);
    }

    public void resetFailedAttempts(UUID uuid) {
        failedAttempts.remove(uuid);
    }

    // === Cleanup ===

    public void cleanup(UUID uuid) {
        authenticated.remove(uuid);
        premiumStatus.remove(uuid);
        storageKeys.remove(uuid);
        failedAttempts.remove(uuid);
        joinTimestamps.remove(uuid);
        premiumCheckDone.remove(uuid);
    }

    // === Stats ===

    public int getAccountCount() {
        return accounts.size();
    }

    public int getCrackAccountCount() {
        return (int) accounts.keySet().stream().filter(k -> k.startsWith(".")).count();
    }

    public int getPremiumAccountCount() {
        return accounts.size() - getCrackAccountCount();
    }

    public long getAuthenticatedCount() {
        return authenticated.values().stream().filter(v -> v).count();
    }

    /**
     * Debug: dump all accounts to console
     */
    public void debugAccounts() {
        logger.info("[LockAuth] === Account dump ===");
        for (Map.Entry<String, PlayerData> entry : accounts.entrySet()) {
            String key = entry.getKey();
            PlayerData data = entry.getValue();
            String type = key.startsWith(".") ? "CRACK" : "PREMIUM";
            logger.info("  " + key + " [" + type + "] display=" + data.displayName
                + " registered=" + data.registeredAt);
        }
        logger.info("[LockAuth] === End dump ===");
    }

    // === Hashing ===

    private String generateSalt() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            md.update(password.getBytes(StandardCharsets.UTF_8));
            byte[] first = md.digest();
            md.reset();
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            md.update(first);
            return Base64.getEncoder().encodeToString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}