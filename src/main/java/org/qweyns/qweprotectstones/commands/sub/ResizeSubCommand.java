package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.commands.SubCommand;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionBounds;
import org.qweyns.qweprotectstones.regions.TrustLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Изменение границ существующего привата: {@code /ps expand} и {@code /ps move}.
 *
 * <p>Все лимиты — шаг расширения, потолок радиуса, дальность переноса, требуемый
 * уровень доступа, запрет во время осады — берутся из секции {@code resize}
 * config.yml, без зашитых констант.</p>
 */
public class ResizeSubCommand extends AbstractRegionSubCommand implements SubCommand {

    public enum Mode {
        EXPAND("expand"),
        MOVE("move");

        private final String name;

        Mode(String name) { this.name = name; }

        public String key() { return name; }
    }

    private final Mode mode;

    public ResizeSubCommand(QweProtectStones plugin, Mode mode) {
        super(plugin);
        this.mode = mode;
    }

    @Override
    public String permission() {
        // expand и move — разные права: расширение доступнее переноса.
        return QweProtectStones.PERMISSION_PREFIX + (mode == Mode.EXPAND ? ".expand" : ".move");
    }

    @Override
    public String name() { return mode.key(); }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        if (!enabled()) {
            player.sendMessage(plugin.getLanguageManager().getMessage(mode.key() + "_disabled"));
            return;
        }

        TrustLevel required = requiredTrust();
        Region region = regionWithTrust(player, required);
        if (region == null) return;

        // Во время осады границы менять нельзя — иначе приват «убегает» от атаки.
        if (plugin.getConfigManager().getConfig().getBoolean("resize.block-during-siege", true)
                && plugin.isUnderSiege(region)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("resize_siege"));
            return;
        }

        if (mode == Mode.EXPAND) expand(player, region, args);
        else move(player, region);
    }

    /** /ps expand [количество] [all|horizontal|up|down] */
    private void expand(Player player, Region region, String[] args) {
        int amount = 1;
        if (args.length > 0) {
            try {
                amount = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.getLanguageManager().getMessage("expand_usage"));
                return;
            }
        }
        int maxStep = maxStep();
        if (amount < 1 || amount > maxStep) {
            player.sendMessage(plugin.getLanguageManager().getMessage("expand_max_step", "%max%", String.valueOf(maxStep)));
            return;
        }

        String direction = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "all";
        boolean verticalAllowed = plugin.getConfigManager().getConfig().getBoolean("resize.expand.vertical", false);
        if (!direction.equals("all") && !direction.equals("horizontal")
                && !direction.equals("up") && !direction.equals("down")) {
            player.sendMessage(plugin.getLanguageManager().getMessage("expand_usage"));
            return;
        }
        if ((direction.equals("up") || direction.equals("down")) && !verticalAllowed) {
            player.sendMessage(plugin.getLanguageManager().getMessage("expand_no_vertical"));
            return;
        }

        World world = region.getWorld();
        if (world == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("expand_world_unloaded"));
            return;
        }

        RegionBounds current = region.getBounds();
        RegionBounds expanded = switch (direction) {
            case "horizontal" -> current.expandHorizontally(amount);
            case "up" -> new RegionBounds(current.minX(), current.minY(), current.minZ(),
                    current.maxX(), current.maxY() + amount, current.maxZ());
            case "down" -> new RegionBounds(current.minX(), current.minY() - amount, current.minZ(),
                    current.maxX(), current.maxY(), current.maxZ());
            default -> current.expand(amount);
        };

        // Клампим по высоте мира: за пределы Heightmap не выходим.
        expanded = new RegionBounds(
                expanded.minX(), Math.max(world.getMinHeight(), expanded.minY()), expanded.minZ(),
                expanded.maxX(), Math.min(world.getMaxHeight() - 1, expanded.maxY()), expanded.maxZ());

        int maxRadius = maxRadius();
        if (maxRadius > 0 && (expanded.sizeX() / 2 > maxRadius || expanded.sizeZ() / 2 > maxRadius)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("expand_max_radius",
                    "%max%", String.valueOf(maxRadius)));
            return;
        }

        Region blocking = plugin.getRegionManager().updateBounds(region, expanded);
        if (blocking != null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("resize_overlap",
                    "%owner%", blocking.getOwnerName()));
            return;
        }

        plugin.getVisualManager().showBoundary(region, "create");
        if (plugin.getDynmapIntegration() != null) plugin.getDynmapIntegration().update(region);
        if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().update(region);
        player.sendMessage(plugin.getLanguageManager().getMessage("expand_done",
                "%size%", region.getBounds().sizeX() + "x" + region.getBounds().sizeZ(),
                "%height%", String.valueOf(region.getBounds().sizeY())));
    }

    /** /ps move — перенести ядро на блок, где стоит игрок. */
    private void move(Player player, Region region) {
        Block target = player.getLocation().getBlock();
        World world = target.getWorld();

        if (world == null || !world.getName().equals(region.getWorldName())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("move_wrong_world",
                    "%world%", region.getWorldName()));
            return;
        }

        int distance = Math.abs(target.getX() - region.getCoreX())
                + Math.abs(target.getY() - region.getCoreY())
                + Math.abs(target.getZ() - region.getCoreZ());
        int maxDistance = maxDistance();
        if (distance > maxDistance) {
            player.sendMessage(plugin.getLanguageManager().getMessage("move_too_far",
                    "%max%", String.valueOf(maxDistance)));
            return;
        }

        // Ставить ядро можно только на свободное место: список заменяемых блоков в конфиге.
        if (!replaceable().contains(target.getType())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("move_blocked"));
            return;
        }

        Region blocking = plugin.getRegionManager().moveRegion(region, player.getLocation());
        if (blocking != null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("resize_overlap",
                    "%owner%", blocking.getOwnerName()));
            return;
        }

        plugin.getHologramManager().createOrUpdateHologram(region);
        plugin.getVisualManager().showBoundary(region, "create");
        if (plugin.getDynmapIntegration() != null) plugin.getDynmapIntegration().update(region);
        if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().update(region);
        player.sendMessage(plugin.getLanguageManager().getMessage("move_done",
                "%x%", String.valueOf(region.getCoreX()),
                "%y%", String.valueOf(region.getCoreY()),
                "%z%", String.valueOf(region.getCoreZ())));
    }

    // ------------------------------------------------------------------
    // Настройки из config.yml (секция resize)
    // ------------------------------------------------------------------

    private boolean enabled() {
        return plugin.getConfigManager().getConfig().getBoolean("resize." + mode.key() + ".enable", true);
    }

    private TrustLevel requiredTrust() {
        String raw = plugin.getConfigManager().getConfig()
                .getString("resize." + mode.key() + ".required-trust",
                        mode == Mode.MOVE ? TrustLevel.OWNER.name() : TrustLevel.MANAGER.name());
        Optional<TrustLevel> parsed = TrustLevel.parse(raw);
        return parsed.orElseGet(() -> mode == Mode.MOVE ? TrustLevel.OWNER : TrustLevel.MANAGER);
    }

    private int maxStep() {
        return Math.max(1, plugin.getConfigManager().getConfig().getInt("resize.expand.max-step", 10));
    }

    private int maxRadius() {
        return Math.max(0, plugin.getConfigManager().getConfig().getInt("resize.expand.max-radius", 32));
    }

    private int maxDistance() {
        return Math.max(1, plugin.getConfigManager().getConfig().getInt("resize.move.max-distance", 64));
    }

    private List<Material> replaceable() {
        List<Material> result = new ArrayList<>();
        for (String raw : plugin.getConfigManager().getConfig().getStringList("resize.move.replaceable")) {
            Material material = Material.matchMaterial(raw);
            if (material != null) result.add(material);
        }
        if (result.isEmpty()) result.add(Material.AIR);
        return result;
    }

    @Override
    public List<String> complete(CommandSender sender, Player player, String[] args) {
        if (mode != Mode.EXPAND) return List.of();
        // Количество блоков: подсказываем максимальный шаг из resize.expand.
        if (args.length == 1) {
            return filter(List.of(String.valueOf(maxStep())), args[0]);
        }
        if (args.length == 2) {
            return filter(List.of("all", "horizontal", "up", "down"), args[1]);
        }
        return List.of();
    }
}
