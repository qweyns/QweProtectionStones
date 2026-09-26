package org.qweyns.qweprotectstones.features.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegionExporterTest {
    @TempDir Path folder;
    @Test void concurrentExportsAreUniqueCompleteAndBackupsUseSeparateDirectory() throws Exception {
        QweProtectStones plugin = mock(QweProtectStones.class, RETURNS_DEEP_STUBS);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(plugin.getPluginMeta().getVersion()).thenReturn("test");
        Region region = new Region(UUID.randomUUID(),"world",new RegionBounds(0,0,0,2,2,2),1,1,1,
                "small",UUID.randomUUID(),"owner",5,10,1);
        StringBuilder special = new StringBuilder("start");
        for (int c=0;c<32;c++) special.append((char)c);
        for (int c=127;c<160;c++) special.append((char)c);
        special.append((char)0x2028).append((char)0x2029).appendCodePoint(0x1F600).append('"').append((char)92).append("end");
        region.restoreDecoration(special.toString());
        when(plugin.getRegionManager().getAllRegions()).thenReturn(List.of(region));
        RegionExporter exporter = new RegionExporter(plugin);
        Set<Path> files = new HashSet<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            List<Future<java.io.File>> futures = new ArrayList<>();
            for (int i=0;i<8;i++) futures.add(executor.submit(exporter::export));
            for (var future : futures) {
                Path file = future.get(5,TimeUnit.SECONDS).toPath();
                assertTrue(files.add(file));
                Object parsed = new org.yaml.snakeyaml.Yaml().load(Files.readString(file));
                assertInstanceOf(Map.class,parsed);
                List<?> regions = (List<?>)((Map<?,?>)parsed).get("regions");
                assertEquals(1, regions.size());
                assertEquals(special.toString(), ((Map<?,?>)regions.getFirst()).get("name"));
            }
        }
        assertEquals(folder.resolve("backups"), exporter.backup().toPath().getParent());
        try (var listing = Files.list(folder.resolve("exports"))) { assertEquals(8,listing.count()); }
    }
}
