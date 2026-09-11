package org.qweyns.qweprotectstones.config;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MiniMessageSmokeTest {

    private static final String[] FILES = {
            "lang/ru_RU.yml", "lang/en_US.yml", "lang/es_ES.yml", "lang/zh_CN.yml",
            "menus/main.yml", "menus/upgrade.yml", "menus/effects.yml",
            "regions.yml", "visuals.yml", "config.yml", "protection.yml",
            "siege.yml", "effects.yml", "features.yml"
    };

    public static List<String> strings() throws Exception {
        List<String> all = new ArrayList<>();
        Yaml yaml = new Yaml();
        for (String name : FILES) {
            try (InputStream in = MiniMessageSmokeTest.class.getResourceAsStream("/" + name)) {
                if (in == null) continue;
                Map<String, Object> root = yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                collect(root, all);
            }
        }
        return all;
    }

    @SuppressWarnings("unchecked")
    private static void collect(Object node, List<String> out) {
        if (node instanceof Map<?, ?> map) {
            for (Object value : map.values()) collect(value, out);
        } else if (node instanceof List<?> list) {
            for (Object value : list) collect(value, out);
        } else if (node instanceof String s) {
            out.add(s);
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("strings")
    void miniMessageParses(String line) {
        assertDoesNotThrow(() -> MiniMessage.miniMessage().deserialize(line),
                "MiniMessage не смог разобрать строку: " + line);
    }
}
