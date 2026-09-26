package org.qweyns.qweprotectstones.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.qweyns.qweprotectstones.utils.ColorUtil;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void hoverAndClickSurviveColorUtil() {
        String line = "<click:run_command:'/ps home a1b2'>"
                + "<hover:show_text:'<#C4B5FD>Нажмите — телепорт к привату'><#86EFAC>▪ строка списка</hover></click>";
        Component component = ColorUtil.formatComponent(line);
        assertTrue(hasEvent(component, true), "click-событие потерялось при разборе MiniMessage");
        assertTrue(hasEvent(component, false), "hover-событие потерялось при разборе MiniMessage");
    }

    @Test
    void hoverTooltipsAreNotEmptyInLangFiles() throws Exception {
        for (String file : new String[]{"ru_RU", "en_US", "es_ES", "zh_CN"}) {
            try (InputStream in = MiniMessageSmokeTest.class.getResourceAsStream("/lang/" + file + ".yml")) {
                if (in == null) continue;
                Map<String, Object> root = new Yaml()
                        .load(new InputStreamReader(in, StandardCharsets.UTF_8));
                List<String> strings = new ArrayList<>();
                collect(root, strings);
                for (String line : strings) {
                    if (!line.contains("<hover:")) continue;

                    Component hovered = findHovered(ColorUtil.formatComponent(line));
                    assertTrue(hovered != null, file + ": hover-событие потерялось: " + line);

                    StringBuilder tooltip = new StringBuilder();
                    collectText((Component) hovered.hoverEvent().value(), tooltip);
                    assertFalse(tooltip.toString().isBlank(),
                            file + ": hover-подсказка пустая: " + line);
                }
            }
        }
    }

    private static boolean hasEvent(Component component, boolean click) {
        if (click ? component.clickEvent() != null : component.hoverEvent() != null) return true;
        for (Component child : component.children()) {
            if (hasEvent(child, click)) return true;
        }
        return false;
    }

    private static Component findHovered(Component component) {
        if (component.hoverEvent() != null) return component;
        for (Component child : component.children()) {
            Component found = findHovered(child);
            if (found != null) return found;
        }
        return null;
    }

    private static void collectText(Component component, StringBuilder out) {
        if (component instanceof net.kyori.adventure.text.TextComponent text) out.append(text.content());
        for (Component child : component.children()) collectText(child, out);
    }
}
