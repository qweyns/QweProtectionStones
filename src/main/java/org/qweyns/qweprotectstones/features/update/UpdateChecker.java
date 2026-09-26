package org.qweyns.qweprotectstones.features.update;

import org.qweyns.qweprotectstones.config.ConfigValues;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.qweyns.qweprotectstones.QweProtectStones;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Проверка новых версий по GitHub releases: раз в период спрашиваем последний
 * релиз и напоминаем о нём в консоль и операторам на входе.
 */
public class UpdateChecker implements Listener {

    private static final String LATEST_URL = "https://api.github.com/repos/qweyns/QweProtectionStones/releases/latest";
    private static final String RELEASES_PAGE = "https://github.com/qweyns/QweProtectionStones/releases";
    private static final Pattern TAG_NAME = Pattern.compile("\"tag_name\"\s*:\s*\"([^\"]+)\"");

    private final QweProtectStones plugin;
    private final HttpClient http;

    // замеченная новая версия; null — обновлений нет или ещё не проверяли
    private volatile String newerVersion;

    private org.qweyns.qweprotectstones.scheduler.Schedulers.Task checkTask;
    private boolean eventsRegistered;

    public UpdateChecker(QweProtectStones plugin) {
        this.plugin = plugin;
        http = HttpClient.newBuilder().connectTimeout(timeout()).build();
    }

    private Duration timeout() {
        return Duration.ofSeconds(ConfigValues.boundedLong(plugin.getConfigManager().getConfig(),
                "updates.http-timeout-seconds", 10L, 1L, 120L));
    }

    public void start() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        if (!plugin.getConfigManager().getConfig().getBoolean("updates.enabled", true)) return;

        long hours = ConfigValues.boundedLong(plugin.getConfigManager().getConfig(), "updates.period-hours", 12L, 1L, 8760L);
        long delay = ConfigValues.boundedLong(plugin.getConfigManager().getConfig(), "updates.initial-delay-ticks", 100L, 1L, 72000L);
        checkTask = plugin.getSchedulers().runTimer(this::check, delay, hours * 3600L * 20L);
        if (!eventsRegistered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            eventsRegistered = true;
        }
    }

    /** При выключении плагина — иначе селектор-поток переживает /reload. */
    public void close() {
        if (checkTask != null) checkTask.cancel();
        http.shutdownNow();
    }

    private void check() {
        plugin.getSchedulers().runAsync(this::request);
    }

    private void request() {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(LATEST_URL))
                    .timeout(timeout())
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            return;
        }

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, throwable) -> {
                    if (throwable != null || response.statusCode() != 200) return;
                    Matcher matcher = TAG_NAME.matcher(response.body());
                    if (!matcher.find()) return;

                    String tag = matcher.group(1);
                    if (isNewer(tag, plugin.getPluginMeta().getVersion())) {
                        newerVersion = tag;
                        plugin.getLogger().warning("Доступна новая версия " + tag
                                + " (у вас " + plugin.getPluginMeta().getVersion() + "): " + RELEASES_PAGE);
                    }
                });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String tag = newerVersion;
        if (tag == null) return;
        if (!event.getPlayer().isOp()
                && !event.getPlayer().hasPermission("qweprotectstones.update-notify")) return;

        plugin.getLanguageManager().sendList(event.getPlayer(), "update_available",
                "%current%", plugin.getPluginMeta().getVersion(),
                "%latest%", tag,
                "%url%", RELEASES_PAGE);
    }

    // сравниваем числовые части по очереди, суффиксы после нечисловой части игнорируем
    static boolean isNewer(String latest, String current) {
        int[] left = parts(latest);
        int[] right = parts(current);
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int a = i < left.length ? left[i] : 0;
            int b = i < right.length ? right[i] : 0;
            if (a != b) return a > b;
        }
        return false;
    }

    static int[] parts(String version) {
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        String[] raw = v.split("[.-]");
        int[] numbers = new int[raw.length];
        int count = 0;
        for (String part : raw) {
            try {
                numbers[count++] = Integer.parseInt(part.trim());
            } catch (NumberFormatException e) {
                break;
            }
        }
        return Arrays.copyOf(numbers, count);
    }
}
