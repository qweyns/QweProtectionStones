[← QweProtectStones](../README.md) / [Документация](README.md)

![REFERENCE](https://img.shields.io/badge/QPS-REFERENCE-a78bfa?style=flat-square&labelColor=181825)

# ⌨️ Команды и права

> [!NOTE]
> В примерах используется /ps. Фактическое имя и алиасы задаются в settings.command и применяются после рестарта.

<details>
<summary><strong>На этой странице</strong></summary>

[Игровая команда](#игровая-команда) · [Команды игроков](#команды-игроков) · [Административная команда](#административная-команда) · [Права](#права) · [Совместимость старой справки](#совместимость-старой-справки)

</details>

---

## Игровая команда

Имя и алиасы задаются в `config.yml` → `settings.command` и регистрируются
при запуске сервера (смена имени — только после рестарта). По умолчанию `/ps`
с алиасами `/приват`, `/region`, `/land`, `/rg`. Если имя занято другим
плагином, команда остаётся доступна как `/qweprotectstones:<имя>`.

Многие команды действуют на приват, **в котором вы стоите** — id указывать
не нужно. Там, где `[id]` упомянут, подходит короткий id из `/ps list`
или голограммы (`%id%`).

## Команды игроков

### Управление доступом

| Команда | Описание |
| --- | --- |
| `/ps trust <ник> [уровень]` | Выдать доступ. Уровни: `access`, `container`, `build`, `manager` |
| `/ps untrust <ник>` | Отозвать доступ |
| `/ps trustall <ник> [уровень]` | Выдать доступ сразу во всех своих приватах |
| `/ps untrustall <ник>` | Отозвать везде |
| `/ps invite <ник> [уровень]` | Пригласить — игрок сам принимает (`/ps accept`, `/ps deny`) |
| `/ps ban <ник>` | Заблокировать вход конкретному игроку |
| `/ps unban <ник>` | Разблокировать |
| `/ps banlist` | Список заблокированных |
| `/ps members` | Участники с уровнями доступа |
| `/ps autoadd [ник]` | Авто-вписывание друга во все новые приваты |
| `/ps autoremove <ник>` | Убрать из списка авто-вписывания |
| `/ps autolist` | Список авто-вписывания |

Уровни доверия (от меньшего к большему): `ACCESS` (двери, кнопки) →
`CONTAINER` (сундуки) → `BUILD` (строить) → `MANAGER` (управлять) → `OWNER`.
Какой уровень нужен для какого действия — настраивается в `config.yml`
(секция `trust`). Игроки с правом `qweprotectstones.admin` проходят везде.

### Информация

| Команда | Описание |
| --- | --- |
| `/ps` | Справка по доступным командам (или меню привата при `help.no-args: menu`) |
| `/ps menu` | Меню привата |
| `/ps info` | Карточка привата + подсветка границ. Свой — по праву `info`; **чужой** — только с `info.others` (по умолчанию op), показывается развёрнутая карточка: границы, участники с уровнями, история атак |
| `/ps list` | Свои приваты и те, куда вписали |
| `/ps flag [флаг] [значение]` | Флаги: просмотр (`true`/`false`/`reset`) |
| `/ps log [страница]` | Журнал действий доверенных |

### Территория

| Команда | Описание |
| --- | --- |
| `/ps findspot [тип]` | Ближайшее свободное место под приват |
| `/ps glow` | Постоянная подсветка границ |
| `/ps delete` | Удалить приват (с подтверждением) |


### Рынок

| Команда | Описание |
| --- | --- |
| `/ps sell <цена>` | Выставить приват на продажу |
| `/ps buy` | Купить приват, в котором стоите |
| `/ps rent offer <цена> <минуты>` | Предложить аренду |
| `/ps rent take` | Снять аренду (или продлить) |
| `/ps rent cancel` | Снять объявление |

Требуется Vault и экономический плагин. Настройки — в `features.yml`
(секция `market`), подробнее — [Рынок и аренда](market.md).

### Прочее

| Команда | Описание |
| --- | --- |
| `/ps home [id]` | Телепорт к привату |
| `/ps transfer <ник>` | Предложить приват игроку; он принимает `/ps transfer accept` или отклоняет `deny` (отключается `transfer.require-confirmation`) |
| `/ps name <текст>` | Название привата (`clear` — убрать); алиасы `rename`, `title` |
| `/ps upgrade` · `/ps effects` | Меню прокачки и эффектов |
| `/ps help [страница]` | Постраничная справка — только команды, доступные вам |

## Административная команда

`/qweprotectstones` (алиасы `/qps`, `/qpstones`) объявлена в `plugin.yml`
и не зависит от настроек игровой команды. По умолчанию требует право
`qweprotectstones.admin`.

| Команда | Описание |
| --- | --- |
| `/qps reload` | Перезагрузить конфиги, языки, меню, типы приватов |
| `/qps bypass` | Режим обхода защиты (для себя) |
| `/qps info [id]` | Полная карточка + статистика атак |
| `/qps delete [id]` | Удалить любой приват |
| `/qps save` | Немедленно сохранить всё в базу |
| `/qps stats` | Приваты, типы, владельцы, рынок (продажи/аренда/штрафы), очередь записи в БД |
| `/qps export` | Выгрузка всех приватов в `exports/regions_<дата>.json` |
| `/qps restore <файл>` | Восстановление из выгрузки |
| `/qps backup` | Внеплановый бэкап в `backups/`; `/qps restore` видит файлы из `exports/` и `backups/` |
| `/qps cleanup` | Удалить заброшенные приваты сейчас |
| `/qps import <wg\|ps\|gp>` | Импорт из WorldGuard / ProtectionStones / GriefPrevention; типы PS — по материалу блока, флаги — по `flag-mapping` |
| `/qps give <игрок> <тип> [кол-во]` | Выдать блоки-ядра (с тегом типа и оформлением) |
| `/qps setdurability <число>` | Прочность привата под ногами |
| `/qps setmax <число>` | Потолок прочности |
| `/qps settype <тип>` | Сменить тип привата |
| `/qps setbounds <id> <minX> <minY> <minZ> <maxX> <maxY> <maxZ>` | Задать границы вручную (с проверкой пересечений) |
| `/qps tp <id>` | Телепорт к ядру |
| `/qps flag <id> <флаг> <значение>` | Флаг любого привата |
| `/qps transfer <id> <ник>` | Передать приват |
| `/qps setowner <id> <ник>` | Сменить владельца (полностью) |
| `/qps ban <id> <ник>` · `unban` | Чёрный список любого привата |
| `/qps members <id>` · `trust` · `untrust` | Управление чужим приватом |
| `/qps log [id] [кол-во]` | Журнал любого привата, в том числе удалённого (если `settings.action_log.keep_on_delete`). Для удалённого введите начало полного id, минимум 4 символа |
| `/qps perf [reset]` | Производительность: время обработчиков событий (вызовов в секунду, среднее, максимум), очередь записи в базу, приваты по мирам. Замеры отключаются `debug.perf-stats: false` |
| `/qps debug` | Диагностика: типы, счётчики, подробный режим |
| `/qps help [страница]` | Постраничная справка по административным командам |

## Права

Модель повторяет ProtectionStones: каждая игровая команда закрыта своим
правом. Отличие — по умолчанию они **разрешены всем** (плагин работает из
коробки без LuckPerms), а запрещаются сознательно. Справка `/ps help`
показывает только те команды, на которые у игрока есть право.

### Права игроков

| Право | Команды | По умолчанию |
| --- | --- | --- |
| `qweprotectstones.create` | Установка блока-ядра | все |
| `qweprotectstones.destroy` | Поломка собственного ядра | все |
| `qweprotectstones.info` | `/ps info` своего привата | все |
| `qweprotectstones.info.others` | `/ps info` **чужого** привата (развёрнутая карточка: границы, участники, атаки — как `/rg info` в WorldGuard) | **op** |
| `qweprotectstones.list` | `/ps list` | все |
| `qweprotectstones.members` | `/ps members` | все |
| `qweprotectstones.trust` | `/ps trust`, `untrust`, `trustall`, `untrustall` | все |
| `qweprotectstones.invite` | `/ps invite`, `accept`, `deny` | все |
| `qweprotectstones.ban` | `/ps ban`, `unban`, `banlist` | все |
| `qweprotectstones.flags` | `/ps flag` | все |
| `qweprotectstones.name` | `/ps name` | все |
| `qweprotectstones.home` | `/ps home` | все |
| `qweprotectstones.log` | `/ps log` | все |
| `qweprotectstones.findspot` | `/ps findspot` | все |
| `qweprotectstones.glow` | `/ps glow` | все |
| `qweprotectstones.buysell` | `/ps sell`, `/ps buy` | все |
| `qweprotectstones.rent` | `/ps rent` | все |
| `qweprotectstones.transfer` | `/ps transfer` | все |
| `qweprotectstones.delete` | `/ps delete` | все |
| `qweprotectstones.autoadd` | `/ps autoadd`, `autoremove`, `autolist` | все |

### Служебные права

| Право | Назначение | По умолчанию |
| --- | --- | --- |
| `qweprotectstones.admin` | Административная команда (включает `bypass`) | op |
| `qweprotectstones.bypass` | Обход защиты (`/qps bypass`) | op |
| `qweprotectstones.limit.<тип>.<число>` | Лимит приватов типа (берётся максимум из всех) | — |
| `qweprotectstones.limit.unlimited` | Без лимитов | false |
| `qweprotectstones.region.<тип>` | Установка ядра, если тип требует право (`place_permission` в regions.yml) | — |
| `qweprotectstones.admin.<действие>` | Право на конкретную подкоманду — только при `admin.require-per-action: true` | — |

Примеры: продавать «приваты только для VIP» — снять `create` у группы
default; отключить рынок — снять `buysell` и `rent`; модераторам — выдать
`info.others`, чтобы смотрели чужие карточки.

Пример: выдать модератору только `/qps tp` и `/qps info`:

```yaml
# config.yml -> admin
require-per-action: true
```

```yaml
# правах LuckPerms
qweprotectstones.admin.tp: true
qweprotectstones.admin.info: true
```

## Совместимость старой справки

Команд игроков `expand`, `move`, `greeting`, `farewell` нет.
Границы администрация меняет через `/qps setbounds`, сообщения входа/выхода
задаются в `lang/*.yml`, а флаг `greeting` включает их для региона.
В переводах `%command%` автоматически показывает фактическое имя команды вместо `/ps`.

---

[← Установка и обслуживание](installation.md) · [Все разделы](README.md) · [Конфигурация →](configuration.md)
