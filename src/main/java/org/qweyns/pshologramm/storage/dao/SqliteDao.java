package org.qweyns.pshologramm.storage.dao;

import com.zaxxer.hikari.HikariConfig;
import org.qweyns.pshologramm.PSHologramm;
import java.io.File;

public class SqliteDao extends AbstractSqlDao {
    public SqliteDao(PSHologramm plugin) { super(plugin); }

    @Override
    protected void configureHikari(HikariConfig config) {
        File dbFile = new File(plugin.getDataFolder(), "data.db");
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
    }

    @Override
    protected String getAutoAddUpsertQuery() {
        return "INSERT OR REPLACE INTO " + tablePrefix + "autoadd (uuid, friends, toggled_off) VALUES (?, ?, ?)";
    }

    @Override
    protected String getUpsertQuery() {
        return "INSERT OR REPLACE INTO " + tablePrefix + "regions (id, type, owner, material, durability, maxDurability, world, x, y, z, effects) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }
}
