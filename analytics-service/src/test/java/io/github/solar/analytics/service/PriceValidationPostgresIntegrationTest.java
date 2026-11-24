package io.github.solar.analytics.service;

import io.github.solar.analytics.exception.InvalidProductPriceMessageException;
import io.github.solar.common.dto.ProductPriceMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.github.solar.analytics.kafka.ProductPriceJsonTestSupport.deserialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Проверяет committed-состояние PostgreSQL после принятия или отказа сообщения реальным сервисом.
 * Тесты не оборачиваются общей транзакцией и читают таблицы через JDBC, минуя persistence context.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PriceValidationPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private PriceAnalysisService service;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Удаляет данные предыдущего сценария без изменения схемы БД.
     */
    @BeforeEach
    void clearPersistedData() {
        jdbc.update("delete from price_history");
        jdbc.update("delete from products");
    }

    /**
     * Проверяет отсутствие товара и истории в реальной БД после отказа нового сообщения.
     */
    @ParameterizedTest(name = "new product: {0}")
    @MethodSource("invalidMessages")
    void shouldLeaveBothTablesEmpty_WhenNewProductMessageIsInvalid(
            String scenario, ProductPriceMessage message, String field) {
        // Given — PostgreSQL пуст перед обработкой нового товара.
        assertThat(products()).isEmpty();
        assertThat(history()).isEmpty();

        // When — реальный транзакционный сервис отклоняет входные данные.
        Throwable failure = catchThrowable(() -> service.analyzePrice(message));

        // Then — проверяем ошибку и фактическое отсутствие committed-записей обеих таблиц.
        assertThat(failure).isInstanceOf(InvalidProductPriceMessageException.class).hasMessageContaining(field);
        assertThat(products()).isEmpty();
        assertThat(history()).isEmpty();
    }

    /**
     * Проверяет неизменность всех колонок товара и всей истории после отказа обновления.
     */
    @ParameterizedTest(name = "existing product: {0}")
    @MethodSource("invalidMessagesForExistingProduct")
    void shouldKeepDatabaseSnapshot_WhenExistingProductMessageIsInvalid(
            String scenario, ProductPriceMessage message, String field) {
        // Given — создание и изменение цены уже committed отдельными вызовами сервиса.
        service.analyzePrice(validMessage("29.99"));
        service.analyzePrice(validMessage("19.99"));
        List<Map<String, Object>> productsBefore = products();
        List<Map<String, Object>> historyBefore = history();
        assertThat(productsBefore).singleElement()
                .satisfies(product -> assertThat(product).containsEntry("external_id", message.getExternalId()));
        assertThat(historyBefore).hasSize(2);

        // When — пробуем применить невалидное обновление существующего товара.
        Throwable failure = catchThrowable(() -> service.analyzePrice(message));

        // Then — JDBC перечитывает все колонки, включая ID, цену, метаданные и временные отметки.
        assertThat(failure).isInstanceOf(InvalidProductPriceMessageException.class).hasMessageContaining(field);
        assertThat(products()).isEqualTo(productsBefore);
        assertThat(history()).isEqualTo(historyBefore);
    }

    /**
     * Проверяет JSON → десериализатор → сервис → PostgreSQL для граничных и точных цен.
     */
    @ParameterizedTest(name = "price={0}; existingProduct={1}")
    @MethodSource("validPrices")
    void shouldPersistExactValueInBothTables_WhenPriceIsValid(String price, boolean existingProduct) {
        // Given — при обновлении в БД есть отличающаяся исходная цена.
        if (existingProduct) {
            service.analyzePrice(validMessage("19.99"));
        }
        ProductPriceMessage message = validMessage(price);
        BigDecimal original = message.getPrice();

        // When — возвращение proxied-сервиса означает успешный commit бизнес-транзакции.
        service.analyzePrice(message);

        // Then — проверяем хранимые значения независимо от JPA-кэша и mock-репозиториев.
        BigDecimal productPrice = jdbc.queryForObject("select current_price from products", BigDecimal.class);
        BigDecimal historyPrice = jdbc.queryForObject(
                "select price from price_history order by id desc limit 1", BigDecimal.class);
        assertThat(productPrice).isEqualByComparingTo(price);
        assertThat(historyPrice).isEqualByComparingTo(price);
        assertThat(products()).hasSize(1);
        assertThat(history()).hasSize(existingProduct ? 2 : 1);
        assertThat(message.getPrice()).isEqualTo(original);
    }

    private List<Map<String, Object>> products() {
        return jdbc.queryForList("select * from products order by id");
    }

    private List<Map<String, Object>> history() {
        return jdbc.queryForList("select * from price_history order by id");
    }

    private static ProductPriceMessage validMessage(String price) {
        return deserialize("{\"externalId\":\"steam-220\",\"title\":\"Half-Life 2\",\"price\":" + price
                + ",\"url\":\"https://example.com/220\"}");
    }

    private static Stream<Arguments> invalidMessages() {
        ProductPriceMessage nullId = validMessage("9.99");
        nullId.setExternalId(null);
        ProductPriceMessage longId = validMessage("9.99");
        longId.setExternalId("x".repeat(256));
        return Stream.concat(Stream.of(
                Arguments.of("null message", null, "message"),
                Arguments.of("null externalId", nullId, "externalId"),
                Arguments.of("long externalId", longId, "externalId")),
                invalidMessagesForExistingProduct());
    }

    private static Stream<Arguments> invalidMessagesForExistingProduct() {
        ProductPriceMessage blankTitle = validMessage("9.99");
        blankTitle.setTitle(" \t\n");
        ProductPriceMessage nullPrice = validMessage("9.99");
        nullPrice.setPrice(null);
        ProductPriceMessage longTitle = validMessage("9.99");
        longTitle.setTitle("t".repeat(256));
        ProductPriceMessage longUrl = validMessage("9.99");
        longUrl.setUrl("u".repeat(513));
        ProductPriceMessage nulUrl = validMessage("9.99");
        nulUrl.setUrl("https://example.com/\0");
        return Stream.of(
                Arguments.of("blank title", blankTitle, "title"),
                Arguments.of("null price", nullPrice, "price"),
                Arguments.of("negative price", validMessage("-0.01"), "price"),
                Arguments.of("over maximum price", validMessage("100000000"), "price"),
                Arguments.of("rounding required 12.345", validMessage("12.345"), "price"),
                Arguments.of("long title", longTitle, "title"),
                Arguments.of("long url", longUrl, "url"),
                Arguments.of("NUL url", nulUrl, "url"));
    }

    private static Stream<Arguments> validPrices() {
        return Stream.of(false, true).flatMap(existing ->
                Stream.of("0", "0.01", "12.340", "99999999.99", "99999999.990")
                        .map(price -> Arguments.of(price, existing)));
    }
}
