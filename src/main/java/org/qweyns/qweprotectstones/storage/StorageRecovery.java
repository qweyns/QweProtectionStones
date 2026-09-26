package org.qweyns.qweprotectstones.storage;

import org.qweyns.qweprotectstones.regions.*;
import org.qweyns.qweprotectstones.features.market.*;
import org.qweyns.qweprotectstones.storage.dao.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Аварийный журнал неподтверждённых намерений. Не заменяет регулярный backup. */
final class StorageRecovery {
    static Map<String, Object> command(String kind, UUID id, Object... fields) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kind", kind); data.put("id", id.toString());
        for (int i = 0; i < fields.length; i += 2) data.put((String)fields[i], fields[i+1]);
        return data;
    }
    static Map<String, Object> region(Region source) {
        Region r = source.snapshot();
        var b = r.getBounds();
        List<Map<String,Object>> members = new ArrayList<>();
        for (var member : r.getMembers()) members.add(Map.of("uuid",member.uuid().toString(),
                "name",member.displayName(),"trust",member.role(),"at",member.addedAt()));
        Map<String, Boolean> flags = new LinkedHashMap<>();
        r.getFlagOverrides().forEach((key,value) -> flags.put(key.name(),value));
        Map<String,String> bans = new LinkedHashMap<>();
        r.getBannedPlayers().forEach((key,value) -> bans.put(key.toString(),value));
        return command("region",r.getId(),"world",r.getWorldName(),"type",r.getTypeId(),
                "bounds",List.of(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()),
                "core",List.of(r.getCoreX(),r.getCoreY(),r.getCoreZ()),
                "owner",r.getOwnerId() == null ? "" : r.getOwnerId().toString(),"ownerName",r.getOwnerName(),
                "durability",r.getDurability(),"max",r.getMaxDurability(),"created",r.getCreatedAt(),
                "name",r.getDisplayName(),"attacks",r.getAttackCount(),"lastAttack",r.getLastAttackAt(),
                "attacker",r.getLastAttackerName(),"penalty",r.getPenaltyUntil(),
                "members",members,"flags",flags,"bans",bans,"effects",List.copyOf(r.getEffects()));
    }
    static String text(Map<?,?> data, String key) { return Objects.toString(data.get(key), ""); }
    static long number(Map<?,?> data, String key) { return ((Number)data.get(key)).longValue(); }
    static double decimal(Map<?,?> data, String key) { return ((Number)data.get(key)).doubleValue(); }
    static UUID uuid(String text) { return text.isEmpty() ? null : UUID.fromString(text); }
    static Region decodeRegion(Map<?,?> data) {
        List<?> b = (List<?>)data.get("bounds"), c = (List<?>)data.get("core");
        int[] bounds = b.stream().mapToInt(v -> ((Number)v).intValue()).toArray();
        Region r = new Region(uuid(text(data,"id")),text(data,"world"),
                new RegionBounds(bounds[0],bounds[1],bounds[2],bounds[3],bounds[4],bounds[5]),
                ((Number)c.get(0)).intValue(),((Number)c.get(1)).intValue(),((Number)c.get(2)).intValue(),
                text(data,"type"),uuid(text(data,"owner")),text(data,"ownerName"),
                (int)number(data,"durability"),(int)number(data,"max"),number(data,"created"));
        r.restoreDecoration(text(data,"name"));
        r.restoreStats((int)number(data,"attacks"),number(data,"lastAttack"),text(data,"attacker"));
        r.setPenaltyUntil(number(data,"penalty"));
        for (Object raw : (List<?>)data.get("members")) {
            Map<?,?> m = (Map<?,?>)raw;
            r.restoreMember(new RegionMember(uuid(text(m,"uuid")),text(m,"name"),text(m,"trust"),number(m,"at")));
        }
        ((Map<?,?>)data.get("flags")).forEach((key,value) -> r.setFlag(RegionFlag.valueOf(key.toString()), (Boolean)value));
        ((Map<?,?>)data.get("bans")).forEach((key,value) -> r.restoreBan(uuid(key.toString()),value.toString()));
        for (Object effect : (List<?>)data.get("effects")) r.getEffects().add(effect.toString());
        return r;
    }
    static void replay(Path path, RegionDao dao) throws IOException {
        if (!Files.exists(path)) return;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setCodePointLimit(128 * 1024 * 1024);
            // Собственный dumper может использовать общие пустые коллекции как YAML-alias.
            options.setMaxAliasesForCollections(1_000_000);
            Object parsed = new Yaml(new SafeConstructor(options)).load(Files.readString(path));
            if (!(parsed instanceof Map<?,?> root) || !Objects.equals(root.get("version"),1)
                    || !(root.get("operations") instanceof List<?> operations)) throw new IOException("Неизвестный формат журнала восстановления");
            for (Object raw : operations) apply((Map<?,?>)raw, dao);
            Files.delete(path);
        } catch (RuntimeException e) { throw new IOException("Не удалось применить журнал восстановления",e); }
    }
    private static void apply(Map<?,?> data, RegionDao dao) {
        UUID id = uuid(text(data,"id"));
        switch (text(data,"kind")) {
            case "region" -> dao.saveAll(List.of(decodeRegion(data)));
            case "deleteRegion" -> dao.deleteAll(List.of(id));
            case "sale" -> dao.saveSale(new RegionSale(id,uuid(text(data,"owner")),text(data,"name"),decimal(data,"price"),number(data,"at")));
            case "deleteSale" -> dao.deleteSale(id);
            case "rental" -> dao.saveRental(new RegionRental(id,uuid(text(data,"owner")),text(data,"name"),decimal(data,"price"),
                    (int)number(data,"minutes"),uuid(text(data,"tenant")),text(data,"tenantName"),number(data,"until")));
            case "deleteRental" -> dao.deleteRental(id);
            case "autoadd" -> {
                Set<String> friends = new HashSet<>();
                for (Object friend : (List<?>)data.get("friends")) friends.add(friend.toString());
                dao.saveAutoAdd(id, friends, (Boolean)data.get("off"));
            }
            case "player" -> dao.touchPlayer(id,text(data,"name"),number(data,"at"));
            case "log" -> dao.appendLog(List.of(new RegionLogEntry(uuid(text(data,"region")),number(data,"at"),
                    text(data,"name"),text(data,"action"),text(data,"detail"))));
            default -> throw new IllegalArgumentException("Неизвестная операция журнала");
        }
    }
    static void write(Path path, List<Map<String,Object>> operations) throws IOException {
        if (operations.isEmpty()) { Files.deleteIfExists(path); return; }
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(),".qps-recovery-",".tmp");
        try {
            var options = new org.yaml.snakeyaml.DumperOptions();
            // Иначе SnakeYAML превращает строки с control-символами в !!binary/byte[].
            options.setNonPrintableStyle(org.yaml.snakeyaml.DumperOptions.NonPrintableStyle.ESCAPE);
            Files.writeString(temp, new Yaml(options).dump(Map.of("version",1,"operations",operations)));
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
