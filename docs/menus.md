# Меню (GUI)

Все меню — обычные YAML-файлы в папке `menus/`:

- `main.yml` — меню по клику на ядро;
- `upgrade.yml` — прокачка прочности;
- `effects.yml` — магазин эффектов.

Формат одинаков для всех файлов. Какое меню открывает клик по ядру — ключ `menu`
у типа в regions.yml (пусто — плагин выберет сам: `main`, если доступна прокачка,
иначе `effects`).

## Предмет

```yaml
menu_title: "<bold>Управление регионом</bold>"
size: 27                  # кратно 9
items:
  upgrade_btn:            # имя — любое, оно только для вас
    slot: 11              # один слот; slots списком — сразу несколько
    priority: 100         # порядок отрисовки при конфликте за слот
    material: ANCIENT_DEBRIS   # %region_material% = блок-ядро привата
    display_name: "<bold>Улучшить прочность</bold>"
    lore:
      - "<#BDC3C7>Штраф от атак: <#F1C40F>%penalty%"
    amount: 1
    enchantments:
      - "unbreaking:1"
    # view_requirement — условия, при которых предмет ВИДЕН
    # click_requirement — условия клика (не прошёл — deny_commands)
    click_commands:
      - "[sound] UI_BUTTON_CLICK"
```

Тексты — MiniMessage: `<bold>`, `<#RRGGBB>`, `<gradient:#A:#B>`
(стопов может быть больше — `<gradient:#A:#B:#C>` — для плавного перелива).

## Действия (click_commands)

Выполняются по порядку; если оплата не прошла — цепочка обрывается:

| Действие | Описание |
| --- | --- |
| `[close]` | Закрыть меню |
| `[refresh]` | Перерисовать меню |
| `[openguimenu] <имя>` | Открыть другое меню из `/menus/` |
| `[message] <текст>` | Сообщение игроку |
| `[player] <команда>` | Команда от имени игрока |
| `[console] <команда>` | Команда от имени консоли |
| `[sound] ЗВУК[:громкость[:высота]]` | Звук |
| `[connect] <сервер>` | Перевести на сервер (BungeeCord) |
| `[takemoney] <сумма>` | Снять деньги (нужен Vault) |
| `[takeexp] <уровни>` | Снять уровни опыта |
| `[takepoints] <число>` | Снять очки (нужен PlayerPoints) |
| `[region_add_effect] <ИМЯ> <уровень>` | Выдать эффект привату |

Строка без `[префикса]` игнорируется — пишите `[player]` явно.

## Требования

`view_requirement` — предмет видим только при выполнении условий;
`click_requirement` — клик срабатывает только при выполнении (иначе
выполняются `deny_commands`, например звук «нельзя»).

```yaml
click_requirement:
  requirements:
    money:
      type: "has money"
      amount: 500
    siege:
      type: "under_siege"
```

Типы: `has money` / `has points` / `has exp` (amount), `has permission`
(permission), `region_durability_enabled` / `region_durability_disabled`,
`is_owner`, `under siege`, `trust_level` (level), `has effect` /
`does not have effect` (effect_name), `allowed_effect` / `not_allowed_effect`
(effect_name).

## Плейсхолдеры

`%player%` `%region_id%` `%owner%` `%name%` `%members%` `%durability%`
`%max_durability%` `%penalty%` `%siege%` `%effect_level_<ИМЯ>%` — и любые
из PlaceholderAPI, если он установлен.

## Анимация

Предметы могут мерцать/меняться покадрово (период — `timings.animation_period_ticks`
в config.yml). Меню перерисовываются только когда приват реально изменился
(счётчик версии) — анимация не гоняет лишние перерисовки.

## Встроенные меню вместо своих

Если хотите отдать меню стороннему плагину (DeluxeMenus и т.п.) —
`settings.custom_menus` в config.yml:

```yaml
custom_menus:
  main:
    action: "COMMAND"
    commands:
      - "[console] dm open custom_ps_main %player%"
```

Доступны `%player%` (ник) и `%region_id%` (короткий id привата).
