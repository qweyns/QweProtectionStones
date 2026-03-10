package org.qweyns.pshologramm.menus;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.espi.protectionstones.PSRegion;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.models.RegionData;
import org.qweyns.pshologramm.utils.ColorUtil;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MenuManager implements Listener {
    private final PSHologramm plugin;
    private final Map<String, FileConfiguration> menus = new HashMap<>();

    private final Map<UUID, Long> clickCooldowns = new HashMap<>();

    public static String getRomanNumeral(int amplifier) {
        if (amplifier <= 0) return "";
        return switch (amplifier) {
            case 1 -> " II";
            case 2 -> " III";
            case 3 -> " IV";
            case 4 -> " V";
            case 5 -> " VI";
            case 6 -> " VII";
            case 7 -> " VIII";
            case 8 -> " IX";
            case 9 -> " X";
            default -> " " + (amplifier + 1);
        };
    }

    public MenuManager(PSHologramm plugin) {
        this.plugin = plugin;
        loadMenus();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
    }

    public void loadMenus() {
        menus.clear();
        File folder = new File(plugin.getDataFolder(), "menus");
        if (!folder.exists()) {
            folder.mkdirs();
            plugin.saveResource("menus/main.yml", false);
            plugin.saveResource("menus/effects.yml", false);
            plugin.saveResource("menus/upgrade.yml", false);
        }
        for (File f : folder.listFiles()) {
            if (f.getName().endsWith(".yml")) {
                menus.put(f.getName().replace(".yml", ""), YamlConfiguration.loadConfiguration(f));
            }
        }
    }

    private String applyPlaceholders(Player p, String text, PSRegion region, Map<String, String> extra) {
        if (text == null) return "";
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                text = text.replace(e.getKey(), e.getValue());
            }
        }

        if (region != null) {
            text = text.replace("%region_id%", region.getId());
            if (text.contains("%penalty%")) {
                int pen = plugin.getPenaltyManager().hasPenalty(region.getId()) ? plugin.getPenaltyManager().getPenaltyMultiplier() : 0;
                text = text.replace("%penalty%", pen > 0 ? String.valueOf(pen) : plugin.getLanguageManager().getRawMessage("no_penalty"));
            }
            Matcher m = Pattern.compile("(?i)%effect_level_([a-zA-Z_]+)%").matcher(text);
            while (m.find()) {
                String effName = m.group(1).toUpperCase();
                int level = getRegionEffectLevel(region, effName);
                text = text.replace(m.group(), String.valueOf(level));
            }
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            text = PlaceholderAPI.setPlaceholders(p, text);
        }
        return text;
    }

    private int getRegionEffectLevel(PSRegion region, String effectName) {
        RegionData rd = plugin.getStorageManager().getRegion(region.getId());
        if (rd == null || rd.getEffects() == null) return 0;
        for (String eff : rd.getEffects()) {
            if (eff.toUpperCase().startsWith(effectName + ":")) {
                String[] parts = eff.split(":");
                try { return parts.length > 1 ? Integer.parseInt(parts[1]) + 1 : 1; } catch (Exception e) { return 1; }
            }
        }
        return 0;
    }

    public void openMenu(Player player, String menuName, PSRegion region) {
        FileConfiguration menuCfg = menus.get(menuName);
        if (menuCfg == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("menu_not_found", "%menu%", menuName));
            return;
        }

        Component title = ColorUtil.formatComponent(applyPlaceholders(player, menuCfg.getString("menu_title", "Меню"), region, null));
        CustomHolder holder = new CustomHolder(menuName, region);
        Inventory inv = Bukkit.createInventory(holder, menuCfg.getInt("size", 27), title);

        renderMenuContent(player, region, menuName, inv, holder);

        int interval = menuCfg.getInt("update_interval", 0);
        if (interval > 0) {
            holder.updateTask = new BukkitRunnable() {
                @Override public void run() { renderMenuContent(player, region, menuName, inv, holder); }
            }.runTaskTimer(plugin, interval, interval);
        }

        if (menuCfg.contains("animations.default")) {
            holder.animator = new MenuAnimator(player, menuCfg, region, inv, holder);
            holder.animator.runTaskTimer(plugin, 1L, 1L);
        }

        player.openInventory(inv);
    }

    private void renderMenuContent(Player player, PSRegion region, String menuName, Inventory inv, CustomHolder holder) {
        ItemStack[] contents = new ItemStack[inv.getSize()];
        FileConfiguration menuCfg = menus.get(menuName);
        if (menuCfg == null) return;

        ConfigurationSection items = menuCfg.getConfigurationSection("items");
        if (items != null) {
            List<String> keys = new ArrayList<>(items.getKeys(false));
            keys.sort(Comparator.comparingInt(k -> items.getInt(k + ".priority", 100)));

            Set<Integer> filled = new HashSet<>();
            Map<String, String> extra = getDynamicPlaceholders(menuName, region);

            for (String key : keys) {
                ConfigurationSection cfg = items.getConfigurationSection(key);
                if (cfg.contains("view_requirement.requirements") && !checkRequirements(cfg.getConfigurationSection("view_requirement.requirements"), player, region)) continue;

                List<Integer> slots = new ArrayList<>();
                if (cfg.contains("slots")) slots.addAll(cfg.getIntegerList("slots"));
                else if (cfg.contains("slot")) slots.add(cfg.getInt("slot"));

                if (slots.isEmpty()) continue;

                boolean needsBuild = false;
                for (int s : slots) { if (s < contents.length && !filled.contains(s)) { needsBuild = true; break; } }

                if (needsBuild) {
                    ItemStack item = buildItem(cfg, player, region, extra);
                    if (item != null) {
                        for (int s : slots) {
                            if (s < contents.length && !filled.contains(s)) { contents[s] = item; filled.add(s); }
                        }
                    }
                }
            }
        }

        if (menuName.equals("upgrade")) {
            RegionData rd = plugin.getStorageManager().getRegion(region.getId());
            int curDur = rd != null ? rd.getDurability() : 1;
            int maxDur = plugin.getConfigManager().getMaxDurability(region.getType());

            List<Integer> slots = menuCfg.getIntegerList("upgrade_slots");
            ConfigurationSection tpl = menuCfg.getConfigurationSection("upgrade_item_template");
            ConfigurationSection maxTpl = menuCfg.getConfigurationSection("max_upgrade_item_template");

            if (slots != null && tpl != null) {
                Material upgradeMat = plugin.getConfigManager().getUpgradeItem();
                for (int i = 0; i < slots.size(); i++) {
                    int targetLevel = curDur + (i + 1);
                    if (targetLevel > maxDur) {
                        if (maxTpl != null) {
                            Map<String, String> extra = getDynamicPlaceholders(menuName, region);
                            ItemStack maxItem = buildItem(maxTpl, player, region, extra);
                            if (maxItem != null && slots.get(i) < contents.length) contents[slots.get(i)] = maxItem;
                        }
                        continue;
                    }

                    long cost = calculateUpgradeCost(curDur, targetLevel, region.getId());
                    Map<String, String> extra = getDynamicPlaceholders(menuName, region);
                    extra.put("%level%", String.valueOf(targetLevel));
                    extra.put("%cost%", String.valueOf(cost));
                    extra.put("%item%", "<translate:" + upgradeMat.translationKey() + ">");

                    ItemStack upg = buildItem(tpl, player, region, extra);
                    if (upg != null) {
                        upg.setType(upgradeMat);
                        upg.setAmount(Math.min(targetLevel, 64));
                        if (slots.get(i) < contents.length) contents[slots.get(i)] = upg;
                    }
                }
            }
        }

        holder.baseLayer = contents;

        if (holder.animator == null) {
            inv.setContents(contents);
        } else {
            holder.animator.refreshBaseLayer(contents);
        }
    }

    private Map<String, String> getDynamicPlaceholders(String menuName, PSRegion region) {
        Map<String, String> map = new HashMap<>();
        if (menuName.equals("upgrade")) {
            RegionData rd = plugin.getStorageManager().getRegion(region.getId());
            map.put("%current%", String.valueOf(rd != null ? rd.getDurability() : 1));
            map.put("%max%", String.valueOf(plugin.getConfigManager().getMaxDurability(region.getType())));
        }
        return map;
    }

    private ItemStack buildItem(ConfigurationSection cfg, Player player, PSRegion region, Map<String, String> placeholders) {
        String matStr = cfg.getString("material", "STONE");
        if (matStr.equalsIgnoreCase("%region_material%") && region != null) matStr = region.getProtectBlock().getType().name();

        ItemStack item;
        if (matStr.startsWith("base64-")) {
            item = getBase64Skull(matStr.substring(7));
        } else if (matStr.startsWith("head-")) {
            item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(matStr.substring(5).replace("%player_name%", player.getName())));
            item.setItemMeta(meta);
        } else {
            Material m = Material.matchMaterial(matStr);
            item = new ItemStack(m != null ? m : Material.STONE);
        }

        item.setAmount(cfg.getInt("amount", 1));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (cfg.contains("display_name")) {
            meta.displayName(ColorUtil.formatComponent(applyPlaceholders(player, cfg.getString("display_name"), region, placeholders)));
        }

        if (cfg.contains("lore")) {
            List<Component> lore = new ArrayList<>();
            for (String line : cfg.getStringList("lore")) {
                lore.add(ColorUtil.formatComponent(applyPlaceholders(player, line, region, placeholders)));
            }
            meta.lore(lore);
        }

        if (cfg.getBoolean("unbreakable", false)) meta.setUnbreakable(true);
        if (cfg.contains("custom_model_data")) meta.setCustomModelData(cfg.getInt("custom_model_data"));

        if (cfg.contains("enchantments")) {
            for (String encStr : cfg.getStringList("enchantments")) {
                String[] split = encStr.split(":");
                try {
                    NamespacedKey key = NamespacedKey.minecraft(split[0].toLowerCase());
                    Enchantment enc = Registry.ENCHANTMENT.get(key);
                    if (enc != null) meta.addEnchant(enc, split.length > 1 ? Integer.parseInt(split[1]) : 1, true);
                } catch (Exception ignored) {}
            }
        }

        if (cfg.getBoolean("hide_attributes", false)) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.setAttributeModifiers(com.google.common.collect.ArrayListMultimap.create());
        }
        if (cfg.getBoolean("hide_enchantments", false)) meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        if (cfg.getBoolean("hide_unbreakable", false)) meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        if (cfg.getBoolean("hide_destroys", false)) meta.addItemFlags(ItemFlag.HIDE_DESTROYS);
        if (cfg.getBoolean("hide_placed_on", false)) meta.addItemFlags(ItemFlag.HIDE_PLACED_ON);
        if (cfg.getBoolean("hide_potion_effects", false)) {
            try { meta.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP")); } catch (Exception ignored) {}
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack getBase64Skull(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", base64));
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);
        }
        return head;
    }

    private long calculateUpgradeCost(int current, int target, String regionId) {
        long total = 0;
        long multiplier = plugin.getConfigManager().getUpgradeMultiplier();
        long tax = plugin.getConfigManager().getConfig().getInt("settings.upgrade_tax", 0);
        for (int i = current + 1; i <= target; i++) total += (i * multiplier) + tax;
        if (plugin.getPenaltyManager().hasPenalty(regionId)) total *= plugin.getPenaltyManager().getPenaltyMultiplier();
        return total;
    }

    private boolean checkRequirements(ConfigurationSection reqs, Player player, PSRegion region) {
        for (String key : reqs.getKeys(false)) {
            String type = reqs.getString(key + ".type");

            if (type.equals("has money") && !plugin.getVaultHook().hasMoney(player, reqs.getDouble(key + ".amount"))) return false;
            if (type.equals("has points") && !plugin.getPlayerPointsHook().hasPoints(player, reqs.getInt(key + ".amount"))) return false;

            if (type.equals("has exp") && player.getLevel() < reqs.getInt(key + ".amount")) return false;
            if (type.equals("has permission") && !player.hasPermission(reqs.getString(key + ".permission"))) return false;
            if (type.equals("region_durability_enabled") && region != null) {
                if (!plugin.getConfigManager().getConfig().getBoolean("regions." + region.getType() + ".enable_durability_upgrade", true)) return false;
            }
            if (type.equals("has effect") && region != null && getRegionEffectLevel(region, reqs.getString(key + ".effect_name")) <= 0) return false;
            if (type.equals("does not have effect") && region != null && getRegionEffectLevel(region, reqs.getString(key + ".effect_name")) > 0) return false;
            if (type.equals("allowed_effect") && region != null && !plugin.getConfigManager().getAllowedEffects(region.getType()).contains(reqs.getString(key + ".effect_name"))) return false;
            if (type.equals("not_allowed_effect") && region != null && plugin.getConfigManager().getAllowedEffects(region.getType()).contains(reqs.getString(key + ".effect_name"))) return false;
        }
        return true;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CustomHolder holder) {
            if (holder.updateTask != null) holder.updateTask.cancel();
            if (holder.animator != null) holder.animator.cancel();
        }
        clickCooldowns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CustomHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CustomHolder holder)) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();

        long now = System.currentTimeMillis();
        if (now - clickCooldowns.getOrDefault(player.getUniqueId(), 0L) < 300L) return;
        clickCooldowns.put(player.getUniqueId(), now);

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        FileConfiguration menuCfg = menus.get(holder.menuName);
        if (menuCfg == null || holder.baseLayer == null || event.getSlot() >= holder.baseLayer.length || holder.baseLayer[event.getSlot()] == null) return;

        if (holder.menuName.equals("upgrade")) {
            List<Integer> upgSlots = menuCfg.getIntegerList("upgrade_slots");
            if (upgSlots != null && upgSlots.contains(event.getSlot())) {
                int slotIndex = upgSlots.indexOf(event.getSlot());
                RegionData rd = plugin.getStorageManager().getRegion(holder.region.getId());
                int curDur = rd != null ? rd.getDurability() : 1;
                int targetLevel = curDur + slotIndex + 1;
                int maxDur = plugin.getConfigManager().getMaxDurability(holder.region.getType());

                if (targetLevel > maxDur) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                long totalCost = calculateUpgradeCost(curDur, targetLevel, holder.region.getId());
                Material upgradeMat = plugin.getConfigManager().getUpgradeItem();
                ItemStack[] storage = player.getInventory().getStorageContents();
                long count = 0;

                for (ItemStack is : storage) {
                    if (is != null && is.getType() == upgradeMat) count += is.getAmount();
                }

                if (count < totalCost) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("upgrade_not_enough_items", "%amount%", String.valueOf(totalCost), "%item%", "<translate:" + upgradeMat.translationKey() + ">"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                long needed = totalCost;
                for (int i = 0; i < storage.length; i++) {
                    ItemStack item = storage[i];
                    if (item != null && item.getType() == upgradeMat) {
                        if (item.getAmount() <= needed) {
                            needed -= item.getAmount();
                            player.getInventory().setItem(i, null);
                        } else {
                            item.setAmount((int) (item.getAmount() - needed));
                            player.getInventory().setItem(i, item);
                            needed = 0;
                        }
                        if (needed <= 0) break;
                    }
                }
                player.updateInventory();

                if (rd != null) {
                    rd.setDurability(targetLevel);
                    plugin.getStorageManager().forceSave(rd);
                }

                String ownerName = rd != null ? rd.getOwner() : "";
                plugin.getHologramManager().createOrUpdateHologram(holder.region.getId(), holder.region.getProtectBlock().getLocation(), holder.region.getType(), ownerName, targetLevel, maxDur);

                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
                player.sendMessage(plugin.getLanguageManager().getMessage("upgrade_success", "%durability%", String.valueOf(targetLevel)));
                renderMenuContent(player, holder.region, holder.menuName, event.getInventory(), holder);
                return;
            }
        }

        ConfigurationSection items = menuCfg.getConfigurationSection("items");
        if (items == null) return;
        List<String> keys = new ArrayList<>(items.getKeys(false));
        keys.sort(Comparator.comparingInt(k -> items.getInt(k + ".priority", 100)));

        for (String key : keys) {
            ConfigurationSection itemCfg = items.getConfigurationSection(key);

            if (itemCfg.contains("view_requirement.requirements") && !checkRequirements(itemCfg.getConfigurationSection("view_requirement.requirements"), player, holder.region)) continue;

            List<Integer> slots = new ArrayList<>();
            if (itemCfg.contains("slots")) slots.addAll(itemCfg.getIntegerList("slots"));
            else if (itemCfg.contains("slot")) slots.add(itemCfg.getInt("slot"));

            if (slots.isEmpty()) continue;

            if (slots.contains(event.getSlot())) {
                if (itemCfg.contains("click_requirement.requirements") && !checkRequirements(itemCfg.getConfigurationSection("click_requirement.requirements"), player, holder.region)) {
                    executeCommands(player, itemCfg.getStringList("click_requirement.deny_commands"), holder.region);
                    return;
                }

                executeCommands(player, itemCfg.contains("left_click_commands") ? itemCfg.getStringList("left_click_commands") : itemCfg.getStringList("click_commands"), holder.region);
                break;
            }
        }
    }

    private void executeCommands(Player player, List<String> commands, PSRegion region) {
        if (commands == null) return;
        for (String cmd : commands) {
            cmd = applyPlaceholders(player, cmd, region, null);
            if (cmd.startsWith("[close]")) player.closeInventory();
            else if (cmd.startsWith("[openguimenu] ") || cmd.startsWith("[refresh]")) {
                String targetMenu = cmd.startsWith("[refresh]") ? null : cmd.substring(14).trim();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Inventory topInv = player.getOpenInventory().getTopInventory();
                    if (topInv != null && topInv.getHolder() instanceof CustomHolder h) {
                        if (targetMenu == null || h.menuName.equals(targetMenu)) {
                            renderMenuContent(player, region, h.menuName, topInv, h);
                            return;
                        }
                    }
                    if (targetMenu != null) openMenu(player, targetMenu, region);
                });
            }
            else if (cmd.startsWith("[message] ")) player.sendMessage(ColorUtil.formatLegacyString(cmd.substring(10)));
            else if (cmd.startsWith("[player] ")) player.performCommand(cmd.substring(9));
            else if (cmd.startsWith("[console] ")) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.substring(10));
            else if (cmd.startsWith("[sound] ")) { try { player.playSound(player.getLocation(), Sound.valueOf(cmd.substring(8)), 1f, 1f); } catch (Exception ignored) {} }
            else if (cmd.startsWith("[connect] ")) { ByteArrayDataOutput out = ByteStreams.newDataOutput(); out.writeUTF("Connect"); out.writeUTF(cmd.substring(10)); player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray()); }

            else if (cmd.startsWith("[takemoney] ")) plugin.getVaultHook().takeMoney(player, Double.parseDouble(cmd.substring(12)));
            else if (cmd.startsWith("[takeexp] ")) player.setLevel(Math.max(0, player.getLevel() - Integer.parseInt(cmd.substring(10))));
            else if (cmd.startsWith("[takepoints] ")) plugin.getPlayerPointsHook().takePoints(player, Integer.parseInt(cmd.substring(13)));

            else if (cmd.startsWith("[ps_add_effect] ")) {
                if (region != null) {
                    String[] split = cmd.substring(16).split(":");
                    int amp = split.length > 1 ? Integer.parseInt(split[1]) : 0;

                    plugin.getEffectManager().addCustomEffect(region.getId(), split[0], amp);

                    String translation;
                    if (split[0].equalsIgnoreCase("ALERTS")) {
                        translation = plugin.getLanguageManager().getRawMessage("effect_alerts");
                    } else if (split[0].equalsIgnoreCase("EXP_BOOST")) {
                        translation = plugin.getLanguageManager().getRawMessage("effect_exp_boost");
                    } else {
                        org.bukkit.potion.PotionEffectType pType = org.bukkit.potion.PotionEffectType.getByName(split[0]);
                        translation = pType != null ? "<translate:" + pType.translationKey() + ">" : split[0];
                    }

                    if (amp > 0) {
                        translation += getRomanNumeral(amp);
                    }

                    player.sendMessage(plugin.getLanguageManager().getMessage("effect_bought", "%effect%", translation));
                }
            }
        }
    }

    private class MenuAnimator extends BukkitRunnable {
        private final Player player;
        private final FileConfiguration menuCfg;
        private final PSRegion region;
        private final Inventory inv;
        private final CustomHolder holder;

        private final Map<Integer, List<Runnable>> compiledFrames = new HashMap<>();
        private final Map<String, ItemStack> animItemCache = new HashMap<>();
        private final ItemStack[] animLayer;
        private int currentTick = 0;
        private boolean changedThisTick = false;

        public MenuAnimator(Player player, FileConfiguration menuCfg, PSRegion region, Inventory inv, CustomHolder holder) {
            this.player = player; this.menuCfg = menuCfg; this.region = region; this.inv = inv; this.holder = holder;
            this.animLayer = new ItemStack[inv.getSize()];
            compileAnimations();
        }

        public void refreshBaseLayer(ItemStack[] newBaseLayer) {
            holder.baseLayer = newBaseLayer;
            animItemCache.clear();
            this.changedThisTick = true;

            ItemStack[] display = new ItemStack[inv.getSize()];
            for (int i = 0; i < display.length; i++) {
                if (animLayer[i] != null) display[i] = animLayer[i];
                else if (holder.baseLayer != null && i < holder.baseLayer.length) display[i] = holder.baseLayer[i];
            }
            inv.setContents(display);
        }

        private void compileAnimations() {
            List<?> animList = menuCfg.getList("animations.default");
            if (animList == null) return;
            int lastTick = -1;
            for (Object obj : animList) {
                if (!(obj instanceof Map)) continue;
                Map<?, ?> map = (Map<?, ?>) obj;
                int tick = map.containsKey("tick") ? Integer.parseInt(map.get("tick").toString()) : lastTick + 1;
                lastTick = tick;

                List<Runnable> tasks = compiledFrames.computeIfAbsent(tick, k -> new ArrayList<>());

                Object ops = map.get("opcodes");
                if (ops instanceof List) {
                    for (Object op : (List<?>) ops) {
                        if (op instanceof String) compileOpcode(op.toString(), tasks);
                        else if (op instanceof Map) {
                            for (Map.Entry<?, ?> entry : ((Map<?, ?>) op).entrySet()) {
                                String key = entry.getKey().toString();
                                if ((key.equalsIgnoreCase("commands") || key.equalsIgnoreCase("cmd")) && entry.getValue() instanceof List) {
                                    for (Object c : (List<?>) entry.getValue()) compileOpcode("cmd " + c.toString(), tasks);
                                } else compileOpcode(key + " " + entry.getValue().toString(), tasks);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void run() {
            changedThisTick = false;
            List<Runnable> actions = compiledFrames.get(currentTick);
            if (actions != null) {
                for (Runnable action : actions) action.run();
            }

            if (changedThisTick || currentTick == 0) {
                ItemStack[] display = new ItemStack[inv.getSize()];
                for (int i = 0; i < display.length; i++) {
                    if (animLayer[i] != null) display[i] = animLayer[i];
                    else if (holder.baseLayer != null && i < holder.baseLayer.length) display[i] = holder.baseLayer[i];
                }
                inv.setContents(display);
            }
            currentTick++;
        }

        private void compileOpcode(String op, List<Runnable> tasks) {
            String[] args = op.split(" ");
            if (args.length == 0) return;
            String cmd = args[0].toLowerCase();
            try {
                switch (cmd) {
                    case "set": case "st":
                        boolean isAir = args[1].equalsIgnoreCase("AIR");
                        List<Integer> sSlots = parseSlots(args[2]);
                        tasks.add(() -> {
                            ItemStack item = isAir ? null : getAnimItem(args[1]);
                            for (int s : sSlots) if (s < animLayer.length) animLayer[s] = item;
                            changedThisTick = true;
                        });
                        break;
                    case "remove": case "rm":
                        List<Integer> rSlots = parseSlots(args[1]);
                        tasks.add(() -> {
                            for (int s : rSlots) if (s < animLayer.length) animLayer[s] = null;
                            changedThisTick = true;
                        });
                        break;
                    case "fill": case "fl":
                        boolean fAir = args[1].equalsIgnoreCase("AIR");
                        tasks.add(() -> {
                            Arrays.fill(animLayer, fAir ? null : getAnimItem(args[1]));
                            changedThisTick = true;
                        });
                        break;
                    case "goto": case "gt":
                        int targetTick = Integer.parseInt(args[1]) - 1;
                        tasks.add(() -> currentTick = targetTick);
                        break;
                    case "sound": case "snd":
                        String sName = args[1].toUpperCase().replace(".", "_");
                        float vol = Float.parseFloat(args[2]); float pitch = Float.parseFloat(args[3]);
                        tasks.add(() -> player.playSound(player.getLocation(), Sound.valueOf(sName), vol, pitch));
                        break;
                    case "cmd": case "commands":
                        String command = op.substring(cmd.length()).trim();
                        tasks.add(() -> executeCommands(player, Collections.singletonList(command), region));
                        break;
                }
            } catch (Exception ignored) {}
        }

        private ItemStack getAnimItem(String id) {
            return animItemCache.computeIfAbsent(id, k -> {
                ConfigurationSection sec = menuCfg.getConfigurationSection("items." + id);
                if (sec != null) return buildItem(sec, player, region, getDynamicPlaceholders(holder.menuName, region));
                Material m = Material.matchMaterial(id.toUpperCase());
                return new ItemStack(m != null ? m : Material.STONE);
            });
        }

        private List<Integer> parseSlots(String str) {
            List<Integer> res = new ArrayList<>();
            if (str.contains(",")) { for (String s : str.split(",")) res.addAll(parseSlots(s)); return res; }
            if (str.contains("-")) {
                String[] p = str.split("-");
                for (int i = Integer.parseInt(p[0]); i <= Integer.parseInt(p[1]); i++) res.add(i);
                return res;
            }
            res.add(Integer.parseInt(str));
            return res;
        }
    }

    public static class CustomHolder implements InventoryHolder {
        public String menuName; public PSRegion region; public BukkitTask updateTask;
        public ItemStack[] baseLayer; public MenuAnimator animator;
        public CustomHolder(String name, PSRegion r) { this.menuName = name; this.region = r; }
        @Override public Inventory getInventory() { return null; }
    }
}
