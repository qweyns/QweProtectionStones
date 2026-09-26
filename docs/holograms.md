[← QweProtectStones](../README.md) / [Документация](README.md)

![VISUALS](https://img.shields.io/badge/QPS-VISUALS-a78bfa?style=flat-square&labelColor=181825)

# 🔮 Голограммы

> [!IMPORTANT]
> На Folia всегда используется NATIVE. FancyHolograms и DecentHolograms доступны только на Paper.

<details>
<summary><strong>На этой странице</strong></summary>

[Строки](#строки) · [Положение и видимость](#положение-и-видимость) · [hologram_settings](#hologram_settings)

</details>

---

Над ядром каждого привата висит голограмма: владелец, прочность, осада —
что захотите. Три провайдера: **FancyHolograms** (рекомендуется,
`MODERN`), **DecentHolograms** (`DECENT`) и встроенный `NATIVE`
(голограммы на TextDisplay, сторонние плагины не нужны).

Выбор провайдера — `hologram_provider` в regions.yml (можно на тип).
На **Folia всегда используется NATIVE**; мосты DH/FH доступны только на Paper.
На Paper при отсутствии выбранного плагина используется NATIVE.
Голограммы выгружаются вместе с чанками — на пустых чанках нагрузки нет.

`NATIVE` понимает те же настройки `hologram_settings`, что и мосты
(смещение, подложка, тень, масштаб, billboard, выравнивание, видимость
по дальности). Исключение — `permission`: право на просмотр работает
только у DecentHolograms и FancyHolograms.

## Строки

Настраиваются на тип (regions.yml):

```yaml
hologram_lines:              # обычный вид
  - "%type%"
  - "<#F2EFFA>Владелец: <#C4B5FD>%owner%"
  - "<#F2EFFA>Прочность: <#C4B5FD>[ %durability% / %max_durability% ]"
hologram_lines_under_attack: # вид во время осады
  - "%type%"
  - "<#FF5555>⚔ ПОД АТАКОЙ"
```

- Строки целиком ваши: `display_name` типа не подставляется автоматически —
  только плейсхолдером `%type%` (вместе с цветами).
- Поддерживается MiniMessage: `<bold>`, `<#RRGGBB>`, `<gradient:#A:#B>`
  (стопов может быть больше: `<gradient:#A:#B:#C>` — плавнее перелив).
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
написанные раньше, продолжают работать). Приоритет для каждого поля:
современный ключ конкретного типа → старый ключ конкретного типа →
современный общий ключ → старый общий ключ → fallback в коде.
В новых примерах используется только `hologram_settings`.

### У обоих провайдеров

| Ключ | Описание |
| --- | --- |
| `update_interval` | Период обновления текста, тиков |
| `see_through` | Видно ли текст сквозь блоки |
| `permission` | Право на просмотр; пусто — видно всем |

### FancyHolograms и NATIVE (с оговорками ниже)

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

NATIVE не поддерживает permission-фильтрацию и внешние режимы
`PERMISSION_REQUIRED`/`MANUAL`: видимость ограничивается расстоянием.
Не рассчитывайте на эти параметры как на контроль доступа на Folia.
Полный restart обязателен для выгрузки плагина; горячий unload не поддерживается.

---

[← Эффекты](effects.md) · [Все разделы](README.md) · [Меню и интерфейс →](menus.md)
