package org.qweyns.qweprotectstones.menus;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Покадровая анимация меню. Кадры компилируются в готовые действия один раз при
 * открытии, поэтому в каждом тике не разбирается YAML.
 */
public class MenuAnimator implements Runnable {

    private final QweProtectStones plugin;
    private final Player player;
    private final FileConfiguration menuCfg;
    private final Region region;
    private final Inventory inv;
    private final MenuHolder holder;

    private final Map<Integer, List<Runnable>> compiledFrames = new HashMap<>();
    private final Map<String, ItemStack> animItemCache = new HashMap<>();
    private final ItemStack[] animLayer;

    private int currentTick = 0;
    private boolean changedThisTick = false;
    private boolean cancelled = false;

    MenuAnimator(QweProtectStones plugin, Player player, FileConfiguration menuCfg,
                 Region region, Inventory inv, MenuHolder holder) {
        this.plugin = plugin;
        this.player = player;
        this.menuCfg = menuCfg;
        this.region = region;
        this.inv = inv;
        this.holder = holder;
        this.animLayer = new ItemStack[inv.getSize()];
        compileAnimations();
    }

    void cancel() {
        cancelled = true;
    }

    boolean isCancelled() {
        return cancelled;
    }

    void refreshBaseLayer(ItemStack[] newBaseLayer) {
        holder.baseLayer = newBaseLayer;
        animItemCache.clear();
        changedThisTick = true;
        inv.setContents(composeDisplay());
    }

    private ItemStack[] composeDisplay() {
        ItemStack[] display = new ItemStack[inv.getSize()];
        ItemStack[] base = holder.baseLayer;

        for (int i = 0; i < display.length; i++) {
            if (animLayer[i] != null) display[i] = animLayer[i];
            else if (base != null && i < base.length) display[i] = base[i];
        }
        return display;
    }

    @Override
    public void run() {
        if (cancelled) return;

        changedThisTick = false;
        List<Runnable> actions = compiledFrames.get(currentTick);
        if (actions != null) actions.forEach(Runnable::run);

        if (changedThisTick || currentTick == 0) inv.setContents(composeDisplay());
        currentTick++;
    }

    private void compileAnimations() {
        List<?> animList = menuCfg.getList("animations.default");
        if (animList == null) return;

        int lastTick = -1;
        for (Object obj : animList) {
            if (!(obj instanceof Map<?, ?> frame)) continue;

            int tick = lastTick + 1;
            Object tickValue = frame.get("tick");
            if (tickValue != null) {
                try {
                    tick = Integer.parseInt(tickValue.toString().trim());
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Некорректный tick в анимации меню: " + tickValue);
                }
            }
            lastTick = tick;

            compileFrame(frame.get("opcodes"), compiledFrames.computeIfAbsent(tick, k -> new ArrayList<>()));
        }
    }

    private void compileFrame(Object opcodes, List<Runnable> tasks) {
        if (!(opcodes instanceof List<?> list)) return;

        for (Object op : list) {
            if (op instanceof String str) {
                compileOpcode(str, tasks);
            } else if (op instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    Object value = entry.getValue();
                    if (value == null) continue;

                    if ((key.equalsIgnoreCase("commands") || key.equalsIgnoreCase("cmd")) && value instanceof List<?> cmds) {
                        for (Object cmd : cmds) compileOpcode("cmd " + cmd, tasks);
                    } else {
                        compileOpcode(key + " " + value, tasks);
                    }
                }
            }
        }
    }

    private void compileOpcode(String op, List<Runnable> tasks) {
        String[] args = op.trim().split("\\s+");
        if (args.length == 0 || args[0].isEmpty()) return;

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "set", "st" -> {
                    boolean isAir = args[1].equalsIgnoreCase("AIR");
                    String itemId = args[1];
                    List<Integer> slots = parseSlots(args[2]);
                    tasks.add(() -> {
                        ItemStack item = isAir ? null : animItem(itemId);
                        for (int slot : slots) if (slot >= 0 && slot < animLayer.length) animLayer[slot] = item;
                        changedThisTick = true;
                    });
                }
                case "remove", "rm" -> {
                    List<Integer> slots = parseSlots(args[1]);
                    tasks.add(() -> {
                        for (int slot : slots) if (slot >= 0 && slot < animLayer.length) animLayer[slot] = null;
                        changedThisTick = true;
                    });
                }
                case "fill", "fl" -> {
                    boolean isAir = args[1].equalsIgnoreCase("AIR");
                    String itemId = args[1];
                    tasks.add(() -> {
                        Arrays.fill(animLayer, isAir ? null : animItem(itemId));
                        changedThisTick = true;
                    });
                }
                case "goto", "gt" -> {
                    int targetTick = Integer.parseInt(args[1]) - 1;
                    tasks.add(() -> currentTick = targetTick);
                }
                case "sound", "snd" -> {
                    String soundName = args[1].toUpperCase(Locale.ROOT).replace('.', '_');
                    float volume = args.length > 2 ? Float.parseFloat(args[2]) : 1f;
                    float pitch = args.length > 3 ? Float.parseFloat(args[3]) : 1f;
                    // Sound.valueOf вызываем при компиляции: опечатка в названии
                    // роняла бы аниматор каждый тик.
                    Sound sound = Sound.valueOf(soundName);
                    tasks.add(() -> player.playSound(player.getLocation(), sound, volume, pitch));
                }
                case "cmd", "commands" -> {
                    String command = op.trim().substring(args[0].length()).trim();
                    tasks.add(() -> plugin.getMenuManager().getActions().execute(player, List.of(command), region));
                }
                default -> plugin.getLogger().warning("Неизвестная команда анимации: " + args[0]);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Некорректная строка анимации меню: '" + op + "' (" + e.getMessage() + ")");
        }
    }

    private ItemStack animItem(String id) {
        return animItemCache.computeIfAbsent(id, key -> {
            ConfigurationSection sec = menuCfg.getConfigurationSection("items." + key);
            if (sec != null) {
                return plugin.getMenuManager().getItemFactory()
                        .build(sec, player, region, Map.of(), holder.menuName + ":anim:" + key);
            }

            Material material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
            return new ItemStack(material != null && material.isItem() ? material : Material.STONE);
        });
    }

    private List<Integer> parseSlots(String str) {
        List<Integer> result = new ArrayList<>();

        for (String part : str.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;

            int dash = part.indexOf('-');
            if (dash > 0) {
                int from = Integer.parseInt(part.substring(0, dash).trim());
                int to = Integer.parseInt(part.substring(dash + 1).trim());
                for (int i = Math.min(from, to); i <= Math.max(from, to); i++) result.add(i);
            } else {
                result.add(Integer.parseInt(part));
            }
        }
        return result;
    }
}
