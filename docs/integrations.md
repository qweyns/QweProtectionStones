# Интеграции

Все зависимости опциональны — плагин работает и без них.

## Карты

Границы приватов рисуются на картах (visuals.yml → `map`).

### Dynmap

```yaml
map:
  dynmap:
    enable: true
    layer_name: "Приваты"
    line_color: "#A78BFA"
    fill_color: "#C4B5FD"
    fill_opacity: 0.25
```

### BlueMap (API 2.x)

```yaml
map:
  bluemap:
    enable: true
    marker_set_label: "Приваты"
    line_color: "#A78BFA"
    fill_color: "#C4B5FD"
    fill_opacity: 0.25
    line_width: 2
    default_hidden: false  # скрыть слой по умолчанию
    toggleable: true        # включается кнопкой в интерфейсе карты
```

## PlaceholderAPI

Включается в features.yml → `placeholders.enabled`. Префикс `%qps_`:

| Плейсхолдер | Значение |
| --- | --- |
| `regions_count` | Приватов у игрока |
| `regions_total` | Приватов на сервере |
| `regions_area` | Суммарная охраняемая площадь игрока (блоков) |
| `standing_id` | Короткий id привата под ногами |
| `standing_owner` | Владелец привата под ногами |
| `standing_name` | Название (или ник владельца) |
| `standing_type` | Тип привата |
| `standing_size` | Размер, например `33x33` |
| `standing_durability` / `standing_max_durability` | Прочность |
| `standing_members` | Число участников |
| `standing_trust` | Ваш уровень доступа (или «нет») |
| `standing_attacks` | Сколько всего атак пережил приват |
| `standing_last_attacker` | Кто атаковал последним |
| `standing_penalty` | Множитель штрафа починки (0 — штрафа нет) |
| `standing_siege` | Идёт ли осада прямо сейчас |

## Экономика

**Vault** (деньги) и **PlayerPoints** (очки) используются для оплаты эффектов
(`effects.purchase.cost-type`), действий в меню (`[takemoney]`,
`[takepoints]`) и рынка (features.yml → `market`).

## Уведомления

features.yml → `notifications`. Сообщение уходит, когда у привата
с эффектом `ALERTS` снимают прочность. Одинаковые уведомления шлются
не чаще раза в 15 секунд.

```yaml
notifications:
  discord_webhook: ""            # URL вебхука
  telegram_bot_token: ""
  telegram_chat_id: ""
  discordsrv:
    enable: true                 # те же уведомления через DiscordSRV
    channel: ""                  # имя канала из config DiscordSRV; пусто — главный
```

## Импорт

`/qps import <wg|ps|gp>` — перенос приватов из других плагинов. Чужие файлы
только читаются; сами плагины ставить не нужно.

```yaml
# features.yml -> import
import:
  type-id: "DIAMOND_BLOCK"  # тип по умолчанию для импортированных
  create-holograms: false   # голограммы у виртуальных ядер
  worldguard:
    data-folder: "plugins/WorldGuard"        # регионы из worlds/*/regions.yml
    type-by-material: true   # тип PS-региона по материалу его блока
    flag-mapping:            # флаги WorldGuard -> наши
      pvp: "PVP"
      entry: "ENTRY"
      mob-spawning: "MONSTER_SPAWNING"
  protectionstones:
    data-folder: "plugins/WorldGuard"        # берём только ps*-регионы
  griefprevention:
    data-folder: "plugins/GriefPreventionData" # клеймы из ClaimData/*.yml
```

| Команда | Откуда |
| --- | --- |
| `/qps import wg` | WorldGuard |
| `/qps import ps` | ProtectionStones (его регионы внутри WorldGuard) |
| `/qps import gp` | GriefPrevention |

Импортированные приваты получают виртуальное ядро в центре границ.
ProtectionStones помечает свои регионы флагом `ps-block-material` —
при `type-by-material: true` тип подбирается по материалу блока один
в один (ключ типа в `regions.yml` = материал), а логические флаги
WorldGuard (`pvp`, `entry`, `tnt`…) переносятся согласно `flag-mapping`
(`allow` → `true`, `deny` → `false`; строковые флаги вроде `greeting`
пропускаются).

## Бэкапы и выгрузки

- `/qps export` — выгрузить все приваты в `exports/regions_<дата>.json`;
- `/qps restore <файл>` — восстановить из выгрузки;
- плановые бэкапы (features.yml → `backup`): `enable`, `interval-minutes`
  (минимум 10), `keep-count` — сколько последних файлов держать в `exports/`.

```yaml
backup:
  enable: false
  interval-minutes: 720
  keep-count: 10
```
