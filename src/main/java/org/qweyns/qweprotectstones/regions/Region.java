package org.qweyns.qweprotectstones.regions;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Region implements Bounded {

    private final UUID id;
    private final String world;
    // при смене границ/типа индекс пересобирает RegionManager

    private volatile RegionBounds bounds;
    private volatile int coreX, coreY, coreZ;
    private volatile String typeId;
    private final long createdAt;

    private volatile UUID ownerId;
    private volatile String ownerName;

    private final Map<UUID, RegionMember> members = new ConcurrentHashMap<>();
    private final Map<RegionFlag, Boolean> flagOverrides = new ConcurrentHashMap<>();
    private final List<String> effects = new CopyOnWriteArrayList<>();

    private volatile int durability;
    private volatile int maxDurability;

    private final Map<UUID, String> bannedPlayers = new ConcurrentHashMap<>();

    private volatile String displayName = "";

    private volatile int attackCount;
    private volatile long lastAttackAt;
    private volatile String lastAttackerName = "";
    // до какого времени (epoch ms) действует штраф за атаку: 0 — штрафа нет
    private volatile long penaltyUntil;

    private final AtomicLong version = new AtomicLong();

    public Region(UUID id, String world, RegionBounds bounds, int coreX, int coreY, int coreZ,
                 String typeId, UUID ownerId, String ownerName,
                 int durability, int maxDurability, long createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.world = Objects.requireNonNull(world, "world");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.coreX = coreX;
        this.coreY = coreY;
        this.coreZ = coreZ;
        this.typeId = Objects.requireNonNull(typeId, "typeId");
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.maxDurability = Math.max(1, maxDurability);
        this.durability = clampDurability(durability);
        this.createdAt = createdAt;
    }

    // Неблокирующая резервация сложной операции. Вложенные callbacks не могут
    // повторно купить/удалить/повредить тот же приват; чужой поток не ждёт Bukkit-событие.
    private final java.util.concurrent.atomic.AtomicReference<Operation> operation =
            new java.util.concurrent.atomic.AtomicReference<>();

    public final class Operation implements AutoCloseable {
        private Operation() { }
        public boolean owns(Region region) { return region == Region.this && operation.get() == this; }
        @Override public void close() { operation.compareAndSet(this, null); }
    }

    public Operation tryOperation() {
        Operation token = new Operation();
        return operation.compareAndSet(null, token) ? token : null;
    }

    /** Копия данных без Bukkit; сериализуется независимо от последующих изменений. */
    public synchronized Region snapshot() {
        Region copy = new Region(id, world, bounds, coreX, coreY, coreZ, typeId,
                ownerId, ownerName, durability, maxDurability, createdAt);
        copy.members.putAll(members);
        copy.flagOverrides.putAll(flagOverrides);
        copy.effects.addAll(effects);
        copy.bannedPlayers.putAll(bannedPlayers);
        copy.displayName = displayName;
        copy.attackCount = attackCount;
        copy.lastAttackAt = lastAttackAt;
        copy.lastAttackerName = lastAttackerName;
        copy.penaltyUntil = penaltyUntil;
        return copy;
    }

    public synchronized void replaceEffect(String name, int level) {
        String normalized = name.toUpperCase(java.util.Locale.ROOT);
        effects.removeIf(e -> e.equalsIgnoreCase(normalized)
                || e.toUpperCase(java.util.Locale.ROOT).startsWith(normalized + ":"));
        effects.add(normalized + ":" + level);
        touch();
    }

    public UUID getId() { return id; }

    public String getShortId() { return id.toString().substring(0, 8); }

    public String getWorldName() { return world; }

    public World getWorld() { return Bukkit.getWorld(world); }

    public RegionBounds getBounds() { return bounds; }

    public synchronized void setBounds(RegionBounds bounds) { this.bounds = Objects.requireNonNull(bounds, "bounds"); touch(); }

    public String getTypeId() { return typeId; }

    public synchronized void setTypeId(String typeId) { this.typeId = Objects.requireNonNull(typeId, "typeId"); touch(); }

    public synchronized void setCore(int coreX, int coreY, int coreZ) {
        this.coreX = coreX;
        this.coreY = coreY;
        this.coreZ = coreZ;
        touch();
    }

    public long getCreatedAt() { return createdAt; }

    public int getCoreX() { return coreX; }
    public int getCoreY() { return coreY; }
    public int getCoreZ() { return coreZ; }

    public Location getCoreLocation() {
        World bukkitWorld = getWorld();
        return bukkitWorld == null ? null : new Location(bukkitWorld, coreX, coreY, coreZ);
    }

    public Location getHomeLocation() {
        World bukkitWorld = getWorld();
        if (bukkitWorld == null) return null;
        return new Location(bukkitWorld, coreX + 0.5, coreY + 1, coreZ + 0.5);
    }

    public boolean isCore(int x, int y, int z) {
        return x == coreX && y == coreY && z == coreZ;
    }

    public boolean isCore(Location loc) {
        return loc != null && world.equals(loc.getWorld() == null ? null : loc.getWorld().getName())
                && isCore(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        return world.equals(loc.getWorld().getName())
                && bounds.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public UUID getOwnerId() { return ownerId; }

    public String getOwnerName() { return ownerName != null ? ownerName : ""; }

    public void transferOwnership(UUID newOwnerId, String newOwnerName) {
        transferOwnership(newOwnerId, newOwnerName, null);
    }

    /**
     * Смена владельца. Участники и баны сохраняются — новый владелец сам решит, кого оставить.
     * @param previousOwnerRole роль бывшего владельца в привате; null — он не остаётся участником
     */
    public synchronized void transferOwnership(UUID newOwnerId, String newOwnerName, TrustLevel previousOwnerRole) {
        UUID previousOwner = this.ownerId;
        String previousName = this.ownerName;

        this.ownerId = newOwnerId;
        this.ownerName = newOwnerName;
        members.remove(newOwnerId);
        bannedPlayers.remove(newOwnerId);
        touch();

        if (previousOwner != null && !previousOwner.equals(newOwnerId) && previousOwnerRole != null
                && !previousOwnerRole.isOwner()) {
            members.put(previousOwner, new RegionMember(previousOwner, previousName, previousOwnerRole, System.currentTimeMillis()));
        }
    }

    public boolean isOwner(UUID uuid) {
        return ownerId != null && ownerId.equals(uuid);
    }

    public Collection<RegionMember> getMembers() {
        return members.values();
    }

    public Optional<RegionMember> getMember(UUID uuid) {
        return Optional.ofNullable(members.get(uuid));
    }

    public int getMemberCount() {
        return members.size();
    }

    public synchronized void setMember(UUID uuid, String name, TrustLevel trust) {
        if (isOwner(uuid)) return;
        touch();
        members.compute(uuid, (key, existing) -> existing == null
                ? new RegionMember(uuid, name, trust, System.currentTimeMillis())
                : new RegionMember(uuid, name != null ? name : existing.name(), trust, existing.addedAt()));
    }

    public synchronized void restoreMember(RegionMember member) {
        if (member != null && !isOwner(member.uuid())) members.put(member.uuid(), member);
    }

    public synchronized boolean removeMember(UUID uuid) {
        boolean removed = members.remove(uuid) != null;
        if (removed) touch();
        return removed;
    }

    public TrustLevel getTrust(UUID uuid) {
        if (uuid == null) return null;
        if (isOwner(uuid)) return TrustLevel.owner();

        RegionMember member = members.get(uuid);
        return member != null ? member.trust() : null;
    }

    public boolean hasTrust(UUID uuid, TrustLevel required) {
        TrustLevel trust = getTrust(uuid);
        return trust != null && trust.atLeast(required);
    }

    public boolean hasTrust(Player player, TrustLevel required) {
        return player != null && hasTrust(player.getUniqueId(), required);
    }

    /** Только чтение: изменения — через setFlag/resetFlag, иначе они не попадут в базу. */
    public Map<RegionFlag, Boolean> getFlagOverrides() {
        return java.util.Collections.unmodifiableMap(flagOverrides);
    }

    public Optional<Boolean> getFlagOverride(RegionFlag flag) {
        return Optional.ofNullable(flagOverrides.get(flag));
    }

    public synchronized void setFlag(RegionFlag flag, boolean value) {
        flagOverrides.put(flag, value);
        touch();
    }

    public synchronized void resetFlag(RegionFlag flag) {
        flagOverrides.remove(flag);
        touch();
    }

    public int getDurability() { return durability; }

    public synchronized void setDurability(int durability) {
        this.durability = clampDurability(durability);
        touch();
    }

    public int getMaxDurability() { return maxDurability; }

    public synchronized void setMaxDurability(int maxDurability) {
        this.maxDurability = Math.max(1, maxDurability);
        this.durability = clampDurability(this.durability);
        touch();
    }

    private int clampDurability(int value) {
        return Math.max(0, Math.min(value, maxDurability));
    }

    public List<String> getEffects() { return effects; }

    public boolean hasEffect(String effectName) {
        String prefix = effectName.toUpperCase(java.util.Locale.ROOT);
        for (String effect : effects) {
            String upper = effect.toUpperCase(java.util.Locale.ROOT);
            if (upper.equals(prefix) || upper.startsWith(prefix + ":")) return true;
        }
        return false;
    }

    public boolean isBanned(UUID uuid) {
        return uuid != null && bannedPlayers.containsKey(uuid);
    }

    public Map<UUID, String> getBannedPlayers() {
        return bannedPlayers;
    }

    public synchronized void ban(UUID uuid, String name) {
        if (uuid == null || isOwner(uuid)) return;

        bannedPlayers.put(uuid, name == null ? "" : name);
        members.remove(uuid);
        touch();
    }

    public synchronized boolean unban(UUID uuid) {
        boolean removed = bannedPlayers.remove(uuid) != null;
        if (removed) touch();
        return removed;
    }

    public synchronized void restoreBan(UUID uuid, String name) {
        if (uuid != null && !isOwner(uuid)) bannedPlayers.put(uuid, name == null ? "" : name);
    }

    public String getDisplayName() { return displayName; }

    public boolean hasDisplayName() { return !displayName.isEmpty(); }

    public String getLabel() {
        return RegionText.label(displayName, getOwnerName(), getShortId());
    }

    public synchronized void setDisplayName(String value) {
        this.displayName = RegionText.normalize(value);
        touch();
    }

    public synchronized void restoreDecoration(String displayName) {
        this.displayName = RegionText.normalize(displayName);
    }

    public int getAttackCount() { return attackCount; }

    public long getPenaltyUntil() { return penaltyUntil; }

    public synchronized void setPenaltyUntil(long penaltyUntil) { this.penaltyUntil = penaltyUntil; touch(); }

    public long getLastAttackAt() { return lastAttackAt; }

    public String getLastAttackerName() { return lastAttackerName == null ? "" : lastAttackerName; }

    public synchronized void recordAttack(String attackerName) {
        attackCount++;
        lastAttackAt = System.currentTimeMillis();
        if (attackerName != null && !attackerName.isBlank()) lastAttackerName = attackerName;
        touch();
    }

    public synchronized void restoreStats(int attackCount, long lastAttackAt, String lastAttackerName) {
        this.attackCount = Math.max(0, attackCount);
        this.lastAttackAt = lastAttackAt;
        this.lastAttackerName = lastAttackerName == null ? "" : lastAttackerName;
    }

    public boolean isUnderSiege(long windowMillis) {
        return lastAttackAt > 0 && System.currentTimeMillis() - lastAttackAt <= windowMillis;
    }

    public long getVersion() { return version.get(); }

    public void touch() { version.incrementAndGet(); }

    @Override
    public boolean equals(Object o) {
        return o instanceof Region other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Region[" + getShortId() + ", type=" + typeId + ", owner=" + getOwnerName()
                + ", " + world + " " + coreX + "/" + coreY + "/" + coreZ + "]";
    }
}
