# API для разработчиков

Пакет `org.qweyns.qweprotectstones`. Плагин объявляет `api-version: 1.21`.

## События

Все события отменяемые, в пакете `org.qweyns.qweprotectstones.regions.event`:

| Событие | Когда | Что можно |
| --- | --- | --- |
| `RegionCreateEvent` | Перед созданием привата | Отменить создание |
| `RegionDeleteEvent` | Перед удалением | Отменить удаление; причина `Reason`: `BROKEN`, `DESTROYED_BY_RAID`, `COMMAND`, `ADMIN`, `EXPIRED` |
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

### Управление (только основной поток сервера)

| Метод | Описание |
| --- | --- |
| `createRegion(Player, RegionType, Location)` | Создать приват со всеми проверками; результат `CreateResult` |
| `deleteRegion(Region, Reason, Player)` | Удалить приват |
| `transferRegion(Region, UUID, String)` | Передать приват |
| `getProtectionService()` | Низкоуровневый доступ к движку защиты |

```java
QpsApi api = QpsApi.get();
if (!api.isAvailable()) return;

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
основного потока сервера (или через планировщик вашего плагина).
