package org.qweyns.qweprotectstones.menus;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MenuPlaceholders {

    private static final Pattern EFFECT_LEVEL_PATTERN = Pattern.compile("(?i)%effect_level_([a-zA-Z_]+)%");

    private final QweProtectStones plugin;

    public MenuPlaceholders(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public static boolean isDynamic(String text) {
        return text != null && text.indexOf('%') >= 0;
    }

    public String apply(Player player, String text, Region region, Map<String, String> extra) {
        if (text == null) return "";
        if (text.indexOf('%') < 0) return text;

        if (extra != null) {
            for (Map.Entry<String, String> entry : extra.entrySet()) {
                text = text.replace(entry.getKey(), entry.getValue());
            }
        }

        if (region != null) text = applyRegion(text, region);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            text = PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    private String applyRegion(String text, Region region) {
        text = text.replace("%region_id%", region.getShortId())
                .replace("%owner%", region.getOwnerName())
                .replace("%name%", region.getLabel())
                .replace("%members%", String.valueOf(region.getMemberCount()))
                .replace("%durability%", String.valueOf(region.getDurability()))
                .replace("%max_durability%", String.valueOf(region.getMaxDurability()));

        if (text.contains("%penalty%")) {
            boolean penalised = plugin.getPenaltyManager().hasPenalty(region.getId());
            text = text.replace("%penalty%", penalised
                    ? String.valueOf(plugin.getPenaltyManager().getPenaltyMultiplier())
                    : plugin.getLanguageManager().rawTemplate("no_penalty"));
        }

        if (text.contains("%siege%")) {
            text = text.replace("%siege%", plugin.getLanguageManager()
                    .rawTemplate(plugin.isUnderSiege(region) ? "siege_active" : "siege_calm"));
        }

        if (text.indexOf('%') >= 0) {
            Matcher matcher = EFFECT_LEVEL_PATTERN.matcher(text);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                int level = effectLevel(region, matcher.group(1));
                matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(level)));
            }
            matcher.appendTail(sb);
            text = sb.toString();
        }
        return text;
    }

    public int effectLevel(Region region, String effectName) {
        if (effectName == null || region == null) return 0;

        String needle = effectName.toUpperCase(Locale.ROOT) + ":";
        for (String effect : region.getEffects()) {
            if (!effect.toUpperCase(Locale.ROOT).startsWith(needle)) continue;

            String[] parts = effect.split(":");
            if (parts.length < 2) return 1;
            try {
                return Integer.parseInt(parts[1].trim()) + 1;
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        return 0;
    }
}
