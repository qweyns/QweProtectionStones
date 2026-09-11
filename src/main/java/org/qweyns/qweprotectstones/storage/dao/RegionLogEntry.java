package org.qweyns.qweprotectstones.storage.dao;

import java.util.UUID;

public record RegionLogEntry(UUID regionId, long at, String playerName, String action, String detail) {

    public static RegionLogEntry of(UUID regionId, String playerName, String action, String detail) {
        return new RegionLogEntry(regionId, System.currentTimeMillis(), playerName, action, truncate(detail));
    }

    private static String truncate(String detail) {
        if (detail == null) return "";
        return detail.length() <= 128 ? detail : detail.substring(0, 128);
    }
}
