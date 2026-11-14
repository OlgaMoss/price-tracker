# price-tracker

Система мониторинга цен Steam Store API на Spring Boot, Kafka и PostgreSQL.
Документация описывает исходники базового commit `ab635693ca71375ea41b76ad44369a3c88440dbe`.

## Реализованные модули

| Gradle-модуль | Назначение | Spring Boot entry point |
| --- | --- | --- |
| `common` | Общий контракт `ProductPriceMessage` | Нет |
| `parser-service` | Получение цен и публикация в Kafka | `io.github.solar.parser.ParserServiceApplication.main` |
| `analytics-service` | Обработка цен и сохранение в PostgreSQL | `io.github.solar.analytics.AnalyticsServiceApplication.main` |

Состав модулей задан в [settings.gradle](settings.gradle), Java toolchain 26 и Spring Boot plugin 4.1.0 — в [build.gradle](build.gradle).

## Текущий поток

Steam Store API → parser-service → Kafka `raw-prices` → analytics-service → PostgreSQL.
Parser запускается по Scheduler или однократно через RunOnce. Аналитика сохраняет
первую цену и последующие изменения, сравнивая входящую цену с текущей.
Снижение только логируется: `PriceDropEvent`, `price-drops` и `notification-service` пока не реализованы.

## Документация

- [Архитектура, контракт, история и ограничения](docs/architecture.md) — подтверждения по исходным файлам и классам, Mermaid и отдельный список планов.
- [Режимы запуска и инфраструктура](docs/running.md) — Scheduler, RunOnce, настройки, Compose и K8s.
- [Справочные материалы](HELP.md).
- Стандарты разработки: [CLAUDE.md](CLAUDE.md) и [.cursorrules](.cursorrules).

Документацию следует обновлять вместе с PR, меняющим соответствующее поведение.
