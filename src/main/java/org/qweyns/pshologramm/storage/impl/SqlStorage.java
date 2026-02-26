package org.qweyns.pshologramm.storage.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.models.RegionData;
import org.qweyns.pshologramm.storage.StorageProvider;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SqlStorage implements StorageProvider {
    private final PSHologramm plugin;
    private final String type;
    private HikariDataSource dataSource;
    private final String tablePrefix;

    public SqlStorage(PSHologramm plugin, String type) {
        this.plugin = plugin;
        this.type = type.toUpperCase();
        this.tablePrefix = plugin.getConfigManager().getConfig().getString("database.table_prefix", "psholo_");
    }

    @Override
    public void init() {
        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(10000);

        if (type.equals("MYSQL")) {
            String host = plugin.getConfigManager().getConfig().getString("database.host", "localhost");
            int port = plugin.getConfigManager().getConfig().getInt("database.port", 3306);
            String db = plugin.getConfigManager().getConfig().getString("database.database", "pshologramm");
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&characterEncoding=utf8");
            config.setUsername(plugin.getConfigManager().getConfig().getString("database.username", "root"));
            config.setPassword(plugin.getConfigManager().getConfig().getString("database.password", ""));
        } else if (type.equals("SQLITE")) {
            File dbFile = new File(plugin.getDataFolder(), "data.db");
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
        } else if (type.equals("H2")) {
            File dbFile = new File(plugin.getDataFolder(), "data");
            config.setJdbcUrl("jdbc:h2:file:" + dbFile.getAbsolutePath() + ";MODE=MySQL");
            config.setDriverClassName("org.h2.Driver");
        }

        dataSource = new HikariDataSource(config);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + tablePrefix + "regions (" +
                             "id VARCHAR(64) PRIMARY KEY, type VARCHAR(64), owner VARCHAR(32), " +
                             "material VARCHAR(64), durability INT, maxDurability INT, " +
                             "world VARCHAR(64), x DOUBLE, y DOUBLE, z DOUBLE, effects TEXT)"
             )) {
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка создания таблиц БД: " + e.getMessage());
        }
    }

    @Override
    public Map<String, RegionData> loadAll() {
        Map<String, RegionData> map = new ConcurrentHashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + tablePrefix + "regions");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                RegionData data = new RegionData(
                        rs.getString("id"), rs.getString("type"), rs.getString("owner"),
                        rs.getString("material"), rs.getInt("durability"), rs.getInt("maxDurability"),
                        rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        Arrays.asList(rs.getString("effects").split(","))
                );
                // Очистка пустого элемента, если строка эффектов была пустой
                if (data.getEffects().size() == 1 && data.getEffects().get(0).isEmpty()) {
                    data.getEffects().clear();
                }
                map.put(data.getId(), data);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }

    @Override
    public void save(RegionData data) {
        String sql = "INSERT INTO " + tablePrefix + "regions (id, type, owner, material, durability, maxDurability, world, x, y, z, effects) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE type=VALUES(type), owner=VALUES(owner), material=VALUES(material), " +
                "durability=VALUES(durability), maxDurability=VALUES(maxDurability), effects=VALUES(effects)";

        if (type.equals("SQLITE")) {
            sql = "INSERT OR REPLACE INTO " + tablePrefix + "regions (id, type, owner, material, durability, maxDurability, world, x, y, z, effects) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, data.getId());
            ps.setString(2, data.getType());
            ps.setString(3, data.getOwner());
            ps.setString(4, data.getMaterial());
            ps.setInt(5, data.getDurability());
            ps.setInt(6, data.getMaxDurability());
            ps.setString(7, data.getWorld());
            ps.setDouble(8, data.getX());
            ps.setDouble(9, data.getY());
            ps.setDouble(10, data.getZ());
            ps.setString(11, String.join(",", data.getEffects()));

            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void remove(String id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM " + tablePrefix + "regions WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
