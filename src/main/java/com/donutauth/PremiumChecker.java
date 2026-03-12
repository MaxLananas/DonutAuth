package com.donutauth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PremiumChecker {

    private final Logger logger;
    private final int timeout;
    private final HttpClient httpClient;

    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();

    public PremiumChecker(Logger logger, int timeoutMs) {
        this.logger = logger;
        this.timeout = timeoutMs;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(timeoutMs))
            .build();
    }

    public CompletableFuture<Boolean> isPremium(String username) {
        String key = username.toLowerCase();

        if (cache.containsKey(key)) {
            return CompletableFuture.completedFuture(cache.get(key));
        }

        String url = "https://api.mojang.com/users/profiles/minecraft/" + username;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(timeout))
            .GET()
            .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                boolean premium = response.statusCode() == 200
                    && response.body() != null
                    && !response.body().isEmpty()
                    && response.body().contains("\"id\"");

                cache.put(key, premium);

                if (premium) {
                    logger.info("[LockAuth] " + username + " -> PREMIUM (Mojang account found)");
                } else {
                    logger.info("[LockAuth] " + username + " -> CRACK (no Mojang account)");
                }

                return premium;
            })
            .exceptionally(ex -> {
                logger.warning("[LockAuth] Premium check failed for " + username + ": " + ex.getMessage());
                logger.warning("[LockAuth] Defaulting to CRACK for safety.");
                cache.put(key, false);
                return false;
            });
    }

    /**
     * Build the storage key.
     * Premium = "username" (lowercase)
     * Crack   = ".username" (lowercase, dot prefix)
     */
    public static String getStorageKey(String username, boolean isPremium) {
        String lower = username.toLowerCase();
        if (isPremium) {
            return lower;
        } else {
            return "." + lower;
        }
    }

    public static boolean isCrackKey(String storageKey) {
        return storageKey.startsWith(".");
    }

    /**
     * Extract display name from storage key (remove dot if crack)
     */
    public static String getDisplayName(String storageKey) {
        if (storageKey.startsWith(".")) {
            return storageKey.substring(1);
        }
        return storageKey;
    }

    public boolean isCached(String username) {
        return cache.containsKey(username.toLowerCase());
    }

    public Boolean getCached(String username) {
        return cache.get(username.toLowerCase());
    }

    public void clearCache() {
        cache.clear();
    }

    public void clearCache(String username) {
        cache.remove(username.toLowerCase());
    }
}