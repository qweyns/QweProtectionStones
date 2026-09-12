package org.qweyns.qweprotectstones.storage.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.features.market.RegionRental;
import org.qweyns.qweprotectstones.features.market.RegionSale;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionBounds;
import org.qweyns.qweprotectstones.regions.RegionFlag;
import org.qweyns.qweprotectstones.regions.RegionMember;
import org.qweyns.qweprotectstones.regions.TrustLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.logging.Level;

public abstract class AbstractSqlRegionDao implements RegionDao {

    private static final int SCHEMA_VERSION = 5;

    protected final QweProtectStones plugin;
    protected final String tablePrefix;
    protected HikariDataSource dataSource;

    protected AbstractSqlRegionDao(QweProtectStones plugin) {
        this.plugin = plugin;
        this.tablePrefix = sanitizePrefix(plugin == null ? "qps_" : plugin.getConfigManager().getConfig().getString("database.table_prefix", "qps_"));
    }

    private String sanitizePrefix(String raw) {
        if (raw == null) return "qps_";
        String cleaned = raw.replaceAll("[^A-Za-z0-9_]", "");
        return cleaned.isEmpty() ? "qps_" : cleaned;
    }

    java.util.logging.Logger log() {
        return plugin != null ? plugin.getLogger() : java.util.logging.Logger.getLogger("QweProtectStones-Test");
    }

    private int resolvePoolSize() {
        return plugin != null ? plugin.getConfigManager().getConfig().getInt("database.pool_size", 10) : 1;
    }

    private int batchSize() {
        return plugin != null ? plugin.getTunables().dbBatchSize() : 500;
    }

    protected abstract void configureHikari(HikariConfig config);

    protected abstract String regionUpsert();

    protected abstract String memberUpsert();

    protected abstract String flagUpsert();

    protected abstract String autoAddUpsert();

    protected abstract String banUpsert();

    protected abstract String playerUpsert();

    protected abstract String booleanType();

    protected abstract void createIndexes(Statement statement) throws SQLException;

    protected final String ownerIndexName() { return "idx_" + tablePrefix + "regions_owner"; }

    protected final String worldIndexName() { return "idx_" + tablePrefix + "regions_world"; }

    protected String regionsTable() { return tablePrefix + "regions"; }

    protected String membersTable() { return tablePrefix + "region_members"; }

    protected String flagsTable() { return tablePrefix + "region_flags"; }

    protected String autoAddTable() { return tablePrefix + "autoadd"; }

    protected String metaTable() { return tablePrefix + "meta"; }

    protected String bansTable() { return tablePrefix + "region_bans"; }

    protected String playersTable() { return tablePrefix + "players"; }

    protected String logTable() { return tablePrefix + "region_log"; }

    protected String salesTable() { return tablePrefix + "region_sales"; }

    protected String rentalsTable() { return tablePrefix + "region_rentals"; }

    @Override
    public void init() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("QweProtectStones-Pool");
        config.setMaximumPoolSize(Math.max(1, resolvePoolSize()));
        config.setConnectionTimeout(10_000L);
        config.setMaxLifetime(1_800_000L);
        configureHikari(config);

        dataSource = new HikariDataSource(config);

        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            // переносы строго до CREATE TABLE, иначе потеряем данные

            renameLegacyTables(conn, st);

            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + metaTable() + " (" +
                    "key_name VARCHAR(32) PRIMARY KEY, value VARCHAR(64))");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + regionsTable() + " (" +
                    "id VARCHAR(36) PRIMARY KEY," +
                    "world VARCHAR(64) NOT NULL," +
                    "min_x INT NOT NULL, min_y INT NOT NULL, min_z INT NOT NULL," +
                    "max_x INT NOT NULL, max_y INT NOT NULL, max_z INT NOT NULL," +
                    "core_x INT NOT NULL, core_y INT NOT NULL, core_z INT NOT NULL," +
                    "type VARCHAR(64) NOT NULL," +
                    "owner_uuid VARCHAR(36)," +
                    "owner_name VARCHAR(32)," +
                    "durability INT NOT NULL," +
                    "max_durability INT NOT NULL," +
                    "effects TEXT," +
                    "created_at BIGINT NOT NULL," +
                    "attack_count INT NOT NULL DEFAULT 0," +
                    "last_attack_at BIGINT NOT NULL DEFAULT 0," +
                    "last_attacker VARCHAR(32)," +
                    "penalty_until BIGINT NOT NULL DEFAULT 0," +
                    "display_name VARCHAR(128))");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + membersTable() + " (" +
                    "region_id VARCHAR(36) NOT NULL," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(32)," +
                    "trust VARCHAR(16) NOT NULL," +
                    "added_at BIGINT NOT NULL," +
                    "PRIMARY KEY (region_id, player_uuid))");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + flagsTable() + " (" +
                    "region_id VARCHAR(36) NOT NULL," +
                    "flag VARCHAR(32) NOT NULL," +
                    "value " + booleanType() + " NOT NULL," +
                    "PRIMARY KEY (region_id, flag))");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + autoAddTable() + " (" +
                    "uuid VARCHAR(36) PRIMARY KEY, friends TEXT, toggled_off " + booleanType() + ")");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + bansTable() + " (" +
                    "region_id VARCHAR(36) NOT NULL," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(32)," +
                    "PRIMARY KEY (region_id, player_uuid))");

            // таблица игроков нужна автоочистке

            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + playersTable() + " (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "name VARCHAR(32)," +
                    "last_seen BIGINT NOT NULL)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + logTable() + " (" +
                    "region_id VARCHAR(36) NOT NULL," +
                    "at BIGINT NOT NULL," +
                    "player_name VARCHAR(32)," +
                    "action VARCHAR(24)," +
                    "detail VARCHAR(128))");

            // схема 4: рынок, один приват одно объявление

            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + salesTable() + " (" +
                    "region_id VARCHAR(36) PRIMARY KEY," +
                    "seller_id VARCHAR(36) NOT NULL," +
                    "seller_name VARCHAR(32)," +
                    "price DOUBLE NOT NULL," +
                    "created_at BIGINT NOT NULL)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + rentalsTable() + " (" +
                    "region_id VARCHAR(36) PRIMARY KEY," +
                    "owner_id VARCHAR(36) NOT NULL," +
                    "owner_name VARCHAR(32)," +
                    "price DOUBLE NOT NULL," +
                    "duration_minutes INT NOT NULL," +
                    "tenant_id VARCHAR(36)," +
                    "tenant_name VARCHAR(32)," +
                    "rented_until BIGINT NOT NULL DEFAULT 0)");

            // Синтаксис создания индексов у SQLite и MySQL разный — отдаём диалекту.
            createIndexes(st);
        } catch (SQLException e) {
            // без схемы плагин работать не может: пул закрываем, ошибку отдаём наверх,
            // вызывающий (onEnable) обязан отреагировать самоотключением
            close();
            throw new IllegalStateException("Не удалось создать таблицы БД", e);
        }

        applyMigrations();
    }

    private void renameLegacyTables(Connection conn, Statement st) throws SQLException {
        Map<String, String> renames = Map.of(
                tablePrefix + "claims", regionsTable(),
                tablePrefix + "claim_members", membersTable(),
                tablePrefix + "claim_flags", flagsTable(),
                tablePrefix + "claim_bans", bansTable(),
                tablePrefix + "claim_log", logTable());

        for (Map.Entry<String, String> entry : renames.entrySet()) {
            if (!tableExists(conn, entry.getKey()) || tableExists(conn, entry.getValue())) continue;

            st.executeUpdate("ALTER TABLE " + entry.getKey() + " RENAME TO " + entry.getValue());
            log().info("Таблица " + entry.getKey() + " переименована в " + entry.getValue() + ".");
        }
    }

    private boolean tableExists(Connection conn, String table) throws SQLException {
        try (ResultSet tables = conn.getMetaData().getTables(null, null, table, null)) {
            return tables.next();
        }
    }

    private void applyMigrations() {
        int current = readSchemaVersion();
        if (current == SCHEMA_VERSION) return;

        if (current > SCHEMA_VERSION) {
            log().warning("База создана более новой версией плагина (схема " + current
                    + " против " + SCHEMA_VERSION + "). Обновите плагин, чтобы избежать потери данных.");
            return;
        }

        if (current < 2) {
            addColumnIfMissing("attack_count", "INT NOT NULL DEFAULT 0");
            addColumnIfMissing("last_attack_at", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing("last_attacker", "VARCHAR(32)");
        }

        if (current < 3) {
            addColumnIfMissing("display_name", "VARCHAR(128)");
        }

        // схема 5: дедлайн штрафа за атаку переживает рестарт

        if (current < 5) {
            addColumnIfMissing("penalty_until", "BIGINT NOT NULL DEFAULT 0");
        }

        writeSchemaVersion();
        if (current > 0) log().info("Схема базы обновлена: " + current + " -> " + SCHEMA_VERSION);
    }

    private void addColumnIfMissing(String column, String definition) {
        try (Connection conn = dataSource.getConnection()) {
            try (ResultSet columns = conn.getMetaData().getColumns(null, null, regionsTable(), column)) {
                if (columns.next()) return;
            }
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("ALTER TABLE " + regionsTable() + " ADD COLUMN " + column + " " + definition);
                log().info("Добавлена колонка " + regionsTable() + "." + column);
            }
        } catch (SQLException e) {
            log().log(Level.WARNING, "Не удалось добавить колонку " + column, e);
        }
    }

    private int readSchemaVersion() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT value FROM " + metaTable() + " WHERE key_name = 'schema_version'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return Integer.parseInt(rs.getString("value"));
        } catch (SQLException | NumberFormatException e) {
            log().log(Level.WARNING, "Не удалось прочитать версию схемы", e);
        }
        return 0;
    }

    private void writeSchemaVersion() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(metaUpsert())) {
            ps.setString(1, "schema_version");
            ps.setString(2, String.valueOf(SCHEMA_VERSION));
            ps.executeUpdate();
        } catch (SQLException e) {
            log().log(Level.WARNING, "Не удалось записать версию схемы", e);
        }
    }

    protected abstract String metaUpsert();

    @Override
    public List<Region> loadAll() {
        Map<UUID, Region> regions = new HashMap<>();

        try (Connection conn = dataSource.getConnection()) {
            loadRegions(conn, regions);
            loadMembers(conn, regions);
            loadFlags(conn, regions);
            loadBans(conn, regions);
        } catch (SQLException e) {
            log().log(Level.SEVERE, "Не удалось загрузить приваты из базы", e);
        }
        return new ArrayList<>(regions.values());
    }

    private void loadRegions(Connection conn, Map<UUID, Region> target) throws SQLException {
        String sql = "SELECT id, world, min_x, min_y, min_z, max_x, max_y, max_z, core_x, core_y, core_z," +
                " type, owner_uuid, owner_name, durability, max_durability, effects, created_at," +
                " attack_count, last_attack_at, last_attacker, penalty_until," +
                " display_name FROM " + regionsTable();

        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID id = parseUuid(rs.getString("id"));
                if (id == null) continue;

                Region region = new Region(
                        id,
                        rs.getString("world"),
                        new RegionBounds(rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"),
                                rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z")),
                        rs.getInt("core_x"), rs.getInt("core_y"), rs.getInt("core_z"),
                        rs.getString("type"),
                        parseUuid(rs.getString("owner_uuid")),
                        rs.getString("owner_name"),
                        rs.getInt("durability"),
                        rs.getInt("max_durability"),
                        rs.getLong("created_at"));

                region.getEffects().addAll(splitCsv(rs.getString("effects")));
                region.restoreStats(rs.getInt("attack_count"), rs.getLong("last_attack_at"), rs.getString("last_attacker"));
                region.setPenaltyUntil(rs.getLong("penalty_until"));
                region.restoreDecoration(rs.getString("display_name"));
                target.put(id, region);
            }
        }
    }

    private void loadMembers(Connection conn, Map<UUID, Region> regions) throws SQLException {
        String sql = "SELECT region_id, player_uuid, player_name, trust, added_at FROM " + membersTable();

        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Region region = regions.get(parseUuid(rs.getString("region_id")));
                UUID memberId = parseUuid(rs.getString("player_uuid"));
                if (region == null || memberId == null) continue;

                TrustLevel trust = TrustLevel.parse(rs.getString("trust")).orElse(TrustLevel.BUILD);
                region.restoreMember(new RegionMember(memberId, rs.getString("player_name"), trust, rs.getLong("added_at")));
            }
        }
    }

    private void loadFlags(Connection conn, Map<UUID, Region> regions) throws SQLException {
        String sql = "SELECT region_id, flag, value FROM " + flagsTable();

        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Region region = regions.get(parseUuid(rs.getString("region_id")));
                if (region == null) continue;

                // читаем до лямбды, ResultSet бросает проверяемое
                boolean value = rs.getBoolean("value");
                RegionFlag.parse(rs.getString("flag")).ifPresent(flag -> region.setFlag(flag, value));
            }
        }
    }

    @Override
    public void saveAll(Collection<Region> regions) {
        if (regions == null || regions.isEmpty()) return;

        try (Connection conn = dataSource.getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                writeRegions(conn, regions);
                writeMembers(conn, regions);
                writeFlags(conn, regions);
                writeBans(conn, regions);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            log().log(Level.SEVERE, "Не удалось сохранить " + regions.size() + " приват(ов)", e);
        }
    }

    private void writeRegions(Connection conn, Collection<Region> regions) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(regionUpsert())) {
            int batched = 0;
            for (Region region : regions) {
                RegionBounds bounds = region.getBounds();

                ps.setString(1, region.getId().toString());
                ps.setString(2, region.getWorldName());
                ps.setInt(3, bounds.minX());
                ps.setInt(4, bounds.minY());
                ps.setInt(5, bounds.minZ());
                ps.setInt(6, bounds.maxX());
                ps.setInt(7, bounds.maxY());
                ps.setInt(8, bounds.maxZ());
                ps.setInt(9, region.getCoreX());
                ps.setInt(10, region.getCoreY());
                ps.setInt(11, region.getCoreZ());
                ps.setString(12, region.getTypeId());
                ps.setString(13, region.getOwnerId() == null ? null : region.getOwnerId().toString());
                ps.setString(14, region.getOwnerName());
                ps.setInt(15, region.getDurability());
                ps.setInt(16, region.getMaxDurability());
                ps.setString(17, String.join(",", region.getEffects()));
                ps.setLong(18, region.getCreatedAt());
                ps.setInt(19, region.getAttackCount());
                ps.setLong(20, region.getLastAttackAt());
                ps.setString(21, region.getLastAttackerName());
                ps.setLong(22, region.getPenaltyUntil());
                ps.setString(23, region.getDisplayName());
                ps.addBatch();

                if (++batched % batchSize() == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
    }

    private void writeMembers(Connection conn, Collection<Region> regions) throws SQLException {
        try (PreparedStatement delete = conn.prepareStatement("DELETE FROM " + membersTable() + " WHERE region_id = ?");
             PreparedStatement insert = conn.prepareStatement(memberUpsert())) {

            for (Region region : regions) {
                delete.setString(1, region.getId().toString());
                delete.addBatch();

                for (RegionMember member : region.getMembers()) {
                    insert.setString(1, region.getId().toString());
                    insert.setString(2, member.uuid().toString());
                    insert.setString(3, member.name());
                    insert.setString(4, member.trust().name());
                    insert.setLong(5, member.addedAt());
                    insert.addBatch();
                }
            }
            delete.executeBatch();
            insert.executeBatch();
        }
    }

    private void writeFlags(Connection conn, Collection<Region> regions) throws SQLException {
        try (PreparedStatement delete = conn.prepareStatement("DELETE FROM " + flagsTable() + " WHERE region_id = ?");
             PreparedStatement insert = conn.prepareStatement(flagUpsert())) {

            for (Region region : regions) {
                delete.setString(1, region.getId().toString());
                delete.addBatch();

                for (Map.Entry<RegionFlag, Boolean> entry : region.getFlagOverrides().entrySet()) {
                    insert.setString(1, region.getId().toString());
                    insert.setString(2, entry.getKey().name());
                    insert.setBoolean(3, entry.getValue());
                    insert.addBatch();
                }
            }
            delete.executeBatch();
            insert.executeBatch();
        }
    }

    @Override
    public void deleteAll(Collection<UUID> regionIds) {
        if (regionIds == null || regionIds.isEmpty()) return;

        try (Connection conn = dataSource.getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement regions = conn.prepareStatement("DELETE FROM " + regionsTable() + " WHERE id = ?");
                 PreparedStatement members = conn.prepareStatement("DELETE FROM " + membersTable() + " WHERE region_id = ?");
                 PreparedStatement flags = conn.prepareStatement("DELETE FROM " + flagsTable() + " WHERE region_id = ?");
                 PreparedStatement bans = conn.prepareStatement("DELETE FROM " + bansTable() + " WHERE region_id = ?");
                 PreparedStatement log = conn.prepareStatement("DELETE FROM " + logTable() + " WHERE region_id = ?")) {

                for (UUID id : regionIds) {
                    String raw = id.toString();
                    regions.setString(1, raw);
                    regions.addBatch();
                    members.setString(1, raw);
                    members.addBatch();
                    flags.setString(1, raw);
                    flags.addBatch();
                    bans.setString(1, raw);
                    bans.addBatch();
                    log.setString(1, raw);
                    log.addBatch();
                }
                members.executeBatch();
                flags.executeBatch();
                bans.executeBatch();
                log.executeBatch();
                regions.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            log().log(Level.SEVERE, "Не удалось удалить приваты из базы", e);
        }
    }

    private void loadBans(Connection conn, Map<UUID, Region> regions) throws SQLException {
        String sql = "SELECT region_id, player_uuid, player_name FROM " + bansTable();

        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Region region = regions.get(parseUuid(rs.getString("region_id")));
                UUID banned = parseUuid(rs.getString("player_uuid"));
                if (region == null || banned == null) continue;

                region.restoreBan(banned, rs.getString("player_name"));
            }
        }
    }

    private void writeBans(Connection conn, Collection<Region> regions) throws SQLException {
        try (PreparedStatement delete = conn.prepareStatement("DELETE FROM " + bansTable() + " WHERE region_id = ?");
             PreparedStatement insert = conn.prepareStatement(banUpsert())) {

            for (Region region : regions) {
                delete.setString(1, region.getId().toString());
                delete.addBatch();

                for (Map.Entry<UUID, String> entry : region.getBannedPlayers().entrySet()) {
                    insert.setString(1, region.getId().toString());
                    insert.setString(2, entry.getKey().toString());
                    insert.setString(3, entry.getValue());
                    insert.addBatch();
                }
            }
            delete.executeBatch();
            insert.executeBatch();
        }
    }

    @Override
    public void touchPlayer(UUID uuid, String name, long lastSeen) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(playerUpsert())) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setLong(3, lastSeen);
            ps.executeUpdate();
        } catch (SQLException e) {
            log().log(Level.WARNING, "Не удалось обновить время входа " + uuid, e);
        }
    }

    @Override
    public Map<UUID, Long> loadLastSeen() {
        Map<UUID, Long> result = new HashMap<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT uuid, last_seen FROM " + playersTable());
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString("uuid"));
                if (uuid != null) result.put(uuid, rs.getLong("last_seen"));
            }
        } catch (SQLException e) {
            log().log(Level.WARNING, "Не удалось загрузить время последнего входа игроков", e);
        }
        return result;
    }

    @Override
    public void appendLog(Collection<RegionLogEntry> entries) {
        if (entries == null || entries.isEmpty()) return;

        String sql = "INSERT INTO " + logTable() + " (region_id, at, player_name, action, detail) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int batched = 0;
                for (RegionLogEntry entry : entries) {
                    ps.setString(1, entry.regionId().toString());
                    ps.setLong(2, entry.at());
                    ps.setString(3, entry.playerName());
                    ps.setString(4, entry.action());
                    ps.setString(5, entry.detail());
                    ps.addBatch();

                    if (++batched % batchSize() == 0) ps.executeBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            log().log(Level.WARNING, "Не удалось записать журнал действий", e);
        }
    }

    @Override
    public List<RegionLogEntry> readLog(UUID regionId, int limit) {
        List<RegionLogEntry> entries = new ArrayList<>();
        String sql = "SELECT at, player_name, action, detail FROM " + logTable()
                + " WHERE region_id = ? ORDER BY at DESC LIMIT ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, regionId.toString());
            ps.setInt(2, Math.max(1, limit));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new RegionLogEntry(regionId, rs.getLong("at"),
                            rs.getString("player_name"), rs.getString("action"), rs.getString("detail")));
                }
            }
        } catch (SQLException e) {
            log().log(Level.WARNING, "Не удалось прочитать журнал действий", e);
        }
        return entries;
    }

    @Override
    public int pruneLog(long olderThan) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM " + logTable() + " WHERE at < ?")) {
            ps.setLong(1, olderThan);
            return ps.executeUpdate();
        } catch (SQLException e) {
            log().log(Level.WARNING, "Не удалось очистить журнал действий", e);
            return 0;
        }
    }

    @Override
    public void loadAutoAdd(UUID uuid, BiConsumer<Set<String>, Boolean> callback) {
        Set<String> friends = new HashSet<>();
        boolean toggledOff = false;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT friends, toggled_off FROM " + autoAddTable() + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    friends.addAll(splitCsv(rs.getString("friends")));
                    toggledOff = rs.getBoolean("toggled_off");
                }
            }
        } catch (SQLException e) {
            log().log(Level.WARNING, "Не удалось загрузить авто-добавление для " + uuid, e);
        }
        callback.accept(friends, toggledOff);
    }

    @Override
    public void saveAutoAdd(UUID uuid, Set<String> friends, boolean toggledOff) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(autoAddUpsert())) {
            ps.setString(1, uuid.toString());
            ps.setString(2, friends == null ? "" : String.join(",", friends));
            ps.setBoolean(3, toggledOff);
            ps.executeUpdate();
        } catch (SQLException e) {
            log().log(Level.WARNING, "Не удалось сохранить авто-добавление для " + uuid, e);
        }
    }

    @Override
    public Map<UUID, RegionSale> loadSales() {
        Map<UUID, RegionSale> result = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + salesTable());
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RegionSale sale = new RegionSale(
                        parseUuid(rs.getString("region_id")),
                        parseUuid(rs.getString("seller_id")),
                        rs.getString("seller_name"),
                        rs.getDouble("price"),
                        rs.getLong("created_at"));
                if (sale.regionId() != null && sale.sellerId() != null) result.put(sale.regionId(), sale);
            }
        } catch (SQLException e) {
            log().log(Level.WARNING, "Не удалось прочитать объявления о продаже", e);
        }
        return result;
    }

    @Override
    public void saveSale(RegionSale sale) {
        if (sale == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO " + salesTable() + " (region_id, seller_id, seller_name, price, created_at) VALUES (?,?,?,?,?)")) {
            ps.setString(1, sale.regionId().toString());
            ps.setString(2, sale.sellerId().toString());
            ps.setString(3, sale.sellerName());
            ps.setDouble(4, sale.price());
            ps.setLong(5, sale.createdAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            log().log(Level.WARNING, "Не удалось сохранить объявление о продаже", e);
        }
    }

    @Override
    public void deleteSale(UUID regionId) {
        if (regionId == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM " + salesTable() + " WHERE region_id = ?")) {
            ps.setString(1, regionId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            log().log(Level.WARNING, "Не удалось удалить объявление о продаже", e);
        }
    }

    @Override
    public Map<UUID, RegionRental> loadRentals() {
        Map<UUID, RegionRental> result = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + rentalsTable());
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RegionRental rental = new RegionRental(
                        parseUuid(rs.getString("region_id")),
                        parseUuid(rs.getString("owner_id")),
                        rs.getString("owner_name"),
                        rs.getDouble("price"),
                        rs.getInt("duration_minutes"),
                        parseUuid(rs.getString("tenant_id")),
                        rs.getString("tenant_name"),
                        rs.getLong("rented_until"));
                if (rental.regionId() != null && rental.ownerId() != null) result.put(rental.regionId(), rental);
            }
        } catch (SQLException e) {
            log().log(Level.WARNING, "Не удалось прочитать условия аренды", e);
        }
        return result;
    }

    @Override
    public void saveRental(RegionRental rental) {
        if (rental == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO " + rentalsTable() + " (region_id, owner_id, owner_name, price, duration_minutes, tenant_id, tenant_name, rented_until) VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setString(1, rental.regionId().toString());
            ps.setString(2, rental.ownerId().toString());
            ps.setString(3, rental.ownerName());
            ps.setDouble(4, rental.price());
            ps.setInt(5, rental.durationMinutes());
            ps.setString(6, rental.tenantId() == null ? null : rental.tenantId().toString());
            ps.setString(7, rental.tenantName());
            ps.setLong(8, rental.rentedUntil());
            ps.executeUpdate();
        } catch (SQLException e) {
            log().log(Level.WARNING, "Не удалось сохранить условия аренды", e);
        }
    }

    @Override
    public void deleteRental(UUID regionId) {
        if (regionId == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM " + rentalsTable() + " WHERE region_id = ?")) {
            ps.setString(1, regionId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            log().log(Level.WARNING, "Не удалось удалить условия аренды", e);
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
