[← QweProtectStones](../README.md) / [Документация](README.md)

![CONFIG](https://img.shields.io/badge/QPS-CONFIG-a78bfa?style=flat-square&labelColor=181825)

# ⚙️ Конфигурация

> [!IMPORTANT]
> Существующие YAML не перезаписываются. Общие недостающие ключи читаются из JAR; типы и меню редактируются отдельно.

<details>
<summary><strong>На этой странице</strong></summary>

[config.yml](#configyml) · [protection.yml](#protectionyml) · [visuals.yml](#visualsyml) · [Дополнительные настраиваемые параметры](#дополнительные-настраиваемые-параметры) · [Локализация и хардкод](#локализация-и-хардкод)

</details>

---

Настройки разложены по темам, чтобы каждый файл был коротким. Отсутствующие
ключи берутся из встроенных значений по умолчанию, поэтому после обновлений
старые конфиги продолжают работать. Почти всё применяется командой
`/qps reload`; исключения — команда/алиасы, БД, connect timeout HTTP-клиентов
и регистрация интеграций при старте. Подробнее — [установка](installation.md).
Это fallback в памяти: существующие YAML не перезаписываются. Для `regions.yml`
наследование идёт из вашего `default_region`; меню редактируются отдельно.

| Файл | Содержание |
| --- | --- |
| `config.yml` | База данных, команда, лимиты, тайминги, доверие, заброшенные, журнал, свои меню |
| `protection.yml` | Воронки, трюки через границу, флаги, закрытые от игроков |
| `siege.yml` | Осады — см. [Осады](siege.md) |
| `effects.yml` | Эффекты — см. [Эффекты](effects.md) |
| `visuals.yml` | Частицы, звуки, карты |
| `features.yml` | Рынок, бэкапы, телепорт, импорт, плейсхолдеры, уведомления |
| `regions.yml` | Типы приватов — см. [Типы приватов](region-types.md) |

## config.yml

### database

```yaml
database:
  type: "SQLITE"        # SQLITE или MYSQL
  table_prefix: "qps_"  # префикс таблиц
  sqlite_busy_timeout_ms: 5000 # SQLite; 0..60000 мс, нужен рестарт
  # дальше — только для MYSQL:
  host: "localhost"
  port: 3306
  database: "qweprotectstones"
  username: "root"
  password: ""
  use_ssl: false
  pool_size: 10         # для SQLITE всегда 1
```

Один сервер — одна база данных. Кэш регионов живёт в памяти инстанса:
при подключении одной MySQL к сети серверов соседний инстанс не увидит
созданий, удалений и сделок рынка до перезапуска. Для сети серверов
используйте отдельную БД для каждого сервера; общей шины изменений в плагине нет.

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
`check_interval_minutes`, `keep_upgraded` (не трогать прокачанные),
`skip_listed` (не трогать выставленные на продажу и в аренду).

**Журнал действий** (`settings.action_log`): `enable`, `log_containers`
(записывать открытие сундуков), `keep_days`.

**Критический журнал** (`settings.critical_log`): файловая копия самых
важных событий — удаления, передачи, сделки рынка, административные
правки. В отличие от журнала в базе, файл переживает любые проблемы
с хранилищем: `enable`, `folder` (относительно папки плагина),
`file-name`, `rotate-size-mb` (при превышении файл переименовывается
в `.old`, хранится одна предыдущая копия).

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
  no-args: help       # /ps без аргументов: help — справка, menu — меню привата
  page-size: 8        # команд на страницу в /ps help
  admin-page-size: 10 # команд на страницу в /qps help
```

### region-messages

Тексты входа/выхода — шаблоны `region_enter` / `region_leave` из
lang-файла; игроки их менять не могут.

```yaml
region-messages:
  enter:
    enabled: true
    channel: "CHAT"   # CHAT | NONE
  leave:
    enabled: true
    channel: "CHAT"
```

Флаг привата `greeting` (`/ps flag greeting`) выключает оба сообщения
для конкретного привата.

### Роли (roles.yml)

Роли участников полностью настраиваются в `roles.yml`: id, название,
старшинство (`weight`), наследование (`inherit`) и список действий (`actions`).
Стандартные `access`, `container`, `build`, `manager` — обычные записи,
их можно переименовать, удалить или добавить свои. Встроенная только роль
владельца (`owner`), у неё все действия; название задаётся в `owner-display`.

```yaml
roles:
  owner-display: "Владелец"
  default-role: build            # /ps trust <ник> без роли, автодобавление, импорт
  public-access-role: container  # что даёт флаг public-access ("" — ничего)
  aliases: {}                    # старый id -> новый, если переименовали id
  list:
    farmer:
      display: "<#86EFAC>Фермер"
      weight: 25                 # выдавать/снимать можно только роли ниже своей
      grantable: true
      inherit: access
      actions: [build, container, -glow]   # "-действие" убирает унаследованное, "*" — все
```

Действия: `interact`, `container`, `build`, `entity`, `menu`, `manage`, `flags`,
`members`, `ban`, `rename`, `upgrade`, `home`, `view-members`, `glow`, `effects`,
`alerts`, `entry`. Продавать и сдавать приват в аренду может только владелец.

id роли хранится в базе. Если роль удалить из конфига, у участников с ней не будет
прав, но id не потеряется: верните роль или добавьте псевдоним в `aliases`.
Старая секция `trust` в `config.yml` больше не читается — при запуске в консоль
выводится предупреждение, если она осталась.

### admin

`permission-prefix` (база прав подкоманд), `require-per-action`
(отдельное право на каждую подкоманду), `give.max-amount` (потолок выдачи),
`teleport-offset-y` (высота приземления `/qps tp`).

### updates

```yaml
updates:
  enabled: true       # сравнивать версию с GitHub releases
  period-hours: 12    # как часто проверять
```

Напоминает в консоль и операторам при входе (право
`qweprotectionstones.update-notify`). Сеть используется только для этого
запроса.

### metrics

```yaml
metrics:
  enable: true     # анонимная статистика bStats
  plugin-id: 33994 # id плагина на bstats.org; 0 — ничего не отправлять
```

Отправляются число приватов и распределение по типам. Полностью
отключается `enable: false`.

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

### protection.pistons

| Ключ | По умолчанию | Описание |
| --- | --- | --- |
| `move-core` | false | Разрешить поршням двигать ядро привата — только внутри его границ; границы не меняются |

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

`type` (тип частицы; цвет учитывается только у DUST), `size`, `density` (0.5 — вдвое плотнее,
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

## Дополнительные настраиваемые параметры

Значения по умолчанию сохраняют прежнее поведение. Новые числовые параметры
ограничиваются диапазоном, не допускают бесконечного ожидания/переполнения;
NaN/Infinity у double заменяются fallback. Диапазоны — техническая защита.

| Файл | Ключ | По умолчанию | Диапазон / применение |
|---|---|---|---|
| config.yml | `database.sqlite_busy_timeout_ms` | 5000 | 0..60000 мс, рестарт |
| config.yml | `settings.critical_log.flush_ticks` | 100 | 1..72000 тиков, reload |
| config.yml | `settings.action_log.prune_interval_minutes` | 1440 | 1..525600 минут, reload |
| config.yml | `settings.action_log.prune_initial_delay_seconds` | 120 | 1..86400 секунд, reload |
| config.yml | `timings.autoadd_retry_ticks` | 100 | 1..72000 тиков, следующий повтор |
| config.yml | `updates.initial-delay-ticks` | 100 | 1..72000 тиков, reload |
| config.yml | `updates.http-timeout-seconds` | 10 | 1..120 секунд; request — следующий запрос, connect — рестарт |
| features.yml | `notifications.rate-limit-seconds` | 15 | 0..86400 секунд; 0 — без задержки, следующий alert |
| features.yml | `notifications.http-timeout-seconds` | 5 | 1..120 секунд; request — следующий запрос, connect — рестарт |
| siege.yml | `siege.damage_per_explosion` | 1 | 1..1000000 HP за Bukkit-взрыв, следующий взрыв |
| visuals.yml | `visuals.boundaries.vertical_radius` | 8 | 1..256 блоков вверх/вниз вокруг ядра, новая подсветка |
| visuals.yml | `visuals.boundaries.glow_color_first` | #00FF00 | Первый цвет /ps glow |
| visuals.yml | `visuals.boundaries.glow_color_second` | #008000 | Второй цвет /ps glow |
| visuals.yml | `visuals.damage_indicator.offset_y` | 1.2 | 0..16 блоков, следующий индикатор |
| visuals.yml | `visuals.damage_indicator.rise` | 1.2 | 0..16 блоков |
| visuals.yml | `visuals.damage_indicator.spread` | 0.2 | 0..1 блока; 0 — без случайного смещения |
| visuals.yml | `visuals.damage_indicator.animation_ticks` | 30 | 0..59 тиков, ограничение TextDisplay |
| visuals.yml | `visuals.damage_indicator.lifetime_ticks` | 35 | 1..1200 тиков |

## Локализация и хардкод

`lang/*.yml` содержит сообщения игроков. `%command%` подставляет фактически
зарегистрированное имя игровой команды (до рестарта оно прежнее, даже если
config уже отредактирован). `menu_default_title` — заголовок меню без `menu_title`.
Оформление конкретного меню задаётся в самом `menus/*.yml`; язык сообщений
не переводит автоматически ваши lore, названия типов и строк голограмм.

Консольные сообщения об ошибках, SQL-схема, имена PDC, HTTP-протокол,
защитные границы поиска и контракты атомарных операций остаются в коде.
Не следует превращать их в произвольные настройки: это нарушит совместимость
данных или позволит обойти защиту. Все значения fallback тоже остаются в JAR.

---

[← Команды и права](commands.md) · [Все разделы](README.md) · [Типы приватов →](region-types.md)
