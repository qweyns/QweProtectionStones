package org.qweyns.qweprotectstones.menus;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.features.effect.EffectManager;
import org.qweyns.qweprotectstones.config.Tunables;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.event.RegionDeleteEvent;
import org.qweyns.qweprotectstones.regions.UpgradeCost;
import org.qweyns.qweprotectstones.utils.ColorUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MenuManager implements Listener {

    private static final List<String> DEFAULT_MENUS = List.of("main", "effects", "upgrade");

    private final QweProtectStones plugin;
    private final MenuPlaceholders placeholders;
    private final MenuItemFactory itemFactory;
    private final MenuRequirements requirements;
    private final MenuActions actions;

    private final Map<String, FileConfiguration> menus = new HashMap<>();
    private final Map<UUID, Long> clickCooldowns = new ConcurrentHashMap<>();
    private final Set<String> menusWithPercent = new HashSet<>();

    public MenuManager(QweProtectStones plugin) {
        this.plugin = plugin;
        this.placeholders = new MenuPlaceholders(plugin);
        this.itemFactory = new MenuItemFactory(plugin, placeholders);
        this.requirements = new MenuRequirements(plugin, placeholders);
        this.actions = new MenuActions(plugin, placeholders, requirements);

        loadMenus();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
    }

    public MenuActions getActions() { return actions; }

    public MenuItemFactory getItemFactory() { return itemFactory; }

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

    public void loadMenus() {
        menus.clear();
        menusWithPercent.clear();
        itemFactory.clearCache();

        File folder = new File(plugin.getDataFolder(), "menus");
        if (!folder.isDirectory() && !folder.mkdirs()) {
            plugin.getLogger().severe("Не удалось создать папку menus/ — меню недоступны.");
            return;
        }

        for (String name : DEFAULT_MENUS) {
            File file = new File(folder, name + ".yml");
            if (!file.isFile()) plugin.saveResource("menus/" + name + ".yml", false);
        }

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            plugin.getLogger().severe("Не удалось прочитать папку menus/.");
            return;
        }

        for (File file : files) {
            String name = file.getName().substring(0, file.getName().length() - ".yml".length());
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            menus.put(name, cfg);
            if (cfg.saveToString().indexOf('%') >= 0) menusWithPercent.add(name);
            validateEffectNames(name, cfg);
        }
        plugin.getLogger().info("Загружено меню: " + menus.size());
    }

    // опечатка в имени эффекта всплыла бы только у игрока на кнопке — ловим при загрузке
    private void validateEffectNames(String menuName, FileConfiguration cfg) {
        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items == null) return;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\\[(?:region|claim|ps)_add_effect\\]\\s*([A-Za-z0-9_]+)");
        for (String itemId : items.getKeys(false)) {
            for (String cmd : items.getStringList(itemId + ".click_commands")) {
                java.util.regex.Matcher matcher = pattern.matcher(cmd);
                if (!matcher.find()) continue;
                if (!EffectManager.isKnownEffect(matcher.group(1))) {
                    plugin.getLogger().warning("Меню '" + menuName + "', предмет '" + itemId
                            + "': неизвестный эффект '" + matcher.group(1) + "'.");
                }
            }
        }
    }

    public String defaultMenuFor(Region region) {
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        if (type == null) return "main";

        if (type.hasMenu()) {
            String name = type.menuName();
            if (menus.containsKey(name)) return name;

            plugin.getLogger().warning("Тип " + type.id() + ": меню '" + name
                    + "' не найдено — открываю главное.");
        }
        return type.durabilityUpgradeEnabled() ? "main" : "effects";
    }

    public void openMenu(Player player, String menuName, Region region) {
        FileConfiguration menuCfg = menus.get(menuName);
        if (menuCfg == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("menu_not_found", "%menu%", menuName));
            return;
        }

        int size = normalizeSize(menuCfg.getInt("size", 27), menuName);
        Component title = ColorUtil.formatComponent(
                placeholders.apply(player, menuCfg.getString("menu_title", "Меню"), region, null));

        MenuHolder holder = new MenuHolder(menuName, region);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.inventory = inv;

        render(player, holder, true);

        MenuAnimator animator = null;
        if (menuCfg.contains("animations.default")) {
            animator = new MenuAnimator(plugin, player, menuCfg, region, inv, holder);
            holder.animator = animator;
        }

        player.openInventory(inv);

        // открытие могли отменить другим плагином — таймеры только у реально открытого меню
        if (player.getOpenInventory().getTopInventory().getHolder() != holder) return;

        int interval = menuCfg.getInt("update_interval", 0);
        if (interval > 0) {
            holder.updateTask = plugin.getSchedulers().runAtEntityTimer(player, () -> {
                // страховка, игрок мог выйти без InventoryCloseEvent

                if (!player.isOnline()) {
                    holder.cancelTasks();
                    return;
                }
                render(player, holder, false);
            }, interval, interval);
        }

        final MenuAnimator anim = animator;
        if (anim != null) {
            // задачу держим в holder, иначе тикает вечно

            holder.animatorTask = plugin.getSchedulers().runAtEntityTimer(player, () -> {
                if (!player.isOnline()) {
                    holder.cancelTasks();
                    return;
                }
                anim.run();
            }, 1L, 1L);
        }
    }

    private int normalizeSize(int configured, String menuName) {
        int size = Math.max(9, Math.min(54, configured));
        if (size % 9 != 0) size = ((size / 9) + 1) * 9;

        if (size != configured) {
            plugin.getLogger().warning("Некорректный размер меню " + menuName + " (" + configured + ") — использую " + size + ".");
        }
        return size;
    }

    public void render(Player player, MenuHolder holder, boolean force) {
        FileConfiguration menuCfg = menus.get(holder.menuName);
        if (menuCfg == null) return;

        Region region = holder.region;
        long version = region == null ? 0 : region.getVersion();

        // статичные предметы не пересобираем дважды в секунду

        if (!force && holder.renderedVersion == version && !hasLivePlaceholders(holder.menuName)) return;
        holder.renderedVersion = version;

        Inventory inv = holder.inventory;
        ItemStack[] contents = new ItemStack[inv.getSize()];
        Map<String, String> extra = dynamicPlaceholders(holder.menuName, region);

        ConfigurationSection items = menuCfg.getConfigurationSection("items");
        if (items != null) {
            Set<Integer> filled = new HashSet<>();

            for (String key : sortedItemKeys(items)) {
                ConfigurationSection cfg = items.getConfigurationSection(key);
                if (cfg == null) continue;
                if (!requirements.passes(cfg, "view_requirement.requirements", player, region)) continue;

                List<Integer> slots = readSlots(cfg);
                if (slots.isEmpty()) continue;

                boolean needsBuild = slots.stream().anyMatch(slot -> slot >= 0 && slot < contents.length && !filled.contains(slot));
                if (!needsBuild) continue;

                String signature = itemSignature(cfg, player, region, extra);
                boolean reusable = true;
                for (int slot : slots) {
                    if (slot < 0 || slot >= contents.length || filled.contains(slot)
                            || !signature.equals(holder.slotSignatureAt(slot))) {
                        reusable = false;
                        break;
                    }
                }
                if (reusable) {
                    for (int slot : slots) {
                        if (filled.add(slot)) contents[slot] = holder.slotStackAt(slot);
                    }
                    continue;
                }

                ItemStack item = itemFactory.build(cfg, player, region, extra, holder.menuName + ":" + key);
                if (item == null) continue;

                holder.ensureSlotCache(inv.getSize());
                for (int slot : slots) {
                    if (slot >= 0 && slot < contents.length && filled.add(slot)) {
                        contents[slot] = item;
                        holder.slotSignatures[slot] = signature;
                        holder.slotStacks[slot] = item;
                    }
                }
            }
        }

        if (holder.menuName.equals("upgrade")) renderUpgradeSlots(player, holder, region, menuCfg, contents);

        holder.baseLayer = contents;

        if (holder.animator == null) inv.setContents(contents);
        else holder.animator.refreshBaseLayer(contents);
    }

    // живые плейсхолдеры имеют смысл, только если в меню вообще есть %
    private boolean hasLivePlaceholders(String menuName) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return false;
        FileConfiguration menuCfg = menus.get(menuName);
        return menuCfg != null && menuCfg.getBoolean("live_placeholders", true)
                && menusWithPercent.contains(menuName);
    }

    // подпись предмета после подстановок: не менялась — не пересобираем
    private String itemSignature(ConfigurationSection cfg, Player player, Region region, Map<String, String> extra) {
        String material = placeholders.apply(player, cfg.getString("material", ""), region, extra);
        String name = placeholders.apply(player, cfg.getString("display_name", ""), region, extra);
        StringBuilder sb = new StringBuilder(material).append('\u0000').append(name).append('\u0000');
        for (String line : cfg.getStringList("lore")) {
            sb.append(placeholders.apply(player, line, region, extra)).append('\u0001');
        }
        return sb.toString();
    }

    private void renderUpgradeSlots(Player player, MenuHolder holder, Region region, FileConfiguration menuCfg, ItemStack[] contents) {
        List<Integer> slots = menuCfg.getIntegerList("upgrade_slots");
        ConfigurationSection template = menuCfg.getConfigurationSection("upgrade_item_template");
        if (slots.isEmpty() || template == null || region == null) return;

        ConfigurationSection maxTemplate = menuCfg.getConfigurationSection("max_upgrade_item_template");

        int currentDurability = region.getDurability();
        int maxDurability = region.getMaxDurability();
        Material upgradeMaterial = upgradeItemFor(region);

        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            if (slot < 0 || slot >= contents.length) continue;

            int targetLevel = currentDurability + i + 1;

            if (targetLevel > maxDurability) {
                if (maxTemplate != null) {
                    ItemStack maxItem = itemFactory.build(maxTemplate, player, region,
                            dynamicPlaceholders("upgrade", region), "upgrade:max");
                    if (maxItem != null) contents[slot] = maxItem;
                }
                continue;
            }

            Map<String, String> extra = dynamicPlaceholders("upgrade", region);
            extra.put("%level%", String.valueOf(targetLevel));
            extra.put("%cost%", String.valueOf(upgradeCost(region, currentDurability, targetLevel)));
            extra.put("%item%", "<translate:" + upgradeMaterial.translationKey() + ">");

            String signature = "upgrade|" + targetLevel + "|" + currentDurability + "/" + maxDurability
                    + "|" + upgradeMaterial.name();
            if (signature.equals(holder.slotSignatureAt(slot))) {
                contents[slot] = holder.slotStackAt(slot);
                continue;
            }

            ItemStack item = itemFactory.build(template, player, region, extra);
            if (item == null) continue;

            // withType, не setType, второй теряет метаданные

            item = item.withType(upgradeMaterial);
            item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), targetLevel)));
            contents[slot] = item;
            holder.ensureSlotCache(contents.length);
            holder.slotSignatures[slot] = signature;
            holder.slotStacks[slot] = item;
        }
    }

    private long upgradeCost(Region region, int current, int target) {
        int penalty = plugin.getPenaltyManager().hasPenalty(region)
                ? plugin.getPenaltyManager().getPenaltyMultiplier()
                : 1;

        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        int multiplier = type != null && type.overridesUpgradeMultiplier()
                ? type.upgradeCostMultiplier()
                : plugin.getConfigManager().getUpgradeMultiplier();
        int tax = type != null && type.overridesUpgradeTax()
                ? type.upgradeTax()
                : plugin.getConfigManager().getUpgradeTax();

        return UpgradeCost.calculate(current, target, multiplier, tax, penalty);
    }

    public Material upgradeItemFor(Region region) {
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        if (type == null || !type.overridesUpgradeItem()) return plugin.getConfigManager().getUpgradeItem();

        Material material = Material.matchMaterial(type.upgradeItem());
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("Тип " + type.id() + ": upgrade.item '" + type.upgradeItem()
                    + "' не является предметом — использую общий.");
            return plugin.getConfigManager().getUpgradeItem();
        }
        return material;
    }

    private List<String> sortedItemKeys(ConfigurationSection items) {
        List<String> keys = new ArrayList<>(items.getKeys(false));
        keys.sort(Comparator.comparingInt(key -> items.getInt(key + ".priority", 100)));
        return keys;
    }

    private List<Integer> readSlots(ConfigurationSection cfg) {
        if (cfg.contains("slots")) return cfg.getIntegerList("slots");
        if (cfg.contains("slot")) return List.of(cfg.getInt("slot"));
        return List.of();
    }

    private Map<String, String> dynamicPlaceholders(String menuName, Region region) {
        Map<String, String> map = new HashMap<>();
        if (!menuName.equals("upgrade") || region == null) return map;

        map.put("%current%", String.valueOf(region.getDurability()));
        map.put("%max%", String.valueOf(region.getMaxDurability()));
        return map;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder holder) holder.cancelTasks();
    }

    // кулдаун переживает закрытие меню — иначе close/open обходит защиту от спама
    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        clickCooldowns.remove(event.getPlayer().getUniqueId());
    }

    // приват уничтожен — никто не должен остаться в его меню
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegionDelete(RegionDeleteEvent event) {
        UUID regionId = event.getRegion().getId();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!(viewer.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder holder)) continue;
            if (holder.getRegion() == null || !holder.getRegion().getId().equals(regionId)) continue;

            plugin.getSchedulers().runAtEntity(viewer, () -> {
                viewer.closeInventory();
                viewer.sendMessage(plugin.getLanguageManager().getMessage("menu_region_gone"));
            });
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // регион могли уничтожить, пока меню было открыто
        if (holder.getRegion() != null
                && plugin.getRegionManager().getById(holder.getRegion().getId()) == null) {
            plugin.getSchedulers().runAtEntity(player, () -> {
                player.closeInventory();
                player.sendMessage(plugin.getLanguageManager().getMessage("menu_region_gone"));
            });
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(event.getView().getTopInventory())) return;

        // кулдаун только по кликам в самом меню

        long now = System.currentTimeMillis();
        if (now - clickCooldowns.getOrDefault(player.getUniqueId(), 0L) < plugin.getTunables().menuClickCooldownMs()) return;
        clickCooldowns.put(player.getUniqueId(), now);

        FileConfiguration menuCfg = menus.get(holder.menuName);
        int slot = event.getSlot();
        if (menuCfg == null || holder.baseLayer == null || slot < 0 || slot >= holder.baseLayer.length
                || holder.baseLayer[slot] == null) {
            return;
        }

        // клик под шторкой анимации не проходит, даже если под ней кнопка
        if (holder.animator != null && holder.animator.isCovered(slot)) return;

        Region region = holder.region;
        // права перепроверяем, меню могло пережить исключение

        if (region != null && !isStillTrusted(player, region)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("no_region_access",
                    "%level%", plugin.getLanguageManager().rawTemplate("trust_container")));
            plugin.getSchedulers().runAtEntity(player, player::closeInventory);
            return;
        }

        if (holder.menuName.equals("upgrade") && handleUpgradeClick(player, holder, menuCfg, slot)) return;

        ConfigurationSection items = menuCfg.getConfigurationSection("items");
        if (items == null) return;

        for (String key : sortedItemKeys(items)) {
            ConfigurationSection itemCfg = items.getConfigurationSection(key);
            if (itemCfg == null) continue;
            if (!requirements.passes(itemCfg, "view_requirement.requirements", player, region)) continue;
            if (!readSlots(itemCfg).contains(slot)) continue;

            if (!requirements.passes(itemCfg, "click_requirement.requirements", player, region)) {
                actions.execute(player, itemCfg.getStringList("click_requirement.deny_commands"), region);
                return;
            }

            actions.execute(player, clickCommands(itemCfg, event.isRightClick()), region);
            return;
        }
    }

    private boolean isStillTrusted(Player player, Region region) {
        if (plugin.getRegionManager().getById(region.getId()) == null) return false;
        return plugin.getProtectionService().has(region, player, plugin.getProtectionService().requiredFor(Tunables.TrustAction.CONTAINER));
    }

    private List<String> clickCommands(ConfigurationSection itemCfg, boolean rightClick) {
        if (rightClick && itemCfg.contains("right_click_commands")) return itemCfg.getStringList("right_click_commands");
        if (!rightClick && itemCfg.contains("left_click_commands")) return itemCfg.getStringList("left_click_commands");
        return itemCfg.getStringList("click_commands");
    }

    private boolean handleUpgradeClick(Player player, MenuHolder holder, FileConfiguration menuCfg, int slot) {
        List<Integer> upgradeSlots = menuCfg.getIntegerList("upgrade_slots");
        int slotIndex = upgradeSlots.indexOf(slot);
        if (slotIndex < 0) return false;

        Region region = holder.region;
        if (region == null) return true;

        if (!plugin.getProtectionService().canManage(player, region)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("no_region_access",
                    "%level%", plugin.getLanguageManager().rawTemplate("trust_manager")));
            return true;
        }

        int currentDurability = region.getDurability();
        int targetLevel = currentDurability + slotIndex + 1;
        int maxDurability = region.getMaxDurability();

        if (targetLevel > maxDurability) {
            plugin.getTunables().menuDenied().playTo(player);
            return true;
        }

        long totalCost = upgradeCost(region, currentDurability, targetLevel);
        Material upgradeMaterial = upgradeItemFor(region);
        String itemName = "<translate:" + upgradeMaterial.translationKey() + ">";

        if (countItems(player, upgradeMaterial) < totalCost) {
            player.sendMessage(plugin.getLanguageManager().getMessage("upgrade_not_enough_items",
                    "%amount%", String.valueOf(totalCost), "%item%", itemName));
            plugin.getTunables().menuDenied().playTo(player);
            return true;
        }

        if (!removeItems(player, upgradeMaterial, totalCost)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("upgrade_not_enough_items",
                    "%amount%", String.valueOf(totalCost), "%item%", itemName));
            return true;
        }

        if (plugin.getPenaltyManager().hasPenalty(region)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("upgrade_penalty",
                    "%multiplier%", String.valueOf(plugin.getPenaltyManager().getPenaltyMultiplier())));
        }

        region.setDurability(targetLevel);
        plugin.getRegionStorage().saveNow(region);
        plugin.getHologramManager().createOrUpdateHologram(region);

        plugin.getTunables().menuSuccess().playTo(player);
        player.sendMessage(plugin.getLanguageManager().getMessage("upgrade_success", "%durability%", String.valueOf(targetLevel)));
        render(player, holder, true);
        return true;
    }

    private long countItems(Player player, Material material) {
        long count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) count += stack.getAmount();
        }
        return count;
    }

    private boolean removeItems(Player player, Material material, long amount) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        long needed = amount;

        for (int i = 0; i < storage.length && needed > 0; i++) {
            ItemStack stack = storage[i];
            if (stack == null || stack.getType() != material) continue;

            if (stack.getAmount() <= needed) {
                needed -= stack.getAmount();
                storage[i] = null;
            } else {
                stack.setAmount((int) (stack.getAmount() - needed));
                needed = 0;
            }
        }
        if (needed > 0) return false;

        player.getInventory().setStorageContents(storage);
        player.updateInventory();
        return true;
    }
}
