package org.qweyns.qweprotectstones.features.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Тесты экранирования HTML для подписей веб-карт: Dynmap и BlueMap рендерят
 * всплывающие подсказки маркеров в браузере, поэтому значение из игры не
 * должно открывать разметку на странице карты.
 */
class MapTextTest {

    @Test
    void escapeHtmlНейтрализуетТеги() {
        assertEquals("&lt;b&gt;жми&lt;/b&gt;", MapText.escapeHtml("<b>жми</b>"));
        assertEquals("&lt;img src=x onerror=alert(1)&gt;", MapText.escapeHtml("<img src=x onerror=alert(1)>"));
    }

    @Test
    void escapeHtmlНейтрализуетАмперсандыИКавычки() {
        assertEquals("R&amp;D", MapText.escapeHtml("R&D"));
        assertEquals("&quot;строка&quot;", MapText.escapeHtml("\"строка\""));
        assertEquals("&#39;строка&#39;", MapText.escapeHtml("'строка'"));
    }

    @Test
    void escapeHtmlПропускаетОбычныйТекст() {
        assertEquals("База Стива [a1b2]", MapText.escapeHtml("База Стива [a1b2]"));
        assertEquals("", MapText.escapeHtml(""));
        assertEquals("", MapText.escapeHtml(null));
    }
}
