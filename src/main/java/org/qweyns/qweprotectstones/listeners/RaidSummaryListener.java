package org.qweyns.qweprotectstones.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;

import java.util.Comparator;
import java.util.List;

/**
 * Сводка рейдов при входе: если приват атаковали, пока владельца не было
 * в сети, при заходе он узнает об этом — какие приваты, сколько атак и
 * на какой прочности они сейчас.
 *
 * <p>Алерт в реальном времени ({@code notifications}) виден только тем,
 * кто онлайн; эта сводка закрывает вторую половину — «что случилось без
 * меня». Включается в {@code siege.yml} ключом {@code siege.raid_summary}.</p>
 */
public class RaidSummaryListener implements Listener {

    private final QweProtectStones plugin;

    public RaidSummaryListener(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfigManager().getConfig().getBoolean("siege.raid_summary", true)) return;

        Player player = event.getPlayer();

        // Небольшая пауза: экран входа, приветствия сервера — сводка не
        // должна потеряться в первые же секунды чата.
        plugin.getSchedulers().runLater(() -> {
            if (!player.isOnline()) return;
            report(player);
        }, 40L);
    }

    private void report(Player player) {
        List<Region> attacked = plugin.getRegionManager().getRegionsOf(player.getUniqueId()).stream()
                .filter(region -> region.getUnseenAttacks() > 0)
                .sorted(Comparator.comparingLong(Region::getLastAttackAt).reversed())
                .toList();

        if (attacked.isEmpty()) return;

        var lang = plugin.getLanguageManager();
        lang.sendList(player, "raid_summary_header",
                "%command%", plugin.getConfigManager().getCommandName());

        for (Region region : attacked) {
            String attacker = region.getLastAttackerName();
            if (attacker.isBlank()) attacker = lang.getRawMessage("unknown_owner");

            lang.sendList(player, "raid_summary_line",
                    "%command%", plugin.getConfigManager().getCommandName(),
                    "%id%", region.getShortId(),
                    "%attacks%", String.valueOf(region.getUnseenAttacks()),
                    "%durability%", String.valueOf(region.getDurability()),
                    "%max%", String.valueOf(region.getMaxDurability()),
                    "%attacker%", attacker);

            // Сводка показана — при следующем входе не повторяем.
            region.clearUnseenAttacks();
            plugin.getRegionStorage().save(region);
        }
    }
}
