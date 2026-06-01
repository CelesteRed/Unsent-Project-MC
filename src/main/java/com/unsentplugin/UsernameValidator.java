package com.unsentplugin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Checks that a recipient name is a real Minecraft account, querying a configurable, ordered list
 * of provider APIs with failover — the first provider to give a definitive answer wins; a provider
 * that errors or times out is skipped and the next is tried. Results are cached with a TTL to
 * respect rate limits.
 *
 * <p>Network calls block, so {@link #validate(String)} must run off the main server thread.
 * Configuration lives under {@code username-validation} in config.yml.
 */
public class UsernameValidator {

    /** Mojang usernames are 3–16 of [A-Za-z0-9_]; anything else can't be a real account. */
    private static final Pattern MOJANG_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    public enum Result {
        /** Confirmed to be a real account. */
        EXISTS,
        /** Confirmed not to be a real account. */
        NOT_FOUND,
        /** Couldn't get a definitive answer (all providers errored/timed out). */
        ERROR
    }

    private record CacheEntry(boolean exists, long expiresAtMillis) {}

    private final UnsentPlugin plugin;
    private final HttpClient http;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public UsernameValidator(UnsentPlugin plugin) {
        this.plugin = plugin;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("username-validation.enabled", false);
    }

    /** When all providers fail, reject the message (true) or allow it (false)? */
    public boolean isFailClosed() {
        return plugin.getConfig().getBoolean("username-validation.fail-closed", true);
    }

    /** Blocking — call from an async task. Never throws. */
    public Result validate(String name) {
        if (!MOJANG_NAME.matcher(name).matches()) return Result.NOT_FOUND;

        String key = name.toLowerCase(Locale.ROOT);
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAtMillis > System.currentTimeMillis()) {
            return cached.exists ? Result.EXISTS : Result.NOT_FOUND;
        }

        List<String> providers = plugin.getConfig().getStringList("username-validation.providers");
        if (providers.isEmpty()) providers = List.of("mojang", "playerdb", "ashcon");
        int timeout = Math.max(1, plugin.getConfig().getInt("username-validation.timeout-seconds", 6));

        for (String provider : providers) {
            Result result = queryProvider(provider.toLowerCase(Locale.ROOT).trim(), name, timeout);
            if (result == Result.EXISTS || result == Result.NOT_FOUND) {
                cache.put(key, new CacheEntry(result == Result.EXISTS, cacheExpiry()));
                return result;
            }
            // ERROR → try the next provider
        }
        return Result.ERROR;
    }

    private long cacheExpiry() {
        long minutes = Math.max(1, plugin.getConfig().getInt("username-validation.cache-ttl-minutes", 1440));
        return System.currentTimeMillis() + minutes * 60_000L;
    }

    /** Queries one provider. {@code name} is already validated to be safe for a URL path segment. */
    private Result queryProvider(String provider, String name, int timeoutSeconds) {
        String url = switch (provider) {
            case "mojang"          -> "https://api.mojang.com/users/profiles/minecraft/" + name;
            case "mojang-services" -> "https://api.minecraftservices.com/minecraft/profile/lookup/name/" + name;
            case "playerdb"        -> "https://playerdb.co/api/player/minecraft/" + name;
            case "ashcon"          -> "https://api.ashcon.app/mojang/v2/user/" + name;
            default -> null;
        };
        if (url == null) {
            plugin.getLogger().warning("username-validation: unknown provider '" + provider + "' — skipping.");
            return Result.ERROR;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("User-Agent", "UnsentPlugin")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();

            if (provider.equals("playerdb")) {
                return parsePlayerDb(response.body()); // playerdb answers 200 with a success flag
            }
            // mojang / mojang-services / ashcon are status-code based.
            if (code == 200) return Result.EXISTS;
            if (code == 404 || code == 204) return Result.NOT_FOUND;
            return Result.ERROR;
        } catch (Throwable t) {
            return Result.ERROR;
        }
    }

    private Result parsePlayerDb(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            boolean success = root.has("success") && root.get("success").getAsBoolean();
            String code = root.has("code") ? root.get("code").getAsString().toLowerCase(Locale.ROOT) : "";
            if (success || code.equals("player.found")) return Result.EXISTS;
            if (code.contains("invalid") || code.contains("not_found") || code.contains("not.found")) return Result.NOT_FOUND;
            return Result.ERROR;
        } catch (Exception e) {
            return Result.ERROR;
        }
    }
}
