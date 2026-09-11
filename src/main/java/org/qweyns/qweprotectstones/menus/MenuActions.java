package org.qweyns.qweprotectstones.menus;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.config.SoundSetting;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.event.RegionEvents;
import org.qweyns.qweprotectstones.utils.ColorUtil;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/** Выполнение действий из click_commands: покупки, звуки, переходы между меню. */
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
    /** Прежние имена действия: меню, написанные до переименования, должны работать. */
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

        for (String raw : commands) {
            String cmd = placeholders.apply(player, raw, region, null);
            try {
                // Если оплата не прошла, остальные действия не выполняются.
                if (!run(player, cmd, region)) return;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка выполнения действия меню: " + cmd, e);
            }
        }
    }

    /** @return false, если цепочку действий нужно прервать */
    private boolean run(Player player, String cmd, Region region) {
        if (cmd.startsWith(TAKE_MONEY)) {
            return takePayment(player, plugin.getVaultHook().isEnabled(),
                    plugin.getVaultHook().takeMoney(player, parseDouble(cmd.substring(TAKE_MONEY.length()))));
        }
        if (cmd.startsWith(TAKE_POINTS)) {
            return takePayment(player, plugin.getPlayerPointsHook().isEnabled(),
                    plugin.getPlayerPointsHook().takePoints(player, (int) parseDouble(cmd.substring(TAKE_POINTS.length()))));
        }
        if (cmd.startsWith(TAKE_EXP)) {
            int cost = (int) parseDouble(cmd.substring(TAKE_EXP.length()));
            if (player.getLevel() < cost) return false;

            player.setLevel(player.getLevel() - cost);
            return true;
        }

        runSimple(player, cmd, region);
        return true;
    }

    private boolean takePayment(Player player, boolean hookEnabled, boolean success) {
        if (success) return true;

        if (!hookEnabled) {
            plugin.getLogger().warning("Меню требует оплату, но нужный экономический плагин не подключён — покупка отменена.");
        }
        player.sendMessage(plugin.getLanguageManager().getMessage("purchase_failed"));
        return false;
    }

    private void runSimple(Player player, String cmd, Region region) {
        if (cmd.startsWith(CLOSE)) {
            player.closeInventory();
        } else if (cmd.startsWith(OPEN) || cmd.startsWith(REFRESH)) {
            String targetMenu = cmd.startsWith(REFRESH) ? null : cmd.substring(OPEN.length()).trim();
            plugin.getSchedulers().runNextTick(() -> reopenOrRefresh(player, region, targetMenu));
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
            addEffect(player, region, cmd.substring(ADD_EFFECT.length()).trim());
        } else {
            for (String legacy : ADD_EFFECT_LEGACY) {
                if (!cmd.startsWith(legacy)) continue;

                addEffect(player, region, cmd.substring(legacy.length()).trim());
                break;
            }
        }
    }

    private void reopenOrRefresh(Player player, Region region, String targetMenu) {
        Inventory topInv = player.getOpenInventory().getTopInventory();
        if (topInv.getHolder() instanceof MenuHolder holder
                && (targetMenu == null || holder.menuName.equals(targetMenu))) {
            plugin.getMenuManager().render(player, holder, true);
            return;
        }
        if (targetMenu != null) plugin.getMenuManager().openMenu(player, targetMenu, region);
    }

    /** Принимает и {@code ENTITY_X}, и {@code ENTITY_X:0.5:1.2} — как в config.yml. */
    private void playSound(Player player, String soundName) {
        SoundSetting sound = SoundSetting.parse(soundName, SoundSetting.NONE);
        if (!sound.isEnabled()) {
            plugin.getLogger().warning("Неизвестный звук в меню: " + soundName);
            return;
        }
        sound.playTo(player);
    }

    /** Некорректное число в конфиге меню не должно прерывать остальные действия. */
    private double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Некорректное число в действии меню: " + raw);
            return 0;
        }
    }

    private void addEffect(Player player, Region region, String argument) {
        if (region == null || argument.isEmpty()) return;

        String[] parts = argument.split(":");
        String effectName = parts[0].trim();
        if (effectName.isEmpty()) return;

        // Эффект, не разрешённый типом привата, купить нельзя — даже если меню
        // настроено с ошибкой.
        if (!requirements.isEffectAllowed(region, effectName)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("effect_not_allowed", "%effect%", effectName));
            return;
        }

        int amplifier = 0;
        if (parts.length > 1) {
            try {
                amplifier = Math.max(0, Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Некорректный уровень эффекта в меню: " + argument);
            }
        }

        // Даём другим плагинам шанс наложить вето (налоги, клановые правила).
        if (RegionEvents.fireEffectPurchase(region, player, effectName.toUpperCase(Locale.ROOT), amplifier)) return;

        // Проверяем имя эффекта ДО списания: за опечатку в конфиге меню
        // игрок платить не должен.
        boolean builtin = effectName.equalsIgnoreCase("ALERTS") || effectName.equalsIgnoreCase("EXP_BOOST");
        if (!builtin && plugin.getEffectManager().potionType(effectName) == null) {
            plugin.getLogger().warning("Магазин эффектов: неизвестный эффект '" + effectName + "' (проверьте menus/effects.yml)");
            player.sendMessage(plugin.getLanguageManager().getMessage("effect_unknown", "%effect%", effectName));
            return;
        }

        // Оплата: тип и цена настраиваются в effects.purchase (по умолчанию бесплатно).
        var purchase = plugin.getEffectPurchaseManager();
        switch (purchase.charge(player, effectName, amplifier)) {
            case NO_ECONOMY -> {
                player.sendMessage(plugin.getLanguageManager().getMessage("effect_no_economy"));
                return;
            }
            case NOT_ENOUGH -> {
                player.sendMessage(plugin.getLanguageManager().getMessage("effect_not_enough",
                        "%price%", org.qweyns.qweprotectstones.features.effect.EffectPurchaseManager.format(
                                purchase.price(effectName, amplifier)),
                        "%unit%", purchase.costTypeName()));
                return;
            }
            default -> { /* FREE и SUCCESS: выдаём */ }
        }

        plugin.getEffectManager().addCustomEffect(region, effectName, amplifier);
        player.sendMessage(plugin.getLanguageManager().getMessage("effect_bought",
                "%effect%", describeEffect(effectName, amplifier)));
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
