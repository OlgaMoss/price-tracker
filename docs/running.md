# Режимы запуска и инфраструктура

Описание основано на исходниках; команды запуска при подготовке документации не выполнялись.
[Архитектура и ограничения](architecture.md) описаны отдельно.

## Выбор режима parser-service

[ParserServiceApplication](../parser-service/src/main/java/io/github/solar/parser/ParserServiceApplication.java)
включает scheduling. Условия создания бинов выбирают один из двух режимов:

| Режим | Условие | Поведение и источник |
| --- | --- | --- |
| Scheduler | `steam.parser.run-once=false` либо свойство отсутствует | [SteamPriceParserScheduler](../parser-service/src/main/java/io/github/solar/parser/scheduler/SteamPriceParserScheduler.java): вызов `parseAndPublishAll()` по `steam.parser.schedule-cron` |
| RunOnce | `steam.parser.run-once=true` | [RunOnceParserRunner](../parser-service/src/main/java/io/github/solar/parser/runner/RunOnceParserRunner.java): один обход при старте через `ApplicationRunner`; после успеха `SpringApplication.exit` и `System.exit(0)` |

В обоих режимах используется [SteamPriceParsingService](../parser-service/src/main/java/io/github/solar/parser/service/SteamPriceParsingService.java).
Пустые результаты пропускаются; исключение прерывает текущий обход.
В RunOnce исключение возникает до участка успешного завершения. Отдельной логики продолжения
с последнего приложения или сохранения прогресса нет. Повторный запуск начинает обход заново.

## Настройки по умолчанию

Источники: [SteamParserProperties](../parser-service/src/main/java/io/github/solar/parser/config/SteamParserProperties.java),
[parser application.yaml](../parser-service/src/main/resources/application.yaml),
[analytics application.yaml](../analytics-service/src/main/resources/application.yaml).

| Настройка | Значение |
| --- | --- |
| `steam.parser.run-once` | `false` |
| `steam.parser.schedule-cron` | `0 */15 * * * *` — каждые 15 минут |
| `steam.parser.country-code` | `ru` — страна HTTP-запроса |
| `steam.parser.api-base-url` | `https://store.steampowered.com/api/appdetails` |
| `steam.parser.kafka-topic` | `raw-prices` |
| `steam.parser.request-delay-ms` | `1500` между запросами |
| `steam.parser.app-ids` | В YAML: `220,570,730,1091500,1245620,1174180`; в классе исходно пустой список |
| `spring.kafka.bootstrap-servers` | `localhost:9092` в обоих сервисах |
| Порт parser | `8082` |
| PostgreSQL analytics | `jdbc:postgresql://localhost:5433/price_tracker_db` |
| Порт в YAML analytics | `8081`; наличие настройки не подтверждает HTTP API |

Consumer [ProductPriceConsumer](../analytics-service/src/main/java/io/github/solar/analytics/kafka/ProductPriceConsumer.java)
использует литералы `raw-prices` и `analytics-group`. Изменение топика только в parser
не перенастраивает consumer автоматически.

## Локальная инфраструктура

[docker-compose.yml](../docker-compose.yml) описывает только PostgreSQL 15-alpine,
ZooKeeper и Kafka (Confluent 7.3.0). PostgreSQL опубликован на `5433`, Kafka — на `9092`.
Java-сервисы в Compose не включены. Конфигурация соответствует запуску Java-сервисов
на хосте по адресам из YAML, но успешность подключения здесь не проверена.

Compose содержит локальные учётные данные БД и volume `pgdata`.
Analytics использует `ddl-auto: update`; это не описывает процедуру миграции или восстановления данных.

## Kubernetes RunOnce

[parser-cronjob.yaml](../k8s/parser-cronjob.yaml) задаёт:

- Расписание `*/15 * * * *`, `concurrencyPolicy: Forbid` и `restartPolicy: OnFailure`.
- Образ `parser-service:latest` с `imagePullPolicy: IfNotPresent`.
- `STEAM_PARSER_RUN_ONCE=true` и `SPRING_MAIN_WEB_APPLICATION_TYPE=none`.
- Подключение [parser-configmap.yaml](../k8s/parser-configmap.yaml) и [parser-secret.yaml](../k8s/parser-secret.yaml) через `envFrom`.
- Requests/limits CPU и памяти; хранение трёх успешных и трёх неуспешных Jobs.

`Forbid` относится к данному CronJob и не исключает другие экземпляры парсера.
`OnFailure` не гарантирует успешную доставку, отсутствие повторов или восстановление прогресса.

## Непроверенные предпосылки окружения

- ConfigMap задаёт `kafka:9092`, а Compose рекламирует внутренний listener `kafka:29092`.
  Это разные описания окружений; правильный адрес K8s требует отдельного Kafka Service.
- В репозитории нет Dockerfile, развёртываний analytics/Kafka/PostgreSQL для K8s
  и подтверждения доступности образа `parser-service:latest`.
- Secret содержит шаблон `KAFKA_SASL_PASSWORD`, но подключение SASL в parser YAML не настроено.
  Наличие Secret не подтверждает аутентификацию Kafka.
- Доступность Steam, Kafka, БД и реальное выполнение обоих режимов не проверялись запуском.

Эти ограничения документируют текущие файлы; изменение инфраструктуры не входит в DOC-01.
