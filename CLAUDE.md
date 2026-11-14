# Steam Price Monitoring — Project Context for Claude

## Архитектура

Текущее состояние: три Gradle-модуля — `common`, `parser-service`, `analytics-service`.
`common` содержит общий DTO; Spring Boot приложения — только parser и analytics.
Стек в `build.gradle`: Java toolchain 26, Spring Boot plugin 4.1.0; сервисы используют Kafka и JPA/PostgreSQL.

**Реализовано**:

- `parser-service`: Scheduler либо RunOnce → Steam Store API → Kafka (`raw-prices`).
- `analytics-service`: Kafka (`raw-prices`) → сравнение с текущей ценой → PostgreSQL.
- История содержит первую цену и изменения в обе стороны; неизменная цена не записывается.
- Снижение цены логируется; публикация события пока является TODO.

**Планируется**: `PriceDropEvent`, публикация в `price-drops`, `notification-service`
и уведомления через Telegram / Email / Console. Эти компоненты не реализованы.

Подтверждения по файлам и классам: [архитектура](docs/architecture.md).
Режимы запуска и инфраструктурные ограничения: [запуск](docs/running.md).
Примеры в стандартах ниже иллюстрируют оформление и не являются перечнем реализованных классов.

---

## Стандарты кода

### Документация
- Каждый класс/интерфейс — JavaDoc с описанием архитектурной роли.
- JavaDoc для методов — только бизнес-логика и публичные методы (`@param`, `@return`).
- Inline-комментарии — объясняют *почему*, а не *что*.
- **Язык**: комментарии и JavaDoc — русский; код (классы, методы, переменные) — английский.

### Clean Code
- **Lombok**: `@Data`, `@Value`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`. Геттеры/сеттеры/конструкторы вручную не писать.
- **Логирование**: только `log.info/debug/error`. `System.out.println` запрещён. В лог передавать контекст (например, `id` товара).
- **Маппинг**: MapStruct или статические мапперы/фабричные методы. Бизнес-логику сеттерами не захламлять.
- **Ошибки**: пустые `catch`-блоки запрещены. Логировать через `log.error("msg", e)`, пробрасывать в Custom Exceptions.

### Тесты
- **Фреймворки**: JUnit 5, Mockito, AssertJ (`assertThat()`).
- **Структура**: обязательный AAA с комментариями `// Given`, `// When`, `// Then`.
- **Именование**: `should[ОжидаемыйРезультат]_When[Условие]`.
- **Интеграционные тесты**: Testcontainers для Kafka и Postgres. H2 не использовать.
- Тесты и методы обязательно документировать.

Пример теста:
```java
@Test
void shouldTriggerPriceDrop_WhenNewPriceIsLowerThanHistorical() {
    // Given — подготовка данных и моков
    SteamProductPrice rawPrice = createMockPrice(50.0);
    when(repository.findHistoricalMin(any())).thenReturn(100.0);

    // When — выполнение тестируемого действия
    PriceDelta result = analyticsService.calculateDelta(rawPrice);

    // Then — проверка результатов (AssertJ)
    assertThat(result.isDrop()).isTrue();
    assertThat(result.getPercentage()).isEqualTo(50.0);
}
```

### Kubernetes
- Все манифесты содержат labels: `app: steam-monitor`, `component: [name]`, `tier: backend`.
- Секреты (Telegram-токен, пароль Postgres) — только через `Secret`/`ConfigMap` + `envFrom`/`secretKeyRef`. Прямо в манифестах запрещено.
- Указывать `resources.limits` и `resources.requests`.

---

## Инструкции для Claude

- При написании кода: сначала показать структуру класса/файла, затем полная реализация без пропусков (не писать `// здесь ваш код`).
- Предлагать оптимизации по производительности: индексы JPA для частых запросов, batch-processing для Kafka.
