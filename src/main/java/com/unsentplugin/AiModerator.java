package com.unsentplugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Second layer of moderation on top of {@link WordFilter}. Sends each message to OpenAI and asks
 * a yes/no question — is this safe to display on a 13+ server? — to catch things the static
 * blocked-words list misses (creative spelling, leetspeak, innuendo, veiled threats, etc.).
 *
 * <p>All network calls are blocking, so {@link #check(String)} must be invoked off the main
 * server thread. Configuration lives under {@code ai-moderation} in {@code config.yml}.
 */
public class AiModerator {

    private static final String ENDPOINT = "https://api.openai.com/v1/chat/completions";

    /** The model is told to answer with exactly "true" (safe) or "false" (unsafe). */
    private static final String SYSTEM_PROMPT = """
        You are a strict content-moderation filter for a Minecraft server whose players are 13 or \
        older. A player wants to publicly post a short anonymous note addressed to someone. Decide \
        whether the note is appropriate to display.

        Mark the note as UNSAFE if it contains, hints at, or disguises any of the following:
        - Sexual content, innuendo, or references of any kind
        - Threats, violence, or wishes of harm toward a person
        - Harassment, bullying, or demeaning insults
        - Hate speech or slurs of any kind
        - Encouragement of self-harm or suicide
        - Profanity, or attempts to bypass a profanity filter using misspellings, leetspeak, extra \
        spaces, symbols, or other obfuscation

        Otherwise the note is SAFE. Kind, neutral, or sad-but-harmless messages are SAFE.

        Respond with EXACTLY one lowercase word and nothing else: "true" if the note is SAFE to \
        display, or "false" if it is UNSAFE.""";

    /** Outcome of a moderation check. */
    public enum Result {
        /** Model judged the message safe — allow it. */
        SAFE,
        /** Model judged the message unsafe — block it. */
        UNSAFE,
        /** The check couldn't complete (bad key, network error, timeout, unexpected reply). */
        ERROR
    }

    private final UnsentPlugin plugin;
    private final HttpClient http;

    public AiModerator(UnsentPlugin plugin) {
        this.plugin = plugin;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** True only when AI moderation is switched on and an API key is configured. */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("ai-moderation.enabled", false)
                && !apiKey().isEmpty();
    }

    /** When a check fails, should the message be rejected (true) or let through (false)? */
    public boolean isFailClosed() {
        return plugin.getConfig().getBoolean("ai-moderation.fail-closed", true);
    }

    /**
     * Asks OpenAI whether {@code message} is safe. Blocking — call from an async task. Never
     * throws; failures are reported as {@link Result#ERROR}.
     */
    public Result check(String message) {
        String key = apiKey();
        if (key.isEmpty()) return Result.ERROR;

        String model = plugin.getConfig().getString("ai-moderation.model", "gpt-4o-mini");
        int timeout = Math.max(1, plugin.getConfig().getInt("ai-moderation.timeout-seconds", 8));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(model, message), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                plugin.getLogger().warning("AI moderation: OpenAI returned HTTP " + response.statusCode()
                        + " — treating as error. " + shortBody(response.body()));
                return Result.ERROR;
            }
            return parseVerdict(response.body());
        } catch (Throwable t) {
            // Catch Throwable (not just Exception) so any failure — including a missing-class
            // Error — maps to ERROR and is handled by the fail-closed/open policy, rather than
            // escaping and stranding the player's in-flight check.
            plugin.getLogger().warning("AI moderation request failed: " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
            return Result.ERROR;
        }
    }

    private String apiKey() {
        return plugin.getConfig().getString("ai-moderation.api-key", "").trim();
    }

    /** Builds the chat-completions JSON payload. Gson handles all string escaping. */
    private static String buildRequestBody(String model, String message) {
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", SYSTEM_PROMPT);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", message);

        JsonArray messages = new JsonArray();
        messages.add(system);
        messages.add(user);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("temperature", 0);
        body.addProperty("max_tokens", 5);
        return body.toString();
    }

    /** Extracts choices[0].message.content and maps it to a verdict. Blocks on "false" by bias. */
    private Result parseVerdict(String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            String content = root.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString()
                    .toLowerCase().trim();

            // Bias toward blocking: if the model says "false" anywhere, treat as unsafe.
            if (content.contains("false")) return Result.UNSAFE;
            if (content.contains("true")) return Result.SAFE;

            plugin.getLogger().warning("AI moderation: unexpected reply \"" + content + "\" — treating as error.");
            return Result.ERROR;
        } catch (Exception e) {
            plugin.getLogger().warning("AI moderation: could not parse OpenAI response — treating as error.");
            return Result.ERROR;
        }
    }

    private static String shortBody(String body) {
        if (body == null || body.isEmpty()) return "";
        String trimmed = body.length() > 200 ? body.substring(0, 200) + "…" : body;
        return "(" + trimmed.replace("\n", " ") + ")";
    }
}
