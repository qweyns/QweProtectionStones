package org.qweyns.qweprotectstones.diagnostics;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigValidatorTest {

    @Test
    void reportsBadValues() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("siege.explosion_types.entities.CREEPER", "CREEPER");
        cfg.set("siege.explosion_types.entities.NOT_A_MOB", "TNT");
        cfg.set("help.no-args", "gui");
        cfg.set("creation.near-siege-radius", -5);
        cfg.set("transfer.previous-owner-role", "no_such_role");

        List<String> warnings = new ArrayList<>();
        ConfigValidator.validate(cfg, warnings::add);

        assertEquals(4, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("NOT_A_MOB")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("help.no-args")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("near-siege-radius")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("no_such_role")));
    }

    @Test
    void defaultsAreClean() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("transfer.previous-owner-role", "manager");
        cfg.set("market.sell.seller-role", "");
        List<String> warnings = new ArrayList<>();
        ConfigValidator.validate(cfg, warnings::add);
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void reportCapturesLoggerWarnings() {
        var logger = java.util.logging.Logger.getLogger("qps-config-report-test");
        logger.setUseParentHandlers(false);
        try (ConfigReport report = ConfigReport.capture(logger)) {
            logger.info("fine");
            logger.warning("broken value");
            report.warn("manual");
            assertEquals(List.of("broken value", "manual"), report.issues());
        }
        logger.warning("after close");
    }
}
