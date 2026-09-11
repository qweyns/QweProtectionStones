package org.qweyns.qweprotectstones.features.notification;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.config.SoundSetting;
import org.qweyns.qweprotectstones.utils.ColorUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Оповещения владельцу в чат и вебхуки в Discord/Telegram. */
public class NotificationManager {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final QweProtectStones plugin;

    private final Cache<String, Long> webhookRateLimiter = CacheBuilder.newBuilder()
            .expireAfterWrite(15, TimeUnit.SECONDS)
            .build();

    /** Один переиспользуемый клиент вместо нового соединения на каждое уведомление. */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public NotificationManager(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public void sendAttackAlert(Region region, String ownerName, Location coreLocation) {
        if (!region.hasEffect("ALERTS")) return;

        String[] placeholders = {
                "%type%", region.getTypeId(),
                "%owner%", ownerName,
                "%x%", String.valueOf(coreLocation.getBlockX()),
                "%y%", String.valueOf(coreLocation.getBlockY()),
                "%z%", String.valueOf(coreLocation.getBlockZ()),
                "%durability%", String.valueOf(region.getDurability())
        };

        notifyTrusted(region, "region_under_attack_chat", plugin.getTunables().raidAttack(), placeholders);
        dispatchWebhooks(region.getId() + "_attack", "region_under_attack_raw", placeholders);
    }

    public void sendDestroyedAlert(Region region, Location coreLocation) {
        String[] placeholders = {
                "%type%", region.getTypeId(),
                "%owner%", region.getOwnerName(),
                "%x%", String.valueOf(coreLocation.getBlockX()),
                "%y%", String.valueOf(coreLocation.getBlockY()),
                "%z%", String.valueOf(coreLocation.getBlockZ())
        };

        notifyTrusted(region, "region_destroyed_chat", plugin.getTunables().raidDestroyed(), placeholders);
        dispatchWebhooks(region.getId() + "_destroyed", "region_destroyed_raw", placeholders);
    }

    public void sendIntruderAlert(Region region, String intruderName) {
        String[] placeholders = {"%type%", region.getTypeId(), "%intruder%", intruderName};

        notifyTrusted(region, "intruder_alert_chat", plugin.getTunables().intruderAlert(), placeholders);
        dispatchWebhooks(region.getId() + "_intrude", "intruder_alert_raw", placeholders);
    }

    /**
     * Сообщение получает владелец и все управляющие: раньше уведомление уходило
     * только владельцу, и совладельцы о рейде не узнавали.
     */
    private void notifyTrusted(Region region, String messageKey, SoundSetting sound, String... placeholders) {
        notifyPlayer(region.getOwnerId(), messageKey, sound, placeholders);

        region.getMembers().stream()
                .filter(member -> member.trust().atLeast(org.qweyns.qweprotectstones.regions.TrustLevel.MANAGER))
                .forEach(member -> notifyPlayer(member.uuid(), messageKey, sound, placeholders));
    }

    private void notifyPlayer(UUID playerId, String messageKey, SoundSetting sound, String... placeholders) {
        if (playerId == null) return;

        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        player.sendMessage(plugin.getLanguageManager().getMessage(messageKey, placeholders));
        sound.playTo(player);
    }

    private void dispatchWebhooks(String rateLimitKey, String messageKey, String... placeholders) {
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        String discordUrl = cfg.getString("notifications.discord_webhook", "");
        String tgToken = cfg.getString("notifications.telegram_bot_token", "");
        String tgChatId = cfg.getString("notifications.telegram_chat_id", "");

        boolean discordEnabled = discordUrl != null && !discordUrl.isBlank();
        boolean telegramEnabled = tgToken != null && !tgToken.isBlank() && tgChatId != null && !tgChatId.isBlank();
        boolean srvEnabled = cfg.getBoolean("notifications.discordsrv.enable", true)
                && plugin.getDiscordSrvHook() != null && plugin.getDiscordSrvHook().isActive();
        if (!discordEnabled && !telegramEnabled && !srvEnabled) return;

        if (webhookRateLimiter.getIfPresent(rateLimitKey) != null) return;
        webhookRateLimiter.put(rateLimitKey, System.currentTimeMillis());

        // Текст готовим в основном потоке: конфигурация читается из кэша.
        // Для Discord/Telegram — чистый текст: §-коды там не рендерятся.
        String rawMessage = ColorUtil.stripFormatting(
                plugin.getLanguageManager().rawTemplate(messageKey, placeholders)).replace("\n", " ");

        if (discordEnabled) {
            post(discordUrl, "{\"content\":\"" + escapeJson(rawMessage) + "\"}");
        }
        if (telegramEnabled) {
            post("https://api.telegram.org/bot" + tgToken + "/sendMessage",
                    "{\"chat_id\":\"" + escapeJson(tgChatId) + "\",\"text\":\"" + escapeJson(rawMessage) + "\"}");
        }
        if (srvEnabled) {
            String channel = cfg.getString("notifications.discordsrv.channel", "");
            plugin.getDiscordSrvHook().sendMessage(rawMessage, channel);
        }
    }

    /** Без экранирования кавычка в нике ломала JSON, и вебхук молча отваливался. */
    private static String escapeJson(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 16);

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private void post(String url, String jsonPayload) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Некорректный URL вебхука в config.yml: " + url);
            return;
        }

        // sendAsync не блокирует ни основной поток, ни планировщик Bukkit.
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.FINE, "Не удалось отправить уведомление", throwable);
                    return null;
                });
    }
}
