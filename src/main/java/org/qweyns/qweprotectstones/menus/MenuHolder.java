package org.qweyns.qweprotectstones.menus;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.scheduler.Schedulers;

public class MenuHolder implements InventoryHolder {

    final String menuName;
    final Region region;

    Inventory inventory;
    Schedulers.Task updateTask;
    Schedulers.Task animatorTask;
    ItemStack[] baseLayer;
    MenuAnimator animator;

    long renderedVersion = -1;

    MenuHolder(String menuName, Region region) {
        this.menuName = menuName;
        this.region = region;
    }

    public String getMenuName() {
        return menuName;
    }

    public Region getRegion() {
        return region;
    }

    void cancelTasks() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        if (animatorTask != null) {
            animatorTask.cancel();
            animatorTask = null;
        }
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
