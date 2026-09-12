package org.qweyns.qweprotectstones.storage.dao;

import com.zaxxer.hikari.HikariConfig;
import org.qweyns.qweprotectstones.QweProtectStones;

import java.io.File;
import java.sql.SQLException;
import java.sql.Statement;

public class SqliteRegionDao extends AbstractSqlRegionDao {

    private File testDbFile;

    public SqliteRegionDao(QweProtectStones plugin) {
        super(plugin);
    }

    public SqliteRegionDao(File testDbFile) {
        super(null);
        this.testDbFile = testDbFile;
    }

    @Override
    protected void configureHikari(HikariConfig config) {
        if (testDbFile != null) {
            config.setJdbcUrl("jdbc:sqlite:" + testDbFile.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(1);
            return;
        }

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.isDirectory() && !dataFolder.mkdirs()) {
            log().warning("Не удалось создать папку плагина для файла базы данных.");
        }

        File dbFile = new File(dataFolder, "data.db");
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");

        // sqlite не умеет параллельную запись, пул всегда 1
        config.setMaximumPoolSize(1);
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("busy_timeout", "5000");
        config.addDataSourceProperty("foreign_keys", "ON");
    }

    @Override
    protected String booleanType() {
        return "INTEGER";
    }

    @Override
    protected void createIndexes(Statement statement) throws SQLException {
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + ownerIndexName() + " ON " + regionsTable() + " (owner_uuid)");
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + worldIndexName() + " ON " + regionsTable() + " (world)");
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "log_region ON " + logTable() + " (region_id, at)");
    }

    @Override
    protected String regionUpsert() {
        return "INSERT OR REPLACE INTO " + regionsTable() + " (id, world, min_x, min_y, min_z, max_x, max_y, max_z," +
                " core_x, core_y, core_z, type, owner_uuid, owner_name, durability, max_durability, effects, created_at," +
                " attack_count, last_attack_at, last_attacker, penalty_until, display_name)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    protected String memberUpsert() {
        return "INSERT OR REPLACE INTO " + membersTable() +
                " (region_id, player_uuid, player_name, trust, added_at) VALUES (?, ?, ?, ?, ?)";
    }

    @Override
    protected String flagUpsert() {
        return "INSERT OR REPLACE INTO " + flagsTable() + " (region_id, flag, value) VALUES (?, ?, ?)";
    }

    @Override
    protected String autoAddUpsert() {
        return "INSERT OR REPLACE INTO " + autoAddTable() + " (uuid, friends, toggled_off) VALUES (?, ?, ?)";
    }

    @Override
    protected String banUpsert() {
        return "INSERT OR REPLACE INTO " + bansTable() + " (region_id, player_uuid, player_name) VALUES (?, ?, ?)";
    }

    @Override
    protected String playerUpsert() {
        return "INSERT OR REPLACE INTO " + playersTable() + " (uuid, name, last_seen) VALUES (?, ?, ?)";
    }

    @Override
    protected String metaUpsert() {
        return "INSERT OR REPLACE INTO " + metaTable() + " (key_name, value) VALUES (?, ?)";
    }
}
