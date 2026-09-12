package org.qweyns.qweprotectstones.menus;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.features.effect.EffectManager;
import org.qweyns.qweprotectstones.config.SoundSetting;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.event.RegionEvents;
import org.qweyns.qweprotectstones.utils.ColorUtil;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public class MenuActions {

    private static final String CLOSE = "[close]";
    private static final String REFRESH = "[refresh]";
    private static final String OPEN = "[openguimenu] ";
    private static final String MESSAGE = "[message] ";
    private static final String PLAYER = "[player] ";
    private static final String CONSOLE = "[console] ";
    private static final String SOUND = "[sound] ";
    private static final String CONNECT = "[connect] ";
    private static final String TAKE_MONEY = "[takemoney] ";
    private static final String TAKE_EXP = "[takeexp] ";
    private static final String TAKE_POINTS = "[takepoints] ";
    private static final String ADD_EFFECT = "[region_add_effect] ";

    private static final String[] ADD_EFFECT_LEGACY = {"[claim_add_effect] ", "[ps_add_effect] "};

    private final QweProtectStones plugin;
    private final MenuPlaceholders placeholders;
    private final MenuRequirements requirements;

    public MenuActions(QweProtectStones plugin, MenuPlaceholders placeholders, MenuRequirements requirements) {
        this.plugin = plugin;
        this.placeholders = placeholders;
        this.requirements = requirements;
    }

    public void execute(Player player, List<String> commands, Region region) {
        if (commands == null || commands.isEmpty()) return;

        // списанное в цепочке возвращается, если дальше что-то не удалось
        Payments taken = new Payments();
        for (String raw : commands) {
            String cmd = placeholders.apply(player, raw, region, null);
            try {
                if (!run(player, cmd, region, taken)) {
                    refund(player, taken, cmd);
                    return;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка выполнения действия меню: " + cmd, e);
                refund(player, taken, cmd);
                return;
            }
        }
    }

    private boolean run(Player player, String cmd, Region region, Payments taken) {
        if (cmd.startsWith(TAKE_MONEY)) {
            Double amount = parseAmount(cmd.substring(TAKE_MONEY.length()));
            if (amount == null) return false;
            if (amount <= 0) return true;

            if (!plugin.getVaultHook().takeMoney(player, amount)) return paymentFailed(player, plugin.getVaultHook().isEnabled());
            taken.money += amount;
            return true;
        }
        if (cmd.startsWith(TAKE_POINTS)) {
            Double amount = parseAmount(cmd.substring(TAKE_POINTS.length()));
            if (amount == null) return false;
            if (amount <= 0) return true;

            int cost = (int) Math.ceil(amount);
            if (!plugin.getPlayerPointsHook().takePoints(player, cost)) return paymentFailed(player, plugin.getPlayerPointsHook().isEnabled());
            taken.points += cost;
            return true;
        }
        if (cmd.startsWith(TAKE_EXP)) {
            Double amount = parseAmount(cmd.substring(TAKE_EXP.length()));
            if (amount == null) return false;
            if (amount <= 0) return true;

            int cost = (int) Math.ceil(amount);
            if (player.getLevel() < cost) return paymentFailed(player, true);
            player.setLevel(player.getLevel() - cost);
            taken.exp += cost;
            return true;
        }

        return runSimple(player, cmd, region);
    }

    private boolean paymentFailed(Player player, boolean hookEnabled) {
        if (!hookEnabled) {
            plugin.getLogger().warning("Меню требует оплату, но нужный экономический плагин не подключён — покупка отменена.");
        }
        player.sendMessage(plugin.getLanguageManager().getMessage("purchase_failed"));
        return false;
    }

    /** null = сумма не разбирается; ноль и меньше ничего не списывают. */
    private Double parseAmount(String raw) {
        try {
            double value = Double.parseDouble(raw.trim());
            if (value < 0) {
                plugin.getLogger().warning("Отрицательная сумма в действии меню: " + raw);
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Некорректное число в действии меню: " + raw);
            return null;
        }
    }

    /** Сколько списали в текущей цепочке — для возврата при сбое дальше по списку. */
    private static final class Payments {
        double money;
        int points;
        int exp;

        boolean any() {
            return money > 0 || points > 0 || exp > 0;
        }
    }

    private void refund(Player player, Payments taken, String failedCmd) {
        if (!taken.any()) return;

        if (taken.money > 0) plugin.getVaultHook().giveMoney(player, taken.money);
        if (taken.points > 0) plugin.getPlayerPointsHook().givePoints(player, taken.points);
        if (taken.exp > 0) player.giveExpLevels(taken.exp);
        plugin.getLogger().warning("Действие меню не удалось ('" + failedCmd + "') — списанное возвращено: "
                + taken.money + " денег, " + taken.points + " очков, " + taken.exp + " уровней опыта.");
    }

    private boolean runSimple(Player player, String cmd, Region region) {
        if (cmd.startsWith(CLOSE)) {
            // закрытие изнутри клика рассинхронизирует клиент — следующим тиком
            plugin.getSchedulers().runAtEntity(player, player::closeInventory);
        } else if (cmd.startsWith(OPEN) || cmd.startsWith(REFRESH)) {
            String targetMenu = cmd.startsWith(REFRESH) ? null : cmd.substring(OPEN.length()).trim();
            // на Folia инвентарь открывает только поток-владелец игрока
            plugin.getSchedulers().runAtEntity(player, () -> reopenOrRefresh(player, region, targetMenu));
        } else if (cmd.startsWith(MESSAGE)) {
            player.sendMessage(ColorUtil.formatComponent(cmd.substring(MESSAGE.length())));
        } else if (cmd.startsWith(PLAYER)) {
            player.performCommand(cmd.substring(PLAYER.length()));
        } else if (cmd.startsWith(CONSOLE)) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.substring(CONSOLE.length()));
        } else if (cmd.startsWith(SOUND)) {
            playSound(player, cmd.substring(SOUND.length()).trim());
        } else if (cmd.startsWith(CONNECT)) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(cmd.substring(CONNECT.length()).trim());
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        } else if (cmd.startsWith(ADD_EFFECT)) {
            return addEffect(player, region, cmd.substring(ADD_EFFECT.length()).trim());
        } else {
            for (String legacy : ADD_EFFECT_LEGACY) {
                if (!cmd.startsWith(legacy)) continue;

                return addEffect(player, region, cmd.substring(legacy.length()).trim());
            }
        }
        return true;
    }

    private void reopenOrRefresh(Player player, Region region, String targetMenu) {
        Inventory topInv = player.getOpenInventory().getTopInventory();
        // игрок успел закрыть меню — заново не открываем
        if (!(topInv.getHolder() instanceof MenuHolder holder)) return;

        if (targetMenu == null || holder.menuName.equals(targetMenu)) {
            plugin.getMenuManager().render(player, holder, true);
            return;
        }
        plugin.getMenuManager().openMenu(player, targetMenu, region);
    }

    private void playSound(Player player, String soundName) {
        SoundSetting sound = SoundSetting.parse(soundName, SoundSetting.NONE);
        if (!sound.isEnabled()) {
            plugin.getLogger().warning("Неизвестный звук в меню: " + soundName);
            return;
        }
        sound.playTo(player);
    }

    // цену задаёт само меню ([takemoney] и т.п. выше по списку) —
    // здесь только проверки и выдача, дважды не списываем
    private boolean addEffect(Player player, Region region, String argument) {
        if (region == null || argument.isEmpty()) return false;

        String[] parts = argument.split(":");
        String effectName = parts[0].trim();
        if (effectName.isEmpty()) return false;

        if (!requirements.isEffectAllowed(region, effectName)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("effect_not_allowed", "%effect%", effectName));
            return false;
        }

        int amplifier = 0;
        if (parts.length > 1) {
            try {
                amplifier = Math.max(0, Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Некорректный уровень эффекта в меню: " + argument);
            }
        }

        // чужие плагины могут наложить вето
        if (RegionEvents.fireEffectPurchase(region, player, effectName.toUpperCase(Locale.ROOT), amplifier)) return false;

        // имя эффекта до списания, за опечатку в конфиге не платят

        if (!EffectManager.isKnownEffect(effectName)) {
            plugin.getLogger().warning("Магазин эффектов: неизвестный эффект '" + effectName + "' (проверьте menus/effects.yml)");
            player.sendMessage(plugin.getLanguageManager().getMessage("effect_unknown", "%effect%", effectName));
            return false;
        }

        plugin.getEffectManager().addCustomEffect(region, effectName, amplifier);
        player.sendMessage(plugin.getLanguageManager().getMessage("effect_bought",
                "%effect%", describeEffect(effectName, amplifier)));
        return true;
    }

    private String describeEffect(String effectName, int amplifier) {
        String translation;

        if (effectName.equalsIgnoreCase("ALERTS")) {
            translation = plugin.getLanguageManager().rawTemplate("effect_alerts");
        } else if (effectName.equalsIgnoreCase("EXP_BOOST")) {
            translation = plugin.getLanguageManager().rawTemplate("effect_exp_boost");
        } else {
            var potionType = plugin.getEffectManager().potionType(effectName);
            translation = potionType != null ? "<translate:" + potionType.translationKey() + ">" : effectName;
        }
        return amplifier > 0 ? translation + MenuManager.getRomanNumeral(amplifier) : translation;
    }
}
