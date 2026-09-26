package org.qweyns.qweprotectstones.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationContractTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    @Test void allYamlResourcesAreMappingsWithoutDuplicateKeys() throws Exception {
        var options = new LoaderOptions(); options.setAllowDuplicateKeys(false);
        try (var files = Files.walk(RESOURCES)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".yml")).toList()) {
                Object root = new Yaml(new SafeConstructor(options)).load(Files.readString(path));
                assertInstanceOf(Map.class, root, path.toString());
            }
        }
    }
    @Test void translationsHaveMatchingKeysAndPlaceholders() throws Exception {
        Map<?,?> reference = new Yaml().load(Files.readString(RESOURCES.resolve("lang/en_US.yml")));
        for (String language : List.of("ru_RU", "es_ES", "zh_CN")) {
            Map<?,?> translated = new Yaml().load(Files.readString(RESOURCES.resolve("lang/" + language + ".yml")));
            assertEquals(reference.keySet(), translated.keySet(),language);
            for (Object key : reference.keySet()) assertEquals(tokens(reference.get(key)),tokens(translated.get(key)),language + ":" + key);
        }
    }
    private Set<String> tokens(Object value) {
        var matcher = java.util.regex.Pattern.compile("%[a-zA-Z_]+%").matcher(String.valueOf(value));
        Set<String> found = new HashSet<>(); while (matcher.find()) found.add(matcher.group()); return found;
    }
    @Test void importDefaultIsARealBundledRegionType() throws Exception {
        var regions = YamlConfiguration.loadConfiguration(RESOURCES.resolve("regions.yml").toFile());
        var features = YamlConfiguration.loadConfiguration(RESOURCES.resolve("features.yml").toFile());
        assertTrue(regions.isConfigurationSection("region_types." + features.getString("import.type-id")));
    }
    @Test void numericSettingsRejectNonFiniteValuesAndClampUnsafeBounds() {
        var config = new YamlConfiguration();
        assertEquals(5,ConfigValues.boundedLong(config,"delay",5,1,120));
        config.set("delay",Long.MAX_VALUE); assertEquals(120,ConfigValues.boundedLong(config,"delay",5,1,120));
        config.set("delay",-10); assertEquals(1,ConfigValues.boundedLong(config,"delay",5,1,120));
        config.set("offset",Double.NaN); assertEquals(1.2,ConfigValues.boundedDouble(config,"offset",1.2,0,16));
        config.set("offset",Double.POSITIVE_INFINITY); assertEquals(1.2,ConfigValues.boundedDouble(config,"offset",1.2,0,16));
        config.set("offset",0); assertEquals(0,ConfigValues.boundedDouble(config,"offset",1.2,0,16));
    }
    @Test void legacyTypeHologramOverrideBeatsModernGlobalDefault() throws Exception {
        QweProtectStones plugin = mock(QweProtectStones.class, RETURNS_DEEP_STUBS);
        ConfigManager manager = mock(ConfigManager.class, CALLS_REAL_METHODS);
        var field = ConfigManager.class.getDeclaredField("plugin"); field.setAccessible(true); field.set(manager,plugin);
        var config = new YamlConfiguration();
        config.set("default_region.hologram_settings.scale",1.0);
        config.set("region_types.DIAMOND_BLOCK.fancyholograms_settings.scale",2.0);
        when(plugin.getRegionConfig().raw()).thenReturn(config);
        when(plugin.getRegionConfig().getDouble("DIAMOND_BLOCK","fancyholograms_settings.scale",1.0)).thenReturn(2.0);
        assertEquals(2.0f,manager.getScale("DIAMOND_BLOCK"));
        config.set("region_types.DIAMOND_BLOCK.hologram_settings.scale",3.0);
        when(plugin.getRegionConfig().getDouble("DIAMOND_BLOCK","hologram_settings.scale",1.0)).thenReturn(3.0);
        assertEquals(3.0f,manager.getScale("DIAMOND_BLOCK"));
    }
    @Test void commandPlaceholderUsesRegisteredNameButAllowsExplicitLabel() throws Exception {
        var plugin = mock(QweProtectStones.class);
        when(plugin.getPlayerCommandName()).thenReturn("land");
        LanguageManager manager = new LanguageManager(plugin);
        var config = new YamlConfiguration(); config.set("usage","/%command% trust");
        var field = LanguageManager.class.getDeclaredField("langConfig"); field.setAccessible(true); field.set(manager,config);
        assertEquals("/land trust",manager.rawTemplate("usage"));
        assertEquals("/region trust",manager.rawTemplate("usage","%command%","region"));
    }
}
