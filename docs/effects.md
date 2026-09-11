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

## Оплата (effects.yml → effects.purchase)

```yaml
effects:
  purchase:
    cost-type: "none"     # none | money (Vault) | points (PlayerPoints) | item
    cost-amount: 100
    scale-with-amplifier: false  # уровень выше — дороже: цена × (уровень+1)
    item: "DIAMOND"       # предмет оплаты для cost-type: item
    per-effect:           # индивидуальные цены
      SPEED: 150
      # ALERTS: 500
  exp_boost_multiplier: 2.0
```

Повышение уровня эффекта покупается там же: каждый следующий уровень
дороже (если включён `scale-with-amplifier`).

## Как это работает под капотом

Эффекты обновляются периодически (`timings.effect_refresh_ticks`), длительность
порции чуть больше периода (`timings.effect_duration_ticks`), поэтому при
выходе с территории эффект исчезает сам. Пересчёт при ходьбе ограничен
`timings.move_throttle_ms`.
