# Рынок и аренда

Требуется **Vault** и любой экономический плагин. Все настройки —
`features.yml` → `market`.

## Продажа

```yaml
market:
  sell:
    enable: true
    max-price: 1000000     # потолок цены в /ps sell
    tax-percent: 0         # комиссия при продаже, % (0 — без неё)
    allow-during-siege: false  # разрешить сделки под осадой
```

- `/ps sell <цена>` — выставить приват, в котором стоите.
- `/ps buy` — купить приват, в котором стоите (деньги уходят продавцу
  за вычетом комиссии).
- Смена владельца: покупатель становится `OWNER`, все доверенные сохраняются.

## Аренда

```yaml
market:
  rent:
    enable: true
    max-price: 100000          # потолок цены за один период
    min-duration-minutes: 10   # границы длительности периода
    max-duration-minutes: 4320
    trust-level: "manager"     # уровень доступа арендатора
    check-interval-minutes: 5  # как часто проверять истёкшие аренды
```

- `/ps rent offer <цена> <минуты>` — опубликовать условия аренды.
- `/ps rent take` — снять приват в аренду (или продлить текущую).
- `/ps rent cancel` — снять объявление (владельцем).

Когда период заканчивается, доступ арендатора отзывается автоматически
(проверка по расписанию `check-interval-minutes`). Приват возвращается
владельцу в исходном виде.

## Изменение границ (features.yml → resize)

Рыночные команды соседствуют с изменением территории:

```yaml
resize:
  block-during-siege: true   # запретить expand/move под осадой
  expand:
    enable: true
    max-step: 10             # максимум за одну команду (блоков)
    max-radius: 32           # потолок полуширины (0 = не ограничено)
    vertical: false          # разрешить up/down
    required-trust: "manager"
  move:
    enable: true
    max-distance: 64         # от текущего ядра за один перенос
    # блоки, на которые можно поставить ядро при переносе
    replaceable:
      - AIR
      - WATER
      - SHORT_GRASS
      - SNOW
    required-trust: "owner"
```

- `/ps expand [число] [all|horizontal|up|down]` — расширить границы.
- `/ps move` — перенести ядро: территория следует за ним.

## Телепорт домой (features.yml → home)

```yaml
home:
  warmup-seconds: 0     # задержка телепорта (0 = мгновенно)
  cancel-on-move: true  # срывать при движении
  cancel-on-damage: true # срывать при уроне
  cooldown-seconds: 0   # перезарядка (0 = без неё)
```

`/ps home [id]` — телепорт к привату (по умолчанию — единственному своему,
или укажите id из `/ps list`).
