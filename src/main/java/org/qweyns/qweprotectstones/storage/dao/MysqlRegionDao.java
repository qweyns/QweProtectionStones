package org.qweyns.qweprotectstones.storage.dao;

import com.zaxxer.hikari.HikariConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.qweyns.qweprotectstones.QweProtectStones;

import java.sql.SQLException;
import java.sql.Statement;

public class MysqlRegionDao extends AbstractSqlRegionDao {

    private static final int ER_DUP_KEYNAME = 1061;

    public MysqlRegionDao(QweProtectStones plugin) {
        super(plugin);
    }

    @Override
    protected void configureHikari(HikariConfig config) {
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        String host = cfg.getString("database.host", "localhost");
        int port = cfg.getInt("database.port", 3306);
        String database = cfg.getString("database.database", "qweprotectstones");
        boolean useSsl = cfg.getBoolean("database.use_ssl", false);

        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl
                + "&useUnicode=true&characterEncoding=utf8"
                + "&rewriteBatchedStatements=true"
                + "&serverTimezone=UTC");
        config.setUsername(cfg.getString("database.username", "root"));
        config.setPassword(cfg.getString("database.password", ""));

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
    }

    @Override
    protected String booleanType() {
        return "BOOLEAN";
    }

    @Override
    protected void createIndexes(Statement statement) throws SQLException {
        // в mysql нет IF NOT EXISTS для индексов, ловим 1061

        createIndexIgnoringDuplicate(statement, "CREATE INDEX " + ownerIndexName() + " ON " + regionsTable() + " (owner_uuid)");
        createIndexIgnoringDuplicate(statement, "CREATE INDEX " + worldIndexName() + " ON " + regionsTable() + " (world)");
        createIndexIgnoringDuplicate(statement, "CREATE INDEX idx_" + tablePrefix + "log_region ON " + logTable() + " (region_id, at)");
    }

    private void createIndexIgnoringDuplicate(Statement statement, String sql) throws SQLException {
        try {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            if (e.getErrorCode() != ER_DUP_KEYNAME) throw e;
        }
    }

    @Override
    protected String regionUpsert() {
        return "INSERT INTO " + regionsTable() + " (id, world, min_x, min_y, min_z, max_x, max_y, max_z," +
                " core_x, core_y, core_z, type, owner_uuid, owner_name, durability, max_durability, effects, created_at," +
                " attack_count, last_attack_at, last_attacker, penalty_until, display_name)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" +
                " ON DUPLICATE KEY UPDATE world = VALUES(world)," +
                " min_x = VALUES(min_x), min_y = VALUES(min_y), min_z = VALUES(min_z)," +
                " max_x = VALUES(max_x), max_y = VALUES(max_y), max_z = VALUES(max_z)," +
                " core_x = VALUES(core_x), core_y = VALUES(core_y), core_z = VALUES(core_z)," +
                " type = VALUES(type), owner_uuid = VALUES(owner_uuid), owner_name = VALUES(owner_name)," +
                " durability = VALUES(durability), max_durability = VALUES(max_durability), effects = VALUES(effects)," +
                " attack_count = VALUES(attack_count), last_attack_at = VALUES(last_attack_at)," +
                " last_attacker = VALUES(last_attacker), penalty_until = VALUES(penalty_until),"
                + " display_name = VALUES(display_name)";
    }

    @Override
    protected String memberUpsert() {
        return "INSERT INTO " + membersTable() + " (region_id, player_uuid, player_name, trust, added_at)" +
                " VALUES (?, ?, ?, ?, ?)" +
                " ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), trust = VALUES(trust)";
    }

    @Override
    protected String flagUpsert() {
        return "INSERT INTO " + flagsTable() + " (region_id, flag, value) VALUES (?, ?, ?)" +
                " ON DUPLICATE KEY UPDATE value = VALUES(value)";
    }

    @Override
    protected String autoAddUpsert() {
        return "INSERT INTO " + autoAddTable() + " (uuid, friends, toggled_off) VALUES (?, ?, ?)" +
                " ON DUPLICATE KEY UPDATE friends = VALUES(friends), toggled_off = VALUES(toggled_off)";
    }

    @Override
    protected String banUpsert() {
        return "INSERT INTO " + bansTable() + " (region_id, player_uuid, player_name) VALUES (?, ?, ?)" +
                " ON DUPLICATE KEY UPDATE player_name = VALUES(player_name)";
    }

    @Override
    protected String playerUpsert() {
        return "INSERT INTO " + playersTable() + " (uuid, name, last_seen) VALUES (?, ?, ?)" +
                " ON DUPLICATE KEY UPDATE name = VALUES(name), last_seen = VALUES(last_seen)";
    }

    @Override
    protected String metaUpsert() {
        return "INSERT INTO " + metaTable() + " (key_name, value) VALUES (?, ?)" +
                " ON DUPLICATE KEY UPDATE value = VALUES(value)";
    }
}
