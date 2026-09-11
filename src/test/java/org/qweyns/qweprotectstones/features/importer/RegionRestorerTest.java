package org.qweyns.qweprotectstones.features.importer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RegionRestorerTest {

    @TempDir
    Path tempDir;

    private File file(String content) throws IOException {
        Path file = tempDir.resolve("regions_test.json");
        Files.writeString(file, content);
        return file.toFile();
    }

    @Test
    void emptyExportRestoresNothing() throws IOException {

        RegionRestorer restorer = new RegionRestorer(null);
        RegionRestorer.Result result = restorer.restore(file(
                "{\n  \"exported_at\": \"2026-01-01_00-00-00\",\n  \"regions\": []\n}\n"));

        assertEquals(0, result.restored());
        assertEquals(0, result.skipped());
        assertEquals(0, result.errors());
    }

    @Test
    void garbageFileIsRejectedWithIOException() throws IOException {
        RegionRestorer restorer = new RegionRestorer(null);

        assertThrows(IOException.class, () -> restorer.restore(file("совсем не json: [[[ ===")));
        assertThrows(IOException.class, () -> restorer.restore(file("{\"no_regions_here\": true}")));
    }

    @Test
    void exportJsonIsParseableAsYaml() throws IOException {
        // Страж формата: реальная выгрузка должна оставаться YAML-совместимой.
        String export = "{\n  \"exported_at\": \"2026-01-01\",\n"
                + "  \"plugin_version\": \"2.0.0\",\n  \"regions\": [\n"
                + "    {\n      \"id\": \"01234567-89ab-cdef-0123-456789abcdef\",\n"
                + "      \"type\": \"small\",\n      \"world\": \"world\",\n"
                + "      \"core\": [0, 32, 0],\n"
                + "      \"bounds\": {\"min\": [-10, 0, -10], \"max\": [10, 60, 10]},\n"
                + "      \"durability\": 50, \"max_durability\": 100,\n"
                + "      \"owner\": {\"uuid\": \"ffffffff-89ab-cdef-0123-456789abcdef\", \"name\": \"Steve\"}\n"
                + "    }\n  ]\n}\n";

        Object parsed = new org.yaml.snakeyaml.Yaml().load(export);
        assertInstanceOf(java.util.Map.class, parsed);
        java.util.Map<?, ?> root = (java.util.Map<?, ?>) parsed;
        assertTrue(root.get("regions") instanceof java.util.List<?> regions && regions.size() == 1);
    }
}
