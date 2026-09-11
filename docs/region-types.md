# Типы приватов (regions.yml)

Всё про блоки-ядра: размер территории, прочность, флаги, голограммы, звуки,
предметы и крафты. Файл устроен как «умолчания + отличия»:

- `default_region` — значения по умолчанию для всех типов;
- `region_types.<БЛОК>` — только отличия конкретного типа.

Ключ типа — материал блока Minecraft (`DIAMOND_BLOCK`, …). Один материал
принадлежит только одному типу.

## Территория

```yaml
radius: 16        # от ядра; 16 → площадка 33×33
radius_x: 24      # неквадратная территория (важнее radius)
radius_z: 8
radius_y: 64      # высота вверх и вниз от ядра
full_height: false # true — от бедрока до неба (radius_y игнорируется)
```

## Прочность и прокачка

```yaml
start_durability: 2     # сколько взрывов выдержит с начала
max_durability: 10      # потолок прокачки
enable_durability_upgrade: true  # разрешить меню «Улучшение»
upgrade:                # переопределение оплаты для типа
  item: ""              # свой предмет; пусто — общий из config.yml
  cost_multiplier: -1   # -1 — общее значение
  tax: -1
```

## Лимиты и доступ

| Ключ | Описание |
| --- | --- |
| `max_per_player` | Лимит приватов типа на игрока (0 — без лимита) |
| `min_distance_to_others` | Минимальное расстояние между ядрами (блоков) |
| `place_permission` | Право на установку ядра; пусто — не нужно |
| `worlds` + `worlds_mode` | `whitelist` — только эти миры; `blacklist` — все кроме |

Лимит можно менять правами: `qweprotectstones.limit.<тип>.<число>`
(берётся максимальное) и `qweprotectstones.limit.unlimited`.

## Поведение

| Ключ | По умолчанию | Описание |
| --- | --- | --- |
| `return_block_on_remove` | true | Ядро выпадает на пол при удалении/поломке |
| `sneak_places_plain_block` | true | Зажав SHIFT, ядро ставится как обычный блок — без привата |
| `spawn-egg-use` | false | Разрешить доверенным яйцо призыва прямо на ядре |
| `menu` | "" | Какое меню открывает клик (`main`, `upgrade`, `effects`); пусто — авто |
| `border_color` | "" | Свой цвет каркаса, например "#00FF00" |
| `greeting` / `farewell` | "" | Тексты входа/выхода типа; важнее — тексты владельца |

## Предмет-ядро: получение и внешний вид

```yaml
restrict-obtaining: false
item:
  name: ""     # имя предмета (MiniMessage); пусто — стандартное
  lore: []     # описание, построчно
  glow: false  # свечение, как у зачарованного
recipe:
  pattern: ["AAA", "ABA", "AAA"]   # схема верстака, пробел — пусто
  ingredients: { A: DIAMOND, B: NETHER_STAR }
  result_amount: 1
```

- `restrict-obtaining: false` — блок работает как обычный блок Minecraft:
  добыл, поставил — приват создался. Оформление из `item` получают предметы,
  созданные плагином (`/qps give`, крафт).
- `restrict-obtaining: true` — «кастомный» предмет: приват создаёт только
  блок из `/qps give` или крафта по `recipe`. Добытый в мире блок того же
  материала остаётся обычным блоком. При возврате (поломка, `/ps delete`)
  предмет сохраняет оформление — покупка не теряет вид.

Прочность возвращаемого ядра сохраняется в PDC предмета, если включены
`settings.core-item-tags` и `settings.return-durability` (config.yml).

## Осада

```yaml
raid_immune: false          # true — взрывы вообще не тратят прочность
damage_cooldown_ticks: -1   # -1 — из siege.yml
explosion_damage_radius: -1 # -1 — из siege.yml
explosions:                 # каким видам взрывов можно вредить ядру
  TNT: true                 # ТНТ, вагонетка с ТНТ, гаст
  WITHER: true              # визер и черепа
  CREEPER: false
  ENDER_CRYSTAL: false
  BED: false                # кровать в Незере
```

Внимание: это про **прочность ядра**; защиту блоков внутри задаёт флаг
`explosion_damage`. Подробнее — [Осады](siege.md).

## Эффекты

```yaml
allowed_effects: [SPEED, SLOW, EXP_BOOST, ...]  # пусто — меню эффектов недоступно
```

Псевдоэффекты: `ALERTS` (уведомления о рейдах), `EXP_BOOST` (бустер опыта).
Подробнее — [Эффекты](effects.md).

## Флаги (начальные значения)

```yaml
flags:
  pvp: false                  # PvP между игроками
  entry: true                 # вход посторонних
  public_access: false        # «публичный доступ»: сундуки открыты всем
  monster_spawning: true      # спавн враждебных мобов
  animal_spawning: true       # спавн животных
  animal_protection: true     # защита животных от урона
  mob_griefing: false         # мобы ломают блоки
  fire_spread: false          # распространение огня
  liquid_flow_in: false       # затекание жидкостей снаружи
  pistons_from_outside: false # поршни с внешней стороны
  crop_trample: false         # вытаптывание грядок
  explosion_damage: false     # ВЗРЫВЫ ЛОМАЮТ БЛОКИ внутри
  teleport_in: true           # телепорты внутрь (эндер-жемчуг)
  item_pickup: true           # подбор предметов посторонними
  greeting: true              # приветствие при входе
  leaf_decay: true            # опадание листвы
  ice_and_snow: true          # образование льда и снега
  block_growth: true          # рост растений
```

Игрок меняет их через `/ps flag`, если флаг не закрыт в
`flags.locked` (protection.yml).

## Голограммы

```yaml
hologram: true               # показывать над ядром
hologram_under_attack: true  # осадный набор строк во время атаки
hologram_provider: MODERN    # MODERN (FancyHolograms) | DECENT (DecentHolograms)
hologram_offset: 1.1         # высота первой строки над ядром
hologram_display_range: 16   # с какого расстояния видно
hologram_lines: ["%type%", "Владелец: %owner%", ...]
hologram_lines_under_attack: ["%type%", "⚔ ПОД АТАКОЙ", ...]
hologram_settings: { ... }   # см. Голограммы
```

Все настройки — на странице [Голограммы](holograms.md).

## Звук и молния на события

```yaml
create:   { sound: ENTITY_PLAYER_LEVELUP, volume: 1.0, pitch: 1.0, lightning: false }
damage:   { sound: ENTITY_GENERIC_EXPLODE, volume: 1.0, pitch: 0.5, lightning: false }
remove:   { sound: BLOCK_BEACON_DEACTIVATE, volume: 1.0, pitch: 0.5, lightning: true }
```

`lightning: true` — чисто визуальный эффект молнии.

## Плейсхолдеры для строк

Работают в `hologram_lines`, `greeting`, `farewell` и текстах меню:

`%type%` (display_name с цветами) · `%owner%` · `%name%` · `%members%` ·
`%size%` · `%durability%` · `%max_durability%` · `%id%` · `%item%` ·
`%siege%` · `%penalty%` · `%attacks%`

`display_name` в голограмму автоматически **не** подставляется — только
плейсхолдером `%type%`, и это единственная автоматическая подстановка.
