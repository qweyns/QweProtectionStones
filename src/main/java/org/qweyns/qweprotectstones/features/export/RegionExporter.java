package org.qweyns.qweprotectstones.features.export;

import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionFlag;
import org.qweyns.qweprotectstones.regions.RegionMember;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class RegionExporter {

    private final QweProtectStones plugin;

    public RegionExporter(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public File export() throws IOException {
        File folder = new File(plugin.getDataFolder(), "exports");
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("не удалось создать папку exports/");
        }

        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File target = new File(folder, "regions_" + stamp + ".json");
        // два экспорта в одну секунду не должны молча перезаписывать друг друга
        int serial = 2;
        while (target.exists()) {
            target = new File(folder, "regions_" + stamp + "_" + serial++ + ".json");
        }

        StringBuilder json = new StringBuilder(1024);
        json.append("{\n  \"exported_at\": \"").append(stamp).append("\",\n");
        json.append("  \"plugin_version\": \"").append(escape(plugin.getPluginMeta().getVersion())).append("\",\n");
        json.append("  \"regions\": [\n");

        boolean first = true;
        for (Region region : plugin.getRegionManager().getAllRegions()) {
            if (!first) json.append(",\n");
            first = false;
            appendRegion(json, region);
        }

        json.append("\n  ]\n}\n");
        Files.writeString(target.toPath(), json.toString(), StandardCharsets.UTF_8);
        return target;
    }

    private void appendRegion(StringBuilder json, Region region) {
        json.append("    {\n");
        json.append("      \"id\": \"").append(region.getId()).append("\",\n");
        json.append("      \"type\": \"").append(escape(region.getTypeId())).append("\",\n");
        json.append("      \"name\": \"").append(escape(region.getDisplayName())).append("\",\n");
        json.append("      \"world\": \"").append(escape(region.getWorldName())).append("\",\n");
        json.append("      \"owner\": {\"uuid\": \"").append(region.getOwnerId())
                .append("\", \"name\": \"").append(escape(region.getOwnerName())).append("\"},\n");
        json.append("      \"core\": [").append(region.getCoreX()).append(", ")
                .append(region.getCoreY()).append(", ").append(region.getCoreZ()).append("],\n");
        json.append("      \"bounds\": {\"min\": [")
                .append(region.getBounds().minX()).append(", ").append(region.getBounds().minY()).append(", ")
                .append(region.getBounds().minZ()).append("], \"max\": [")
                .append(region.getBounds().maxX()).append(", ").append(region.getBounds().maxY()).append(", ")
                .append(region.getBounds().maxZ()).append("]},\n");
        json.append("      \"durability\": ").append(region.getDurability())
                .append(", \"max_durability\": ").append(region.getMaxDurability()).append(",\n");
        json.append("      \"created_at\": ").append(region.getCreatedAt()).append(",\n");
        json.append("      \"attacks\": {\"count\": ").append(region.getAttackCount())
                .append(", \"last_at\": ").append(region.getLastAttackAt())
                .append(", \"last_by\": \"").append(escape(region.getLastAttackerName())).append("\"},\n");

        appendMembers(json, region);
        appendBans(json, region);
        appendFlags(json, region);
        appendEffects(json, region);

        json.append("    }");
    }

    private void appendMembers(StringBuilder json, Region region) {
        json.append("      \"members\": [");
        boolean first = true;
        for (RegionMember member : region.getMembers()) {
            if (!first) json.append(", ");
            first = false;
            json.append("{\"uuid\": \"").append(member.uuid())
                    .append("\", \"name\": \"").append(escape(member.displayName()))
                    .append("\", \"trust\": \"").append(member.trust().key()).append("\"}");
        }
        json.append("],\n");
    }

    private void appendBans(StringBuilder json, Region region) {
        json.append("      \"bans\": [");
        boolean first = true;
        for (Map.Entry<java.util.UUID, String> entry : region.getBannedPlayers().entrySet()) {
            if (!first) json.append(", ");
            first = false;
            json.append("{\"uuid\": \"").append(entry.getKey())
                    .append("\", \"name\": \"").append(escape(entry.getValue())).append("\"}");
        }
        json.append("],\n");
    }

    private void appendFlags(StringBuilder json, Region region) {
        json.append("      \"flags\": {");
        boolean first = true;
        for (Map.Entry<RegionFlag, Boolean> entry : region.getFlagOverrides().entrySet()) {
            if (!first) json.append(", ");
            first = false;
            json.append("\"").append(entry.getKey().key()).append("\": ").append(entry.getValue());
        }
        json.append("},\n");
    }

    private void appendEffects(StringBuilder json, Region region) {
        json.append("      \"effects\": [");
        boolean first = true;
        for (String effect : region.getEffects()) {
            if (!first) json.append(", ");
            first = false;
            json.append("\"").append(escape(effect)).append("\"");
        }
        json.append("]\n");
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
