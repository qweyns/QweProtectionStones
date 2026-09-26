[← QweProtectStones](../README.md) / [Документация](README.md)

![DEVELOPERS](https://img.shields.io/badge/QPS-DEVELOPERS-a78bfa?style=flat-square&labelColor=181825)

# 💥 Взрывы и аддоны

> [!WARNING]
> Для урона из другого региона используйте damageRegionAsync. Не блокируйте серверный тик через join() или get().

<details>
<summary><strong>На этой странице</strong></summary>

[Классификация](#классификация) · [Прямой урон](#прямой-урон)

</details>

---

Эта страница описывает существующий API **QPS**. План аддона QweTnts (динамиты, конфиги, типы приватов под HolyWorld) — в [DYNAMITE_ADDON_API.md](DYNAMITE_ADDON_API.md).
Аддон — отдельный плагин и отдельный репозиторий. Его предметы, рецепты, PDC,
визуальные эффекты и команды QPS не реализует.

## Классификация

`org.qweyns.qweprotectstones.regions.event.RegionExplosionTypeEvent` вызывается
при включённой осаде для принятого Bukkit-взрыва, до поиска соседних регионов.
Событие не отменяемое; класс содержит `getEntity()`, `getBlock()`,
`getExplosionType()`, `setExplosionType(String)`, `getDamageRadiusMultiplier()`,
`setDamageRadiusMultiplier(double)`.

Обработчик читает **свои** метки взрывчатки и меняет тип/радиус:

```java
@EventHandler
public void classify(RegionExplosionTypeEvent event) {
    // Здесь сначала проверьте, принадлежит ли источник вашему аддону.
    if (!isOurExplosive(event.getEntity())) return;
    event.setExplosionType("DYNAMITE_A");
    event.setDamageRadiusMultiplier(3.0);
}
```

`isOurExplosive` — функция аддона, не метод QPS. Классификация возможна и вне
границ привата: расширенный радиус не зависит от vanilla `blockList()`.
Множитель должен быть конечным, положительным; он ограничен 100.
Итоговый радиус ограничен 256 блоками. Это защитные пределы, не игровые настройки.

Разрешение конкретного типа задаётся в `regions.yml`:

```yaml
region_types:
  DIAMOND_BLOCK:
    explosions:
      DYNAMITE_A: true
      TNT: false
```

Имена типов взрывов регистронезависимы. `raid_immune`, выключенная осада,
запрет типа, cooldown и отменяемые события продолжают действовать.
Ванильный урон за принятый взрыв задаёт `siege.damage_per_explosion`.

## Прямой урон

```java
QpsApi api = QpsApi.get();
if (api == null) return;
// Region получен заранее; имя игрока захвачено в его потоке.
api.damageRegionAsync(region, 2, "DYNAMITE_A", attackerName)
   .thenAccept(damaged -> {
       // Только потокобезопасная работа; для Bukkit нужна отдельная задача.
   });
```

`damageRegion` допустим на Paper в основном потоке, на Folia — **в потоке ядра**.
Нельзя считать поток взрыва автоматически потоком удалённого ядра.
Из другого потока используйте `damageRegionAsync`; никогда не блокируйте тик через `join()`/`get()`.

`RegionDamageEvent` позволяет отменить/изменить урон. При отмене смертельного
`RegionDeleteEvent` HP, статистика, штраф и cooldown не фиксируются, результат — `false`.
Штраф ремонта применяется только к принятому урону; при выключении QPS ожидающие
async-вызовы завершаются `false`.

Не вызывайте прямой урон дополнительно к обычному взрыву, если не хотите две
попытки урона. Не изменяйте мир и `Region` напрямую в обход защиты и сервиса.
При выгрузке проверяйте доступность API; поддержка других плагинов событий
предполагает соблюдение Bukkit-контракта MONITOR.

---

[← API для разработчиков](api.md) · [Все разделы](README.md) · [К проекту →](../README.md)
