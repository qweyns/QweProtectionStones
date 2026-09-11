package org.qweyns.qweprotectstones.holograms;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.data.property.Visibility;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.joml.Vector3f;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.config.ConfigManager;
import org.qweyns.qweprotectstones.utils.ColorUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Голограммы через FancyHolograms.
 *
 * <p>Провайдер отдаёт всё, что умеет TextDisplay: подложку, выравнивание,
 * прозрачность для блоков, тень, яркость, смещение и интерполяцию. Раньше из
 * этого настраивались только тень, масштаб и billboard.</p>
 */
public class FHProvider implements IHologramProvider {

    private final QweProtectStones plugin;
    private final Set<String> activeHolograms = new LinkedHashSet<>();

    public FHProvider(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    /** FancyHolograms может быть выключен раньше нас при остановке сервера. */
    private Optional<de.oliver.fancyholograms.api.HologramManager> manager() {
        if (!FancyHologramsPlugin.isEnabled()) return Optional.empty();

        FancyHologramsPlugin api = FancyHologramsPlugin.get();
        return api == null ? Optional.empty() : Optional.ofNullable(api.getHologramManager());
    }

    @Override
    public void createOrUpdate(Region region, Location coreLocation) {
        Optional<de.oliver.fancyholograms.api.HologramManager> managerOpt = manager();
        if (managerOpt.isEmpty()) return;

        de.oliver.fancyholograms.api.HologramManager fhManager = managerOpt.get();
        ConfigManager config = plugin.getConfigManager();

        String name = hologramName(region.getId());
        String typeId = region.getTypeId();

        Location hologramLoc = coreLocation.clone().add(0.5, config.getHologramOffset(typeId), 0.5);

        Hologram holo = fhManager.getHologram(name).orElse(null);
        if (holo == null) {
            holo = fhManager.create(new TextHologramData(name, hologramLoc));
            fhManager.addHologram(holo);
        }

        if (!(holo.getData() instanceof TextHologramData textData)) return;

        textData.setText(buildLines(region, typeId, config));
        textData.setLocation(hologramLoc);

        // Без этого FancyHolograms сохранит наши голограммы в свой holograms.yml,
        // и после рестарта они останутся висеть дубликатами.
        textData.setPersistent(false);

        applyTextSettings(textData, typeId, config);
        applyDisplaySettings(textData, typeId, config);

        holo.forceUpdate();
        holo.refreshForViewersInWorld();

        activeHolograms.add(name);
    }

    private List<String> buildLines(Region region, String typeId, ConfigManager config) {
        // Голограмма целиком состоит из hologram_lines (или _under_attack):
        // display_name не подставляется — заголовок, если нужен, настраивается
        // в конфиге вручную. Плейсхолдеры работают в каждой строке.
        List<String> lines = new ArrayList<>();
        for (String line : config.getHologramLines(typeId, plugin.isUnderSiege(region))) {
            lines.add(HologramText.apply(plugin, line, region));
        }
        return lines;
    }

    private void applyTextSettings(TextHologramData textData, String typeId, ConfigManager config) {
        textData.setTextShadow(config.hasShadow(typeId));
        textData.setSeeThrough(config.isHologramSeeThrough(typeId));
        textData.setTextUpdateInterval(config.getHologramUpdateInterval(typeId));
        textData.setTextAlignment(parseAlignment(config.getHologramAlignment(typeId)));

        // Пустая строка означает «без подложки» — FancyHolograms принимает null.
        String background = config.getHologramBackground(typeId);
        textData.setBackground(background.isBlank() ? null : ColorUtil.parseParticleColor(background));
    }

    private void applyDisplaySettings(TextHologramData textData, String typeId, ConfigManager config) {
        textData.setVisibilityDistance(config.getHologramRange(typeId));
        textData.setVisibility(parseVisibility(config.getHologramVisibility(typeId)));
        textData.setBillboard(parseBillboard(config.getBillboard(typeId)));

        float scale = config.getScale(typeId);
        textData.setScale(new Vector3f(scale, scale, scale));
        textData.setTranslation(new Vector3f(
                config.getHologramTranslationX(typeId),
                config.getHologramTranslationY(typeId),
                config.getHologramTranslationZ(typeId)));

        textData.setShadowRadius(config.getHologramShadowRadius(typeId));
        textData.setShadowStrength(config.getHologramShadowStrength(typeId));
        textData.setInterpolationDuration(config.getHologramInterpolation(typeId));

        applyBrightness(textData, typeId, config);
    }

    /** Отрицательные значения означают «брать освещение сцены», как в ваниле. */
    private void applyBrightness(TextHologramData textData, String typeId, ConfigManager config) {
        int block = config.getHologramBlockLight(typeId);
        int sky = config.getHologramSkyLight(typeId);

        if (block < 0 && sky < 0) {
            textData.setBrightness(null);
            return;
        }
        textData.setBrightness(new Display.Brightness(clampLight(block), clampLight(sky)));
    }

    private static int clampLight(int value) {
        return Math.max(0, Math.min(15, value));
    }

    private TextDisplay.TextAlignment parseAlignment(String raw) {
        try {
            return TextDisplay.TextAlignment.valueOf(raw);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Неизвестное выравнивание голограммы: " + raw + " — использую CENTER.");
            return TextDisplay.TextAlignment.CENTER;
        }
    }

    private Visibility parseVisibility(String raw) {
        try {
            return Visibility.valueOf(raw);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Неизвестный режим видимости голограммы: " + raw + " — использую ALL.");
            return Visibility.ALL;
        }
    }

    private Display.Billboard parseBillboard(String raw) {
        try {
            return Display.Billboard.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return Display.Billboard.CENTER;
        }
    }

    @Override
    public void remove(UUID regionId) {
        String name = hologramName(regionId);
        manager().ifPresent(fhManager -> fhManager.getHologram(name).ifPresent(fhManager::removeHologram));
        activeHolograms.remove(name);
    }

    @Override
    public void deleteAll() {
        manager().ifPresent(fhManager -> {
            for (String name : activeHolograms) {
                fhManager.getHologram(name).ifPresent(fhManager::removeHologram);
            }
        });
        activeHolograms.clear();
    }

    private static String hologramName(UUID regionId) {
        return "region_" + regionId;
    }
}
