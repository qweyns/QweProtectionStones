<div align="center">

<img src="assets/banner.svg" alt="QweProtectStones — центр документации" width="1000" />

# 📚 Центр документации

**От первого ядра до собственной интеграции — всё в одном месте.**

[![Platform](https://img.shields.io/badge/Paper_API-1.21.4-67e8f9?style=flat-square&labelColor=181825)](installation.md)
[![Runtime](https://img.shields.io/badge/Java-21-fbbf24?style=flat-square&labelColor=181825)](installation.md)
[![Configuration](https://img.shields.io/badge/Config-YAML-a78bfa?style=flat-square&labelColor=181825)](configuration.md)

[← К проекту](../README.md) · [Начать установку](installation.md) · [Исходные конфиги](../src/main/resources) · [API](api.md)

</div>

---

## 🧭 Выберите свой маршрут

| Я настраиваю сервер | Я меняю оформление | Я разрабатываю аддон |
| :--- | :--- | :--- |
| **[1. Установить плагин](installation.md)** | **[1. Настроить меню](menus.md)** | **[1. Изучить API](api.md)** |
| [2. Создать типы приватов](region-types.md) | [2. Оформить голограммы](holograms.md) | [2. Подключить взрывы](explosions-api.md) |
| [3. Выдать права и доступ](commands.md) | [3. Выбрать звуки и частицы](configuration.md#visualsyml) | [3. Учесть Folia](installation.md#reload-и-folia) |
| [4. Проверить перед production](installation.md#проверка-перед-production) | [4. Добавить интеграции](integrations.md) | [4. Проверить игровые правила](siege.md) |

> [!IMPORTANT]
> **Обновление не заменяет ваши настройки.** Общие пропущенные ключи читаются
> из JAR. Новые типы, оформление меню и примеры переносятся вручную.
> Перед заменой JAR сохраните папку плагина и БД.

## 🗂️ Справочник

### Основа сервера

| Раздел | Что найдёте |
| :--- | :--- |
| [🚀 Установка и обслуживание](installation.md) | Запуск, обновление, БД, recovery и проверка сервера |
| [⌨️ Команды и права](commands.md) | Синтаксис, разрешения и уровни доступа |
| [⚙️ Конфигурация](configuration.md) | Файлы, параметры, диапазоны, язык и reload |
| [🪨 Типы приватов](region-types.md) | Размеры, флаги, предметы-ядра, рецепты и наследование |

### Игровые механики

| Раздел | Что найдёте |
| :--- | :--- |
| [⚔️ Осады](siege.md) | Прочность, урон, cooldown, штраф и уведомления |
| [🪙 Рынок и аренда](market.md) | Продажа, аренда, смена владельца и экономика |
| [✨ Эффекты](effects.md) | Баффы, ловушки, цели действия и оплата |

### Оформление и интеграции

| Раздел | Что найдёте |
| :--- | :--- |
| [🔮 Голограммы](holograms.md) | NATIVE, DH/FH, внешний вид и ограничения провайдеров |
| [🎛️ Меню и интерфейс](menus.md) | Предметы, требования, анимация и платные цепочки |
| [🔌 Интеграции](integrations.md) | Карты, PAPI, экономика, мессенджеры, импорт и backup |
| [🧑‍💻 API](api.md) | Фасад, события, потоки и доступ к данным |
| [💥 Взрывы и аддоны](explosions-api.md) | Классификация, радиус и безопасный межрегиональный урон |

## 💡 Быстрые ответы

<details>
<summary><strong>Где менять цену эффекта в магазине?</strong></summary>

В `menus/effects.yml`, действиями кнопки. `effects.purchase` относится к отдельному
EffectPurchaseManager. Правила оплаты и возврата — [в разделе меню](menus.md#платные-цепочки).

</details>

<details>
<summary><strong>Что перечитывается без рестарта?</strong></summary>

`/qps reload` перечитывает основные настройки, язык, меню и типы.
Для имени команды, БД, connect timeout HTTP-клиентов и регистрации интеграций
нужен рестарт. Подробности — [установка](installation.md#reload-и-folia).

</details>

<details>
<summary><strong>Можно ли подключить несколько серверов к общей БД?</strong></summary>

Нет общей шины синхронизации: один инстанс может перезаписать данные другого.
Используйте отдельную БД для каждого сервера. См. [хранение](installation.md#хранение-и-восстановление).

</details>

<details>
<summary><strong>Чем отличаются exports, backups и storage-recovery?</strong></summary>

`exports/` — ручные JSON-выгрузки, `backups/` — копии с ротацией.
`storage-recovery.yml` — неподтверждённые SQL-операции, не резервная копия.
**Не удаляйте recovery вручную.** Подробнее — [обслуживание](installation.md#хранение-и-восстановление).

</details>

> [!WARNING]
> **Folia:** только NATIVE-голограммы и полный restart для выгрузки плагина.
> Тесты CI не заменяют проверку живого сервера с вашей экономикой и интеграциями.

---

<div align="center">

[Исходные конфиги](../src/main/resources) · [Сборки и тесты](https://github.com/qweyns/QweProtectionStones/actions) · [Сообщить об ошибке](https://github.com/qweyns/QweProtectionStones/issues)

<sub>Документация текущего кода · Без обязательного WorldGuard или ProtectionStones</sub>

</div>
