package org.qweyns.pshologramm.storage.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.models.RegionData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractSqlDao implements RegionDao {
    protected final PSHologramm plugin;
    protected final String tablePrefix;
    protected HikariDataSource dataSource;

    public AbstractSqlDao(PSHologramm plugin) {
        this.plugin = plugin;
        this.tablePrefix = plugin.getConfigManager().getConfig().getString("database.table_prefix", "psholo_");
    }

    protected abstract void configureHikari(HikariConfig config);
    protected abstract String getUpsertQuery();

    @Override
    public void init() {
        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(10000);
        configureHikari(config);

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
                if (data.getEffects().size() == 1 && data.getEffects().get(0).isEmpty()) data.getEffects().clear();
                map.put(data.getId(), data);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return map;
    }

    @Override
    public void saveAll(Collection<RegionData> regions) {
        if (regions.isEmpty()) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(getUpsertQuery())) {

            conn.setAutoCommit(false);
            for (RegionData data : regions) {
                ps.setString(1, data.getId()); ps.setString(2, data.getType()); ps.setString(3, data.getOwner());
                ps.setString(4, data.getMaterial()); ps.setInt(5, data.getDurability()); ps.setInt(6, data.getMaxDurability());
                ps.setString(7, data.getWorld()); ps.setDouble(8, data.getX()); ps.setDouble(9, data.getY()); ps.setDouble(10, data.getZ());
                ps.setString(11, String.join(",", data.getEffects()));
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void deleteAll(Collection<String> ids) {
        if (ids.isEmpty()) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM " + tablePrefix + "regions WHERE id = ?")) {
            conn.setAutoCommit(false);
            for (String id : ids) {
                ps.setString(1, id);
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }
}
