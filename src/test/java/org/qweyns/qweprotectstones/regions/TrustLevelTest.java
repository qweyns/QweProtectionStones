package org.qweyns.qweprotectstones.regions;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.config.RoleLoader;
import org.qweyns.qweprotectstones.config.Tunables.TrustAction;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class TrustLevelTest {

    @AfterEach
    void reset() { TrustLevel.install(null); }

    private static TrustLevel role(String id) { return TrustLevel.parse(id).orElseThrow(); }

    @Test
    void builtinRolesKeepLegacyBehaviour() {
        assertTrue(role("access").allows(TrustAction.INTERACT));
        assertFalse(role("access").allows(TrustAction.CONTAINER));
        assertTrue(role("container").allows(TrustAction.CONTAINER));
        assertFalse(role("container").allows(TrustAction.BUILD));
        assertTrue(role("build").allows(TrustAction.BUILD));
        assertFalse(role("build").allows(TrustAction.FLAGS));
        assertTrue(role("manager").allows(TrustAction.MEMBERS));
        assertTrue(role("manager").atLeast(role("build")));
        assertEquals("build", TrustLevel.defaultRole().id());
        assertEquals("container", TrustLevel.publicRole().id());
    }

    @Test
    void parseIsCaseAndSpaceInsensitive() {
        assertEquals("build", role(" BUILD ").id());
        assertTrue(role("owner").isOwner());
        assertTrue(TrustLevel.parse("vip").isEmpty());
        assertTrue(TrustLevel.parse("").isEmpty());
        assertTrue(TrustLevel.parse(null).isEmpty());
    }

    @Test
    void grantableExcludesOwner() {
        for (TrustLevel level : TrustLevel.grantable()) assertFalse(level.isOwner());
        assertEquals(4, TrustLevel.grantable().length);
    }

    @Test
    void customRolesFromConfig() throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString("""
                roles:
                  owner-display: "Хозяин"
                  default-role: helper
                  public-access-role: ""
                  aliases:
                    old: guest
                  list:
                    guest:
                      display: "Гость"
                      weight: 5
                      actions: [interact, entry]
                    helper:
                      display: "Помощник"
                      weight: 15
                      inherit: guest
                      actions: [build, -entry]
                    service:
                      weight: 1
                      grantable: false
                      actions: ["*", "-build"]
                """);
        TrustLevel.install(RoleLoader.load(cfg, Logger.getAnonymousLogger()));

        assertEquals("helper", TrustLevel.defaultRole().id());
        assertNull(TrustLevel.publicRole());
        assertEquals("Хозяин", TrustLevel.owner().displayName());
        assertTrue(role("helper").allows(TrustAction.INTERACT));
        assertTrue(role("helper").allows(TrustAction.BUILD));
        assertFalse(role("helper").allows(TrustAction.ENTRY));
        assertEquals("guest", role("old").id());
        assertTrue(role("service").allows(TrustAction.FLAGS));
        assertFalse(role("service").allows(TrustAction.BUILD));
        assertEquals(2, TrustLevel.grantable().length);
        assertEquals("guest", TrustLevel.lowestWith(TrustAction.INTERACT).id());
        assertTrue(TrustLevel.parse("build").isEmpty());
    }

    @Test
    void removedRoleIsKeptAsOrphanWithoutRights() {
        RegionMember member = new RegionMember(UUID.randomUUID(), "x", "vip", 1);
        assertEquals("vip", member.role());
        assertFalse(member.trust().known());
        assertTrue(member.trust().actions().isEmpty());
    }
}
