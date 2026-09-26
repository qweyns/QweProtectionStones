package org.qweyns.qweprotectstones.menus;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionBounds;
import org.qweyns.qweprotectstones.regions.event.EffectPurchaseEvent;

import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuTransactionTest {
    private QweProtectStones plugin;
    private MenuPlaceholders placeholders;
    private MenuActions actions;
    private Region region;
    private Player player;
    @BeforeEach void setup() {
        plugin = mock(QweProtectStones.class, RETURNS_DEEP_STUBS);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        placeholders = mock(MenuPlaceholders.class);
        when(placeholders.apply(any(), anyString(), any(), isNull())).thenAnswer(call -> call.getArgument(1));
        MenuRequirements requirements = mock(MenuRequirements.class);
        when(requirements.isEffectAllowed(any(), anyString())).thenReturn(true);
        actions = new MenuActions(plugin, placeholders, requirements);
        player = mock(Player.class);
        region = new Region(UUID.randomUUID(),"world",new RegionBounds(0,0,0,2,2,2),1,1,1,
                "small",UUID.randomUUID(),"owner",5,10,1);
        when(plugin.getRegionManager().getById(region.getId())).thenReturn(region);
        when(plugin.getProtectionService().canManage(player, region)).thenReturn(true);
        when(plugin.getVaultHook().takeMoney(player,10)).thenReturn(true);
        when(plugin.getVaultHook().giveMoney(player,10)).thenReturn(true);
    }
    @Test void multiplePaidGrantsRejectedBeforeWithdrawal() {
        actions.execute(player,List.of("[takemoney] 10","[region_add_effect] ALERTS:0","[console] give player diamond"),region);
        verify(plugin.getVaultHook(),never()).takeMoney(any(),anyDouble());
        assertTrue(region.getEffects().isEmpty());
    }
    @Test void paidGrantCannotBeFollowedByMoreWithdrawals() {
        actions.execute(player,List.of("[takemoney] 10","[region_add_effect] ALERTS:0","[takeexp] 1"),region);
        verify(plugin.getVaultHook(),never()).takeMoney(any(),anyDouble());
    }
    @Test void placeholderFailureHappensBeforePayment() {
        when(placeholders.apply(eq(player),eq("bad"),eq(region),isNull())).thenThrow(new IllegalArgumentException());
        actions.execute(player,List.of("[takemoney] 10","bad"),region);
        verify(plugin.getVaultHook(),never()).takeMoney(any(),anyDouble());
    }
    @Test void vetoRefundsButFailureAfterGrantDoesNot() {
        PluginManager events = mock(PluginManager.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(events);
            doAnswer(call -> { ((EffectPurchaseEvent) call.getArgument(0)).setCancelled(true); return null; })
                    .when(events).callEvent(any(EffectPurchaseEvent.class));
            List<String> chain = List.of("[takemoney] 10","[region_add_effect] ALERTS:0");
            actions.execute(player,chain,region);
            assertFalse(region.hasEffect("ALERTS"));
            verify(plugin.getVaultHook()).giveMoney(player,10);
            reset(events);
            doThrow(new IllegalStateException("display failed")).when(plugin.getRegionStorage()).saveNow(region);
            actions.execute(player,chain,region);
            assertTrue(region.hasEffect("ALERTS"));
            verify(plugin.getVaultHook(),times(1)).giveMoney(player,10);
        }
    }
}
