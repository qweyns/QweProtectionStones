package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.RegionBounds;
import org.qweyns.qweprotectstones.regions.RegionType;

import java.util.ArrayList;
import java.util.List;

public class FindSpotSubCommand extends AbstractRegionSubCommand {

    public FindSpotSubCommand(QweProtectStones plugin) {
        super(plugin);
    }

    @Override
    public String permission() {
        return QweProtectStones.PERMISSION_PREFIX + ".findspot";
    }

    @Override
    public String name() {
        return "findspot";
    }

    @Override
    public List<String> aliases() {
        return List.of("find", "spot");
    }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        RegionType type = resolveType(player, args);
        if (type == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("findspot_usage",
                    "%types%", String.join(", ", plugin.getRegionTypes().ids())));
            return;
        }

        Location from = player.getLocation();
        World world = from.getWorld();
        if (world == null) return;

        Location free = search(world, from, type);
        if (free == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("findspot_none",
                    "%distance%", String.valueOf(plugin.getTunables().findSpotMaxRings() * stepFor(type))));
            return;
        }

        int distance = (int) Math.round(free.distance(from));
        player.sendMessage(plugin.getLanguageManager().getMessage("findspot_found",
                "%x%", String.valueOf(free.getBlockX()),
                "%y%", String.valueOf(free.getBlockY()),
                "%z%", String.valueOf(free.getBlockZ()),
                "%distance%", String.valueOf(distance),
                "%type%", type.displayName()));
    }

    private Location search(World world, Location from, RegionType type) {
        int step = stepFor(type);
        int y = from.getBlockY();

        int maxRings = plugin.getTunables().findSpotMaxRings();
        for (int ring = 1; ring <= maxRings; ring++) {
            for (int[] offset : ringOffsets(ring)) {
                int x = from.getBlockX() + offset[0] * step;
                int z = from.getBlockZ() + offset[1] * step;

                RegionBounds candidate = RegionBounds.around(x, y, z,
                        type.radiusX(), type.radiusY(), type.radiusZ(),
                        world.getMinHeight(), world.getMaxHeight() - 1);

                RegionBounds checked = type.minDistanceToOthers() > 0
                        ? candidate.expand(type.minDistanceToOthers())
                        : candidate;

                if (plugin.getRegionManager().isAreaFree(world, checked)) {
                    return new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
                }
            }
        }
        return null;
    }

    private int stepFor(RegionType type) {
        return Math.max(1, Math.max(type.widthX(), type.widthZ()));
    }

    private List<int[]> ringOffsets(int ring) {
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
                if (Math.abs(dx) == ring || Math.abs(dz) == ring) offsets.add(new int[]{dx, dz});
            }
        }
        return offsets;
    }

    private RegionType resolveType(Player player, String[] args) {
        if (args.length > 0) return plugin.getRegionTypes().byId(args[0].toUpperCase(java.util.Locale.ROOT));

        RegionType inHand = plugin.getRegionTypes().byMaterial(player.getInventory().getItemInMainHand().getType());
        if (inHand != null) return inHand;

        return plugin.getRegionTypes().all().stream().findFirst().orElse(null);
    }

    @Override
    public List<String> complete(CommandSender sender, Player player, String[] args) {
        return args.length == 1 ? filter(new ArrayList<>(plugin.getRegionTypes().ids()), args[0]) : List.of();
    }
}
