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

## Сборка и тесты

Для локальной проверки нужны JDK 26 и запущенный Docker с доступом к образу `postgres:16`. Из корня репозитория выполните ту же команду, которую использует CI:

```bash
./gradlew --no-daemon clean build
```

Gradle Wrapper собирает `common`, `parser-service` и `analytics-service` и запускает все unit- и smoke-тесты. В `analytics-service` smoke-тест запускает PostgreSQL 16 через Testcontainers. В `parser-service` smoke-тест использует встроенный Kafka-брокер, а тесты HTTP-клиента обращаются к локальному WireMock. Для этой проверки достаточно запустить Docker. Вручную поднимать Kafka или PostgreSQL и передавать production credentials не требуется.

После сборки в каждом модуле с тестами доступны отчёты:

- `<module>/build/reports/tests/test/index.html` — отчёт для просмотра в браузере.
- `<module>/build/test-results/test/` — результаты в формате JUnit XML.

### Проверка в GitHub

GitHub Actions запускает проверку **Gradle build and tests** при открытии, обновлении и повторном открытии pull request в `develop`, а также при push в `develop`.

В pull request откройте вкладку **Checks** и выберите **Gradle build and tests**, чтобы увидеть результат и лог сборки. Для push откройте соответствующий запуск во вкладке **Actions**.

Чтобы скачать XML- и HTML-отчёты, откройте страницу запуска workflow и выберите **gradle-test-reports** в разделе **Artifacts**. Отчёты хранятся 7 дней и доступны в том числе при падении тестов. Если сборка остановилась до запуска тестов, причину ошибки можно увидеть в логе сборки.

## Документация

- [Архитектура, контракт, история и ограничения](docs/architecture.md) — подтверждения по исходным файлам и классам, Mermaid и отдельный список планов.
- [Режимы запуска и инфраструктура](docs/running.md) — Scheduler, RunOnce, настройки, Compose и K8s.
- [Справочные материалы](HELP.md).
- Стандарты разработки: [CLAUDE.md](CLAUDE.md) и [.cursorrules](.cursorrules).

Документацию следует обновлять вместе с PR, меняющим соответствующее поведение.
