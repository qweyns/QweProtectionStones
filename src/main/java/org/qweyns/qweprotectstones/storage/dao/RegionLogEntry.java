package org.qweyns.qweprotectstones.storage.dao;

import java.util.UUID;

/**
 * Запись журнала действий в привате: кто, когда и что сделал.
 *
 * <p>Журнал ведётся только для доверенных игроков и только по действиям,
 * которые меняют мир, — записывать каждое движение бессмысленно и дорого.</p>
 */
public record RegionLogEntry(UUID regionId, long at, String playerName, String action, String detail) {

    public static RegionLogEntry of(UUID regionId, String playerName, String action, String detail) {
        return new RegionLogEntry(regionId, System.currentTimeMillis(), playerName, action, truncate(detail));
    }

    /** Колонка detail рассчитана на 128 символов — режем длинные названия заранее. */
    private static String truncate(String detail) {
        if (detail == null) return "";
        return detail.length() <= 128 ? detail : detail.substring(0, 128);
    }
}
