package org.qweyns.pshologramm.storage.dao;

import com.zaxxer.hikari.HikariConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.qweyns.pshologramm.PSHologramm;

public class MysqlDao extends AbstractSqlDao {
    public MysqlDao(PSHologramm plugin) { super(plugin); }

    @Override
    protected void configureHikari(HikariConfig config) {
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        String host = cfg.getString("database.host", "localhost");
        int port = cfg.getInt("database.port", 3306);
        String db = cfg.getString("database.database", "pshologramm");
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&characterEncoding=utf8");
        config.setUsername(cfg.getString("database.username", "root"));
        config.setPassword(cfg.getString("database.password", ""));
    }

    @Override
    protected String getAutoAddUpsertQuery() {
        return "INSERT INTO " + tablePrefix + "autoadd (uuid, friends, toggled_off) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE friends=VALUES(friends), toggled_off=VALUES(toggled_off)";
    }

    @Override
    protected String getUpsertQuery() {
        return "INSERT INTO " + tablePrefix + "regions (id, type, owner, material, durability, maxDurability, world, x, y, z, effects) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE type=VALUES(type), owner=VALUES(owner), material=VALUES(material), " +
                "durability=VALUES(durability), maxDurability=VALUES(maxDurability), effects=VALUES(effects)";
    }
}
