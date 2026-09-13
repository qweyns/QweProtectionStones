# QPS: API для аддона кастомных динамитов (механика HolyWorld)

Этот документ — готовое ТЗ для плагина-аддона «кастомные динамиты» поверх
QweProtectStones (QPS). Все описанные здесь хуки **уже реализованы в QPS** и
покрыты CI. Аддон пишется как отдельный плагин в отдельном репозитории.

---

## 1. Что копируем с HolyWorld (режим Лайт-Анархия)

### Динамиты

| Динамит | Радиус взрыва | Особенности |
|---|---|---|
| Обычный | ванильный | ванильная ТНТ |
| Динамит A | ×3 от обычного | урон осаде с ×3 дистанции; крафт: 5 пороха + 2 песка + 2 ТНТ; фитиль 5 сек |
| Динамит B | ×10 от обычного | урон осаде с ×10 дистанции; крафт: 5 пороха + 2 песка + 2 динамита A |
| С4 | как обычный | ломает обсидиан/плачущий обсидиан; вредит незерит-привату |
| Разрывная волна | как обычный | ломает обсидиан; работает в воде и лаве (уничтожая их); сносит **2** прочности привата; на сломанный обсидиан вешает рейд-блок на 5 минут |

### Типы приватов и стойкость к взрывам

| Приват | Размер | Прочность | Чем взрывается |
|---|---|---|---|
| Железный блок | 5×5 | 1 | любой динамит |
| Золотой блок | 7×7 | 1 | любой динамит |
| Алмазный блок | 11×11 | 1 | любой динамит |
| Изумрудная руда | 21×21 | 1 | любой динамит |
| Незеритовый блок | 31×31 | 1 → 2 (апгрейд незеритовыми слитками) | только С4 и Разрывная волна |
| Уникальный (Древние обломки) | 31×31 | от 4 | только С4 и Разрывная волна |

---

## 2. Архитектура аддона

- Отдельный плагин, `plugin.yml`:

```yaml
name: QweDynamites
version: 1.0.0
main: ru.example.qwedynamites.QweDynamites
api-version: '1.21'
depend: [QweProtectStones]
```

- Динамиты аддон спавнит как `TNTPrimed` с PDC-меткой типа (пример ниже).
- Вся осадная механика (прочность, кулдаун, алерты, уничтожение) уже в QPS —
  аддон только **классифицирует** свои взрывы и при необходимости **модифицирует урон**.
- QPS сам разрулит блоки: флаг привата `explosion_damage` решает, ломать ли
  блоки внутри; правила `explosions` у типа привата решают, снимать ли прочность.

---

## 3. Точки интеграции (всё уже в QPS)

### 3.1. RegionExplosionTypeEvent — классификация взрыва

`org.qweyns.qweprotectstones.regions.event.RegionExplosionTypeEvent`

Летит, когда взрыв задел чей-то приват и осада включена. Аддон подменяет тип
взрыва — эта строка сверяется с секцией `explosions` у типа привата
(регистр не важен). Заодно можно задать множитель радиуса урона ядру.

```java
public class DynamiteClassifier implements Listener {
    private final NamespacedKey kindKey; // new NamespacedKey(plugin, "dynamite-kind")

    @EventHandler
    public void onClassify(RegionExplosionTypeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) return;
        String kind = tnt.getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
        if (kind == null) return; // ванильная ТНТ — тип TNT по умолчанию

        switch (kind) {
            case "A" -> { event.setExplosionType("DYNAMITE_A"); event.setDamageRadiusMultiplier(3.0); }
            case "B" -> { event.setExplosionType("DYNAMITE_B"); event.setDamageRadiusMultiplier(10.0); }
            case "C4" -> event.setExplosionType("C4");
            case "SHOCKWAVE" -> event.setExplosionType("SHOCKWAVE");
        }
    }
}
```

- `getEntity()` / `getBlock()` — источник взрыва (один из них null).
- `getDefaultType()` — что QPS определил сам: `TNT, CREEPER, WITHER,
  ENDER_CRYSTAL, BED, WIND_CHARGE`.
- `setDamageRadiusMultiplier(double)` — во сколько раз дальше от ядра
  взрыв ещё снимает прочность (база: `siege.explosion_damage_radius` или
  `explosion_damage_radius` у типа привата). A = 3.0, B = 10.0, остальное 1.0.

### 3.2. RegionDamageEvent — изменение/отмена урона осаде

`org.qweyns.qweprotectstones.regions.event.RegionDamageEvent`

Летит при каждом снятии прочности (взрывом QPS или ручным вызовом API).

- `setDamage(int)` — «Разрывная волна сносит 2»:

```java
@EventHandler
public void onRegionDamage(RegionDamageEvent event) {
    if ("SHOCKWAVE".equals(event.getExplosionType())) {
        event.setDamage(2); // HolyWorld: волна сносит сразу 2 прочности
    }
}
```

- `getAttackerName()` — кто атакует (может быть null); `setCancelled(true)` —
  полностью защитить приват.

### 3.3. QpsApi — программное снятие прочности и справка

```java
QpsApi api = QpsApi.get();               // null, если QPS выключен
api.isAvailable();                       // проверка без get()

// Снять прочность напрямую — полный конвейер QPS (кулдаун, событие,
// алерты, индикатор, голограмма, уничтожение на нуле). Поток сервера.
api.damageRegion(region, 2, "SHOCKWAVE", attacker.getName());

// Справка: вредит ли тип взрыва этому привату (explosions + raid_immune)
api.isExplosionDamaging(region, "C4");

// Кулдаун урона привата (мс; 0 — урон пройдёт)
api.getSiegeService().cooldownRemainingMs(region);

// Включена ли осада (siege.enabled) — без неё урона осаде не будет вообще
api.isSiegeEnabled();

// Типы приватов (радиусы, прочность, правила взрывов)
api.getRegionType("netherite");
api.regionTypeOf(region);
api.getRegionTypes();

// Геометрия и доступ — было и раньше
api.getRegionAt(loc); api.getRegionsOf(uuid); api.isUnderSiege(region);
```

### 3.4. Остальные события

- `RegionDeleteEvent` (`Reason.DESTROYED_BY_RAID`) — приват взорван; аддон
  может, например, логировать или выдавать трофеи.
- `RegionCreateEvent`, `RegionFlagChangeEvent`, `RegionTransferEvent` — общий список.

---

## 4. Конфиг QPS под механику HolyWorld

### 4.1. Типы приватов (regions.yml)

Незеритовый приват — «только С4 и Разрывная волна», прочность 1, апгрейд до 2:

```yaml
netherite:
  material: NETHERITE_BLOCK
  display_name: "Незеритовый приват"
  radius_x: 15
  radius_y: 15
  radius_z: 15
  start_durability: 1
  max_durability: 2                 # апгрейд незеритовыми слитками до 2
  upgrade_item: NETHERITE_INGOT
  explosions:
    TNT: false                      # ванильная ТНТ и динамит A/B не вредят
    DYNAMITE_A: false
    DYNAMITE_B: false
    C4: true
    SHOCKWAVE: true
```

Уникальный приват — прочность от 4:

```yaml
unique:
  material: REINFORCED_DEEPSLATE   # «Древние обломки»
  radius_x: 15
  radius_y: 15
  radius_z: 15
  start_durability: 4
  explosions:
    C4: true
    SHOCKWAVE: true
```

Дешёвые приваты — «любой динамит», прочность 1:

```yaml
iron:
  material: IRON_BLOCK
  radius_x: 2   # 5×5
  start_durability: 1
  explosions:
    TNT: true
    DYNAMITE_A: true
    DYNAMITE_B: true
    C4: true
    SHOCKWAVE: true
```

Радиусы: 5×5 → `radius_x: 2`, 7×7 → 3, 11×11 → 5, 21×21 → 10, 31×31 → 15.

Ключи в `explosions` — произвольные строки (кастомные типы аддонов
загружаются из конфига без ограничений, регистр не важен).

### 4.2. Осада (config.yml / siege-настройки)

- `siege.enabled: true` — без этого урон осаде не снимается вовсе.
- `siege.damage_cooldown_ticks` — кулдаун между снятиями прочности у одного
  привата; у типа привата можно переопределить (`damage_cooldown_ticks`).
- `siege.explosion_damage_radius` — базовый «радиус до ядра», который
  умножается на `damageRadiusMultiplier` из события классификации.

---

## 5. Каркас аддона (что писать)

1. **DynamiteType** — record: id, ItemStack-предмет (имя/лore/glow), fuse-тики,
   yield (мощность взрыва блоков), тип для QPS (`DYNAMITE_A`...), множитель
   радиуса, флаги (works-in-water, breaks-obsidian), рецепт.
2. **DynamiteRegistry** — типы из конфига аддона (по образцу RegionTypeRegistry QPS).
3. **Активация** — слушатель `PlayerInteractEvent` (правый клик предметом):
   расходуем предмет, спавним `TNTPrimed` c PDC-меткой типа, `setFuseTicks`,
   `setYield(yield)`, источник — игрок (для атрибуции атак: QPS сам возьмёт
   `TNTPrimed.getSource()` как атакующего).
4. **DynamiteClassifier** — см. §3.1.
5. **Взрыв блоков** — `EntityExplodeEvent` (.MONITOR или HIGHEST):
   - С4/волна: добавить обсидиан/плачущий обсидиан в `blockList()`
     (и убрать воду/лаву из списка для волны — вернее, добавить блоки под ними);
   - волна: пометить сломанный обсидиан «рейд-блоком» на 5 минут (PDC блока
     или карта в аддоне; при попытке поставить — отмена с сообщением).
   Важно: НЕ удалять блоки, если QPS отменил взрыв (проверяйте `event.isCancelled()`),
   внутри чужих приватов блоки защищает флаг `explosion_damage` — QPS сам
   вычищает защищённые блоки из `blockList()` на приоритете LOW.
6. **Крафты** — `ShapedRecipe` с PDC-тегом на результате (чтобы крафт не
   конфликтовал с ванилью, и предмет было видно в конфиге QPS-ядер не нужен).
7. **Проверка «вредит ли»** перед активацией не обязательна — QPS сам решит
   по правилам `explosions`; аддону это нужно только для подсказок в лоре.

## 6. Замечания

- **Folia**: QPS весь шедулинг гоняет через свой мост; аддону достаточно
  пользоваться обычными Bukkit-событиями (они синхронны в регионе сущности)
  и не трогать блоки из async-потоков.
- **Атрибуция атак**: QPS записывает атакующим поджигавшего
  (`TNTPrimed.getSource()`), а если источник не игрок — ближайшего
  постороннего игрока рядом с ядром. Владельцы и участники привата
  атакующими не считаются.
- **Кулдаун урона** общий на приват (не на тип динамита): залпом из 5 ТНТ
  нельзя снять 5 прочностей — как на HolyWorld.
- **Уничтожение привата** на нуле прочности проходит через RegionDeleteEvent
  (Reason.DESTROYED_BY_RAID) — другие плагины могут отреагировать.
- Защита блоков и урон осаде — независимые вещи: `explosion_damage: false`
  в приват не впускает взрыв блоков, `explosions.TNT: false` запрещает
  снимать прочность. У HolyWorld дешёвые приваты ломаются «с одного
  динамита» — это прочность 1 + разрешённые типы взрывов.
