package org.qweyns.qweprotectstones.features.effect;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.qweyns.qweprotectstones.QweProtectStones;

public class EffectPurchaseManager {

    public enum ChargeResult {

        SUCCESS,

        FREE,

        NO_ECONOMY,

        NOT_ENOUGH
    }

    private final QweProtectStones plugin;

    public EffectPurchaseManager(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public double price(String effectName, int amplifier) {
        double base = plugin.getConfigManager().getConfig()
                .getDouble("effects.purchase.per-effect." + effectName.toUpperCase(), -1.0);
        if (base < 0) {
            base = plugin.getConfigManager().getConfig().getDouble("effects.purchase.cost-amount", 0.0);
        }
        if (base <= 0) return 0;

        if (plugin.getConfigManager().getConfig().getBoolean("effects.purchase.scale-with-amplifier", false)
                && amplifier > 0) {
            base = base * (amplifier + 1);
        }
        return base;
    }

    public static String format(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) return String.valueOf((long) value);
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    public ChargeResult charge(Player player, String effectName, int amplifier) {
        double price = price(effectName, amplifier);
        if (price <= 0) return ChargeResult.FREE;

        switch (costType()) {
            case "money" -> {
                if (!plugin.getVaultHook().isEnabled()) return ChargeResult.NO_ECONOMY;
                if (!plugin.getVaultHook().hasMoney(player, price)) return ChargeResult.NOT_ENOUGH;
                return plugin.getVaultHook().takeMoney(player, price)
                        ? ChargeResult.SUCCESS : ChargeResult.NOT_ENOUGH;
            }
            case "points" -> {
                int points = (int) Math.ceil(price);
                if (!plugin.getPlayerPointsHook().isEnabled()) return ChargeResult.NO_ECONOMY;
                if (!plugin.getPlayerPointsHook().hasPoints(player, points)) return ChargeResult.NOT_ENOUGH;
                return plugin.getPlayerPointsHook().takePoints(player, points)
                        ? ChargeResult.SUCCESS : ChargeResult.NOT_ENOUGH;
            }
            case "item" -> {
                Material material = paymentItem();
                int amount = (int) Math.ceil(price);
                if (material == null || material.isAir()) return ChargeResult.NO_ECONOMY;
                if (countItems(player, material) < amount) return ChargeResult.NOT_ENOUGH;
                removeItems(player, material, amount);
                return ChargeResult.SUCCESS;
            }
            default -> {
                return ChargeResult.FREE;
            }
        }
    }

    public String costTypeName() {
        return switch (costType()) {
            case "money" -> plugin.getLanguageManager().rawTemplate("effect_cost_money");
            case "points" -> plugin.getLanguageManager().rawTemplate("effect_cost_points");
            case "item" -> "<translate:" + paymentItem().translationKey() + ">";
            default -> "";
        };
    }

    private String costType() {
        String raw = plugin.getConfigManager().getConfig().getString("effects.purchase.cost-type", "none");
        if (raw == null) return "none";
        return raw.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private Material paymentItem() {
        String raw = plugin.getConfigManager().getConfig().getString("effects.purchase.item", "DIAMOND");
        Material material = raw == null ? null : Material.matchMaterial(raw);
        return material != null && material.isItem() ? material : Material.DIAMOND;
    }

    private int countItems(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) total += item.getAmount();
        }
        return total;
    }

    private void removeItems(Player player, Material material, int amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material) continue;

            int take = Math.min(amount, item.getAmount());
            if (take >= item.getAmount()) {
                player.getInventory().setItem(i, null);
            } else {
                // getStorageContents() может вернуть копию, пишем через setItem

                ItemStack reduced = item.clone();
                reduced.setAmount(item.getAmount() - take);
                player.getInventory().setItem(i, reduced);
            }
            amount -= take;
        }
    }
}
