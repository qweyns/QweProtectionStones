# Конфигурация

Настройки разложены по темам, чтобы каждый файл был коротким. Отсутствующие
ключи берутся из встроенных значений по умолчанию, поэтому после обновлений
старые конфиги продолжают работать. Почти всё применяется командой
`/qps reload`; исключения — имя команды и настройки базы данных.

| Файл | Содержание |
| --- | --- |
| `config.yml` | База данных, команда, лимиты, тайминги, доверие, заброшенные, журнал, свои меню |
| `protection.yml` | Воронки, трюки через границу, флаги, закрытые от игроков |
| `siege.yml` | Осады — см. [Осады](siege.md) |
| `effects.yml` | Эффекты — см. [Эффекты](effects.md) |
| `visuals.yml` | Частицы, звуки, карты |
| `features.yml` | Границы, рынок, бэкапы, телепорт, импорт, плейсхолдеры, уведомления |
| `regions.yml` | Типы приватов — см. [Типы приватов](region-types.md) |

## config.yml

### database

```yaml
database:
  type: "SQLITE"        # SQLITE или MYSQL
  table_prefix: "qps_"  # префикс таблиц
  # дальше — только для MYSQL:
  host: "localhost"
  port: 3306
  database: "qweprotectstones"
  username: "root"
  password: ""
  use_ssl: false
  pool_size: 10         # для SQLITE всегда 1
```

### settings

| Ключ | Описание |
| --- | --- |
| `core-item-tags` | PDC-теги на предметах-ядрах: `/qps give` помечает блок типом |
| `return-durability` | Возвращать при поломке накопленную прочность (в PDC предмета) |
| `language` | `ru_RU`, `en_US`, `es_ES`, `zh_CN` |
| `command.name` / `command.aliases` | Игровая команда и алиасы (рестарт) |
| `region_cooldown_seconds` | Пауза между созданием приватов (антиспам; 0 — выкл) |
| `preview-messages` | Подсказка в actionbar при ядре в руке: влезет ли приват |
| `invite_expire_seconds` | Сколько живёт приглашение `/ps invite` |
| `upgrade_item` | Предмет оплаты прокачки (тип может переопределить) |
| `upgrade_cost_multiplier` / `upgrade_tax` | Цена уровня: N × множитель + налог |
| `menu_command_radius` | С какого расстояния `/ps menu` достаёт до ядра |

**Заброшенные приваты** (`settings.abandoned`): `enable`, `inactive_days`,
`check_interval_minutes`, `keep_upgraded` (не трогать прокачанные).

**Журнал действий** (`settings.action_log`): `enable`, `log_containers`
(записывать открытие сундуков), `keep_days`.

**Свои меню** (`settings.custom_menus`): позволяет отдать меню стороннему
плагину (DeluxeMenus и т.п.). Для каждого из `main` / `upgrade` / `effects`:
`action: MENU` (встроенное) или `COMMAND` + список команд с `%player%` и
`%region_id%` (форматы `[console]` / `[message]` / от игрока).

### timings

Всё, что раньше было константами в коде. Ноль и меньше — откат к дефолту:

| Ключ | По умолчанию | Описание |
| --- | --- | --- |
| `menu_click_cooldown_ms` | 300 | Антидребезг кликов по меню |
| `delete_confirm_seconds` | 30 | Окно подтверждения `/ps delete` |
| `deny_message_cooldown_ms` | 2000 | Как часто повторять «нет доступа» |
| `move_throttle_ms` | 300 | Пересчёт эффектов при ходьбе |
| `effect_refresh_ticks` | 40 | Период обновления эффектов |
| `effect_duration_ticks` | 60 | Длительность эффекта (больше периода) |
| `db_flush_ticks` | 60 | Период сброса изменений в базу |
| `animation_period_ticks` | 10 | Шаг мерцания границ (glow) |

### limits

`autoadd_friends` (50), `db_batch_size` (500), `log_page_size` (15),
`log_max_page_size` (50), `findspot_rings` (24).

### help

Справка `/ps help` и `/qps help` постраничная, с кнопками перелистывания.
Каждая строка подсказывает команду по клику; игрок видит только команды,
на которые у него есть право.

```yaml
help:
  page-size: 8        # команд на страницу в /ps help
  admin-page-size: 10 # команд на страницу в /qps help
```

### region-messages

Куда писать приветствие/прощание. Порядок текста: строка владельца
(`/ps greeting`, `/ps farewell`) → текст типа из `regions.yml` → шаблон
`region_enter` / `region_leave` из lang-файла.

```yaml
region-messages:
  enter:
    enabled: true
    channel: "CHAT"   # CHAT | ACTIONBAR | NONE
  leave:
    enabled: true
    channel: "CHAT"
```

Флаг привата `greeting` (`/ps flag greeting`) выключает оба сообщения
для конкретного привата.

### trust

Минимальный уровень для действий внутри чужого привата
(`access` < `container` < `build` < `manager` < `owner`):

```yaml
trust:
  required:
    interact: ACCESS      # двери, кнопки, рычаги, кровати
    container: CONTAINER  # сундуки, бочки, печи, воронки
    build: BUILD          # ставить и ломать блоки
    entity: BUILD         # рамки, стойки брони, вагонетки
    manage: MANAGER       # меню, журнал, приглашения
  flag_edit_level: MANAGER    # кто меняет флаги
  member_edit_level: MANAGER  # кто выдаёт доступ и банит
```

### admin

`permission-prefix` (база прав подкоманд), `require-per-action`
(отдельное право на каждую подкоманду), `give.max-amount` (потолок выдачи),
`teleport-offset-y` (высота приземления `/qps tp`).

## protection.yml

### protection.hoppers

| Ключ | По умолчанию | Описание |
| --- | --- | --- |
| `enable` | true | Включить защиту от автоматических переносов |
| `block-outflow` | true | Запретить вытягивать предметы из чужих контейнеров |
| `block-inflow` | false | Запретить заталкивать предметы в чужие контейнеры |

### protection.border

Трюки через границу привата:

| Ключ | По умолчанию | Описание |
| --- | --- | --- |
| `frost-walker` | true | Ледоход не замораживает воду в чужом привате |
| `mob-trails` | true | Следы мобов (снег голема) подчиняются флагу MOB_GRIEFING |
| `bonemeal` | true | Костная мука снаружи не прорастает в чужой приват |
| `fishing` | true | Удочка не вытаскивает мобов и предметы из чужого привата |

### flags.locked

Флаги, которые игрок **не может** менять сам через `/ps flag` — только админ:

```yaml
flags:
  locked:
    - pvp
    - entry
```

## visuals.yml

### visuals.particle — частицы границ

`type` (учитывается у DUST), `size`, `density` (0.5 — вдвое плотнее,
2.0 — вдвое реже и дешевле), `max_points` (потолок точек на каркас).

### visuals.boundaries — подсветка

Цвета кратковременной подсветки: `color_open` (создание), `color_closed`
(удаление), `color_info` (`/ps info`), `show_time_ticks`.

### sounds

Формат: `ЗВУК`, `ЗВУК:громкость` или `ЗВУК:громкость:высота`.
`none` / `off` / `false` — выключить. Опечатка в имени не роняет плагин —
в лог уйдёт предупреждение. Свои значения для: `menu_denied`, `menu_success`,
`raid_attack`, `raid_destroyed`, `raid_nearby`, `intruder_alert`,
`invite_received`.

### map — Dynmap и BlueMap

См. [Интеграции → Карты](integrations.md#карты).
