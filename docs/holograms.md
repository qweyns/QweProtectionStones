# Голограммы

Над ядром каждого привата висит голограмма: владелец, прочность, осада —
что захотите. Нужен один из плагинов: **FancyHolograms** (рекомендуется,
провайдер `MODERN`) или **DecentHolograms** (провайдер `DECENT`).

Выбор провайдера — `hologram_provider` в regions.yml (можно на тип).
Голограммы выгружаются вместе с чанками — на пустых чанках нагрузки нет.

## Строки

Настраиваются на тип (regions.yml):

```yaml
hologram_lines:              # обычный вид
  - "%type%"
  - "<#F0F0F0>Владелец: <#FFaa00>%owner%"
  - "<#F0F0F0>Прочность: <#FFaa00>[ %durability% / %max_durability% ]"
hologram_lines_under_attack: # вид во время осады
  - "%type%"
  - "<#FF5555>⚔ ПОД АТАКОЙ"
```

- Строки целиком ваши: `display_name` типа не подставляется автоматически —
  только плейсхолдером `%type%` (вместе с цветами).
- Поддерживается MiniMessage: `<bold>`, `<#RRGGBB>`, `<gradient:#A:#B>`.
- Переключение на осадный вид — только при `hologram_under_attack: true`.

Плейсхолдеры строк: `%type%` `%owner%` `%name%` `%members%` `%size%`
`%durability%` `%max_durability%` `%id%` `%item%` `%siege%` `%penalty%`
`%attacks%`.

## Положение и видимость

| Ключ | По умолчанию | Описание |
| --- | --- | --- |
| `hologram` | true | Показывать голограмму у типа |
| `hologram_offset` | 1.1 | Высота первой строки над ядром |
| `hologram_display_range` | 16 | С какого расстояния видно |

## hologram_settings

Общие настройки работают и в `default_region`, и внутри конкретного типа
(старое имя секции `fancyholograms_settings` тоже читается — конфиги,
написанные раньше, продолжают работать).

### У обоих провайдеров

| Ключ | Описание |
| --- | --- |
| `update_interval` | Период обновления текста, тиков |
| `see_through` | Видно ли текст сквозь блоки |
| `permission` | Право на просмотр; пусто — видно всем |

### Только FancyHolograms

| Ключ | Описание |
| --- | --- |
| `shadow` | Тень у букв |
| `scale` | Размер текста |
| `billboard` | Поворот к игроку: `CENTER`, `VERTICAL`, `HORIZONTAL`, `FIXED` |
| `text_alignment` | `LEFT`, `CENTER`, `RIGHT` |
| `background` | Подложка, например `#40000000`; пусто — без неё |
| `visibility` | `ALL`, `PERMISSION_REQUIRED`, `MANUAL` |
| `shadow_radius` / `shadow_strength` | Тень «на земле» под голограммой |
| `interpolation_ticks` | Плавность перемещения |
| `translation` | Дополнительное смещение текста (x, y, z — в блоках) |
| `brightness` | Освещение текста (`block`/`sky`; -1 — как в окружении) |

### Только DecentHolograms

| Ключ | Описание |
| --- | --- |
| `update_range` | Радиус, в котором обновляется содержимое |
| `down_origin` | Считать точку низом голограммы, а не верхом |
