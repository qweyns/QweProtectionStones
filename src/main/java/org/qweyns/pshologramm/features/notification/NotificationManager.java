package org.qweyns.pshologramm.features.notification;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.models.RegionData;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NotificationManager {
    private final PSHologramm plugin;

    private final Cache<String, Long> webhookRateLimiter = CacheBuilder.newBuilder()
            .expireAfterWrite(15, TimeUnit.SECONDS)
            .build();

    public NotificationManager(PSHologramm plugin) { this.plugin = plugin; }

    public void sendAttackAlert(String regionId, String ownerName, String regionType, org.bukkit.Location loc) {
        RegionData rd = plugin.getStorageManager().getRegion(regionId);
        if (rd == null) return;
        List<String> effects = rd.getEffects();

        boolean hasAlerts = effects != null && effects.stream().anyMatch(e -> e.startsWith("ALERTS"));
        if (!hasAlerts) return;

        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        org.bukkit.entity.Player owner = Bukkit.getPlayer(ownerName);

        if (owner != null && owner.isOnline()) {
            owner.sendMessage(plugin.getConfigManager().getMessage("region_under_attack_chat", "%type%", regionType, "%x%", String.valueOf(loc.getBlockX()), "%y%", String.valueOf(loc.getBlockY()), "%z%", String.valueOf(loc.getBlockZ())));
            owner.playSound(owner.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
        }

        if (webhookRateLimiter.getIfPresent(regionId + "_attack") != null) return;
        webhookRateLimiter.put(regionId + "_attack", System.currentTimeMillis());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String rawMessage = plugin.getConfigManager().getRawMessage("region_under_attack_raw", "%type%", regionType, "%x%", String.valueOf(loc.getBlockX()), "%y%", String.valueOf(loc.getBlockY()), "%z%", String.valueOf(loc.getBlockZ())).replace("\n", " ");
            sendWebhooks(cfg, rawMessage);
        });
    }

    public void sendIntruderAlert(String regionId, String ownerName, String regionType, String intruderName) {
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        org.bukkit.entity.Player owner = Bukkit.getPlayer(ownerName);

        if (owner != null && owner.isOnline()) {
            owner.sendMessage(plugin.getConfigManager().getMessage("intruder_alert_chat", "%type%", regionType, "%intruder%", intruderName));
            owner.playSound(owner.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
        }

        if (webhookRateLimiter.getIfPresent(regionId + "_intrude") != null) return;
        webhookRateLimiter.put(regionId + "_intrude", System.currentTimeMillis());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String rawMessage = plugin.getConfigManager().getRawMessage("intruder_alert_raw", "%type%", regionType, "%intruder%", intruderName).replace("\n", " ");
            sendWebhooks(cfg, rawMessage);
        });
    }

    private void sendWebhooks(FileConfiguration cfg, String rawMessage) {
        String discordUrl = cfg.getString("notifications.discord_webhook", "");
        if (!discordUrl.isEmpty()) sendPostRequest(discordUrl, "{\"content\": \"" + rawMessage + "\"}");

        String tgToken = cfg.getString("notifications.telegram_bot_token", "");
        String tgChatId = cfg.getString("notifications.telegram_chat_id", "");
        if (!tgToken.isEmpty() && !tgChatId.isEmpty()) {
            String tgUrl = "https://api.telegram.org/bot" + tgToken + "/sendMessage";
            sendPostRequest(tgUrl, "{\"chat_id\": \"" + tgChatId + "\", \"text\": \"" + rawMessage + "\"}");
        }
    }

    private void sendPostRequest(String urlStr, String jsonPayload) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            conn.getResponseCode();
        } catch (Exception ignored) {
        }
    }
}
