# Эффекты

Эффекты — постоянные зелья на территории привата. Покупаются в меню
«Эффекты» (`/ps effects` или клик по ядру), действуют, пока игрок
на территории.

## Кому достаётся (effects.yml → effect_targets)

| Режим | Кому |
| --- | --- |
| `MEMBERS` | Владельцу и доверенным — баффы |
| `ENEMIES` | Посторонним, зашедшим на территорию, — ловушки |

```yaml
effect_targets:
  EXP_BOOST: MEMBERS
  SPEED: MEMBERS
  SLOW: ENEMIES
  WITHER: ENEMIES
  # ...и остальные эффекты
```

## Какие эффекты доступны

Список для каждого типа привата — `allowed_effects` в regions.yml.
Пустой список — меню эффектов недоступно. Имена — как у зачарований зелий:
`SPEED`, `SLOW`, `FAST_DIGGING`, `INCREASE_DAMAGE`, `INVISIBILITY`,
`HEALTH_BOOST`, `WITHER`, …

Псевдоэффекты:

- **ALERTS** — уведомления о рейдах (в чат и мессенджеры);
- **EXP_BOOST** — бустер опыта, множитель `effects.exp_boost_multiplier`.

## Оплата (menus/effects.yml)

Цена покупки задаётся действиями в `click_commands` кнопки —
`[takemoney]`, `[takepoints]` или `[takeexp]` перед `[region_add_effect]`.
Если после списания какое-то действие цепочки не удалось — деньги, очки
и уровни возвращаются игроку автоматически.

```yaml
# menus/effects.yml -> items.<кнопка>.click_commands
click_commands:
  - "[takemoney] 15000"
  - "[region_add_effect] SPEED 1"
```

Секция `effects.purchase` (в `effects.yml`) цен больше не списывает —
её значения читают только сторонние интеграции через API
(`EffectPurchaseManager`).

## Как это работает под капотом

Эффекты обновляются периодически (`timings.effect_refresh_ticks`), длительность
порции чуть больше периода (`timings.effect_duration_ticks`), поэтому при
выходе с территории эффект исчезает сам. Пересчёт при ходьбе ограничен
`timings.move_throttle_ms`.
