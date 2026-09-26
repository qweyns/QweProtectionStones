package org.qweyns.qweprotectstones.menus;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
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

        executeReserved(player, commands, region);
    }

    private void executeReserved(Player player, List<String> commands, Region region) {
        Payments taken = new Payments();
        String current = "preflight";
        try {
            // Все подстановки/суммы проверяются до первого списания. После результата
            // новые списания запрещены: произвольная console-команда необратима.
            java.util.List<String> resolved = new java.util.ArrayList<>();
            boolean resultSeen = false;
            int results = 0;
            double moneyTotal = 0, pointsTotal = 0, expTotal = 0;
            boolean paid = false;
            for (String raw : commands) {
                String cmd = placeholders.apply(player, raw, region, null);
                resolved.add(cmd);
                String payment = cmd.startsWith(TAKE_MONEY) ? TAKE_MONEY : cmd.startsWith(TAKE_POINTS) ? TAKE_POINTS
                        : cmd.startsWith(TAKE_EXP) ? TAKE_EXP : null;
                if (payment != null) {
                    Double amount = parseAmount(cmd.substring(payment.length()));
                    if (amount == null || resultSeen) throw new IllegalArgumentException("Оплата должна предшествовать выдаче");
                    paid = true;
                    if (payment.equals(TAKE_MONEY)) moneyTotal += amount;
                    else if (payment.equals(TAKE_POINTS)) pointsTotal += Math.ceil(amount);
                    else expTotal += Math.ceil(amount);
                }
                if (isResult(cmd)) { resultSeen = true; results++; }
            }
            if (!Double.isFinite(moneyTotal) || pointsTotal > Integer.MAX_VALUE || expTotal > Integer.MAX_VALUE
                    || paid && results != 1) throw new IllegalArgumentException("Платная цепочка должна содержать ровно одну выдачу");
            boolean grantEffect = resolved.stream().anyMatch(cmd -> cmd.startsWith(ADD_EFFECT)
                    || java.util.Arrays.stream(ADD_EFFECT_LEGACY).anyMatch(cmd::startsWith));
            try (Region.Operation operation = region != null && grantEffect ? region.tryOperation() : null) {
                if (region != null && (plugin.getRegionManager().getById(region.getId()) != region
                        || grantEffect && operation == null)) return;
                // Произвольная команда сама управляет своим регионом: не держим резервацию
                // вокруг [player]/[console], иначе легитимный /ps delete не мог бы выполниться.
                for (String cmd : resolved) {
                    current = cmd;
                    if (!run(player, cmd, region, taken)) { refund(player, taken, cmd); return; }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка действия меню: " + current, e);
            refund(player, taken, current);
        }
    }

    private static boolean isResult(String cmd) {
        if (cmd.startsWith(PLAYER) || cmd.startsWith(CONSOLE) || cmd.startsWith(CONNECT) || cmd.startsWith(ADD_EFFECT)) return true;
        for (String legacy : ADD_EFFECT_LEGACY) if (cmd.startsWith(legacy)) return true;
        return false;
    }

    private boolean run(Player player, String cmd, Region region, Payments taken) {
        if (cmd.startsWith(TAKE_MONEY)) {
            Double amount = parseAmount(cmd.substring(TAKE_MONEY.length()));
            if (amount == null) return false;
            if (amount <= 0) return true;

            if (!plugin.getVaultHook().takeMoney(player, amount)) return paymentFailed(player, plugin.getVaultHook().isEnabled());
            taken.addMoney(amount);
            return true;
        }
        if (cmd.startsWith(TAKE_POINTS)) {
            Double amount = parseAmount(cmd.substring(TAKE_POINTS.length()));
            if (amount == null) return false;
            if (amount <= 0) return true;

            int cost = Payments.ceilCost(amount);
            if (!plugin.getPlayerPointsHook().takePoints(player, cost)) return paymentFailed(player, plugin.getPlayerPointsHook().isEnabled());
            taken.addPoints(cost);
            return true;
        }
        if (cmd.startsWith(TAKE_EXP)) {
            Double amount = parseAmount(cmd.substring(TAKE_EXP.length()));
            if (amount == null) return false;
            if (amount <= 0) return true;

            int cost = Payments.ceilCost(amount);
            if (player.getLevel() < cost) return paymentFailed(player, true);
            player.setLevel(player.getLevel() - cost);
            taken.addExp(cost);
            return true;
        }

        return runSimple(player, cmd, region, taken);
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
            if (!Double.isFinite(value) || value < 0) {
                plugin.getLogger().warning("Недопустимая сумма в действии меню: " + raw);
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Некорректное число в действии меню: " + raw);
            return null;
        }
    }

    private void refund(Player player, Payments taken, String failedCmd) {
        if (!taken.any()) return;
        if (taken.committed()) {
            plugin.getLogger().warning("Результат уже выдан/команда запущена; автоматического возврата нет: " + failedCmd);
            return;
        }

        boolean refunded = true;
        if (taken.money() > 0) refunded &= plugin.getVaultHook().giveMoney(player, taken.money());
        if (taken.points() > 0) refunded &= plugin.getPlayerPointsHook().givePoints(player, taken.points());
        if (!refunded) plugin.getLogger().severe("Возврат меню не подтверждён для " + player.getUniqueId()
                + "; требуется ручная компенсация денег/очков");
        if (taken.exp() > 0) player.giveExpLevels(taken.exp());
        plugin.getLogger().warning("Действие меню не удалось ('" + failedCmd + "') — запрошен возврат: "
                + taken.money() + " денег, " + taken.points() + " очков, " + taken.exp() + " уровней опыта.");
    }

    private boolean runSimple(Player player, String cmd, Region region, Payments taken) {
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
            taken.commit();
            player.performCommand(cmd.substring(PLAYER.length()));
        } else if (cmd.startsWith(CONSOLE)) {
            taken.commit();
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.substring(CONSOLE.length()));
        } else if (cmd.startsWith(SOUND)) {
            playSound(player, cmd.substring(SOUND.length()).trim());
        } else if (cmd.startsWith(CONNECT)) {
            taken.commit();
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(cmd.substring(CONNECT.length()).trim());
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        } else if (cmd.startsWith(ADD_EFFECT)) {
            return addEffect(player, region, cmd.substring(ADD_EFFECT.length()).trim(), taken);
        } else {
            for (String legacy : ADD_EFFECT_LEGACY) {
                if (!cmd.startsWith(legacy)) continue;

                return addEffect(player, region, cmd.substring(legacy.length()).trim(), taken);
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
        // громкость и тон идут через двоеточие после имени
        Sound sound = SoundSetting.resolveOnce(plugin.getLogger(), soundName.split(":")[0], "в меню");
        if (sound == null) return;
        SoundSetting.parse(soundName, new SoundSetting(sound, 1f, 1f)).playTo(player);
    }

    // цену задаёт само меню ([takemoney] и т.п. выше по списку) —
    // здесь только проверки и выдача, дважды не списываем
    private boolean addEffect(Player player, Region region, String argument, Payments taken) {
        if (region == null || argument.isEmpty()) return false;

        // эффекты — настройка привата, покупка доступна управляющим
        if (!plugin.getProtectionService().canManage(player, region)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("no_region_access",
                    "%level%", plugin.getLanguageManager().rawTemplate("trust_manager")));
            return false;
        }

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

        // Точка фиксации — сама выдача; последующие эффекты/сообщения не делают её бесплатной.
        region.replaceEffect(effectName, Math.min(255, amplifier));
        taken.commit();
        plugin.getRegionStorage().saveNow(region);
        plugin.getEffectManager().refreshPlayers();
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
