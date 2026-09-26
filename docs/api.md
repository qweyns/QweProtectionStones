[← QweProtectStones](../README.md) / [Документация](README.md)

![DEVELOPERS](https://img.shields.io/badge/QPS-DEVELOPERS-a78bfa?style=flat-square&labelColor=181825)

# 🧑‍💻 API для разработчиков

> [!WARNING]
> На Folia основной поток — не универсальный контекст. Bukkit-операции выполняются в потоке владельца объекта.

<details>
<summary><strong>На этой странице</strong></summary>

[События](#события) · [QpsApi](#qpsapi) · [Осада и сторонние взрывчатки](#осада-и-сторонние-взрывчатки)

</details>

---

Пакет `org.qweyns.qweprotectstones`. Плагин объявляет `api-version: 1.21`.

## События

События находятся в пакете `org.qweyns.qweprotectstones.regions.event`:

| Событие | Когда | Что можно |
| --- | --- | --- |
| `RegionCreateEvent` | Перед созданием привата | Отменить создание |
| `RegionDeleteEvent` | Перед удалением | Отменить удаление; причина `Reason`: `BROKEN`, `DESTROYED_BY_RAID`, `COMMAND`, `ADMIN`, `EXPIRED` |
| `RegionExplosionTypeEvent` | До поиска регионов взрыва | Изменить тип/множитель радиуса; не отменяемое |
| `RegionDamageEvent` | Перед снятием прочности | Отменить или изменить урон |
| `RegionFlagChangeEvent` | Перед сменой флага | Отменить смену |
| `RegionMemberChangeEvent` | Перед выдачей/отзывом доступа | Отменить |
| `RegionTransferEvent` | Перед передачей привата | Отменить |
| `EffectPurchaseEvent` | Перед покупкой эффекта | Отменить покупку |

```java
@EventHandler
public void onRegionCreate(RegionCreateEvent event) {
    Region region = event.getRegion();
    if (region.getBounds().area() > 10_000) {
        event.setCancelled(true);
    }
}

@EventHandler
public void onRegionDamage(RegionDamageEvent event) {
    event.setDamage(event.getDamage() * 2); // двойной урон осад
}
```

## QpsApi

Готовый фасад — статический `QpsApi.get()` (инициализируется при включении
плагина; в коде, который выполняется при выключении сервера, проверяйте
`QpsApi.isAvailable()`).

### Чтение

| Метод | Описание |
| --- | --- |
| `getRegionAt(Location)` | Приват в точке или `null` |
| `getRegion(UUID)` / `getRegionByShortId(String)` | По полному или короткому id |
| `getRegionsOf(UUID owner)` | Приваты владельца |
| `getAccessibleRegions(UUID player)` | Приваты игрока (свои + вписанные) |
| `getRegionCount()` / `getAllRegions()` | Счётчик и все приваты |
| `trustOf(Region, Player)` | Уровень доверия игрока |
| `isTrusted(Player, Location, TrustAction)` / `(..., TrustLevel)` | Проверка доступа |
| `flagAt(Location, RegionFlag)` | Значение флага в точке (вне приватов — разрешающее) |
| `isUnderSiege(Region)` | Идёт ли осада |

### Управление (поток-владелец Bukkit-объекта)

| Метод | Описание |
| --- | --- |
| `createRegion(Player, RegionType, Location)` | Создать приват со всеми проверками; результат `CreateResult` |
| `deleteRegion(Region, Reason, Player)` | Удалить приват |
| `transferRegion(Region, UUID, String)` | Передать приват |
| `getProtectionService()` | Низкоуровневый доступ к движку защиты |

```java
QpsApi api = QpsApi.get();
if (api == null) return;

Region region = api.getRegionAt(player.getLocation());
if (region != null && api.isUnderSiege(region)) {
    player.sendMessage("Этот приват под осадой!");
}
```

### Прямой доступ к плагину

```java
QweProtectStones plugin = (QweProtectStones)
        Bukkit.getPluginManager().getPlugin("QweProtectStones");
Region region = plugin.getRegionManager().getRegionAt(location);
```

Потокобезопасность: геометрические запросы (`getRegionAt`, границы, флаги)
можно звать из любого потока; создание и удаление приватов — только из
основного потока Paper либо соответствующего регионального/entity-потока Folia.
Чтение Bukkit-сущностей не становится async-безопасным из-за использования API.
Не меняйте живой Region в обход сервисов: прямые мутации не составляют транзакцию.

## Осада и сторонние взрывчатки

`damageRegion` требует поток ядра; `damageRegionAsync` выполняет переход к ядру.
Есть `isExplosionDamaging`, `isSiegeEnabled`, `getSiegeService`, `getRegionType`,
`regionTypeOf` и `getRegionTypes`. Детали, примеры и защитные пределы —
[API взрывов](explosions-api.md).

Отменяемые события меняются до MONITOR. MONITOR предназначен только для наблюдения:
плагин, отменяющий событие на этом приоритете, нарушает Bukkit-контракт.

---

[← Интеграции](integrations.md) · [Все разделы](README.md) · [Взрывы и аддоны →](explosions-api.md)
