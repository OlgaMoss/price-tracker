package io.github.solar.analytics.kafka;

import io.github.solar.analytics.exception.InvalidProductPriceMessageException;
import io.github.solar.analytics.validation.ProductPriceMessageValidator;
import io.github.solar.common.dto.ProductPriceMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static io.github.solar.analytics.kafka.ProductPriceJsonTestSupport.deserialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Контрактные проверки JSON → Kafka JsonDeserializer → общий DTO → бизнес-валидация.
 */
class ProductPriceJsonContractTest {

    private static final String LEGACY_JSON =
            "{\"externalId\":\"steam-220\",\"title\":\"Half-Life 2\",\"price\":12.340,\"url\":\"https://example.com/220\"}";

    /**
     * Проверяет точное числовое значение и совместимость четырёхполевого legacy JSON без type headers.
     */
    @Test
    void shouldAcceptLegacyMessageWithoutNumericLoss_WhenPriceHasTrailingZero() {
        // Given — JSON старого контракта содержит числовой literal, а не строку или double.
        ProductPriceMessage message = deserialize(LEGACY_JSON);
        BigDecimal deserializedPrice = message.getPrice();

        // When — выполняем production-валидацию.
        ProductPriceMessageValidator.validate(message);

        // Then — все четыре поля прочитаны, а валидация не меняет десериализованную цену.
        assertThat(message.getExternalId()).isEqualTo("steam-220");
        assertThat(message.getTitle()).isEqualTo("Half-Life 2");
        assertThat(message.getUrl()).isEqualTo("https://example.com/220");
        assertThat(message.getPrice()).isEqualByComparingTo("12.340").isEqualTo(deserializedPrice);
    }

    /**
     * Проверяет отказ для JSON-цены, которая требует округления до масштаба 2.
     */
    @Test
    void shouldRejectWithoutRounding_WhenJsonPriceIs12345() {
        // Given — фактический десериализатор сохраняет недопустимое числовое значение.
        ProductPriceMessage message = deserialize(LEGACY_JSON.replace("12.340", "12.345"));

        // When — проверяем точность после десериализации.
        Throwable failure = catchThrowable(() -> ProductPriceMessageValidator.validate(message));

        // Then — ошибка относится к price, а цена не была округлена или исправлена.
        assertThat(failure).isInstanceOf(InvalidProductPriceMessageException.class)
                .hasMessageContaining("без округления");
        assertThat(((InvalidProductPriceMessageException) failure).getField()).isEqualTo("price");
        assertThat(message.getPrice()).isEqualByComparingTo("12.345");
    }

    /**
     * Проверяет отсутствие неявных значений для null и отсутствующих обязательных полей legacy JSON.
     */
    @ParameterizedTest(name = "{0}; explicitNull={1}")
    @MethodSource("missingRequiredFields")
    void shouldRejectWithoutDefaults_WhenRequiredFieldIsMissingOrNull(String field, boolean explicitNull) {
        // Given — удаляем поле либо заменяем его JSON-значение на null до десериализации.
        String member = switch (field) {
            case "externalId" -> "\"externalId\":\"steam-220\"";
            case "title" -> "\"title\":\"Half-Life 2\"";
            case "price" -> "\"price\":12.340";
            default -> throw new IllegalArgumentException(field);
        };
        String json = explicitNull
                ? LEGACY_JSON.replace(member, "\"" + field + "\":null")
                : LEGACY_JSON.replace(member + ",", "");
        ProductPriceMessage message = deserialize(json);

        // When — отсутствующие данные проверяются теми же правилами, что и новые сообщения.
        Throwable failure = catchThrowable(() -> ProductPriceMessageValidator.validate(message));

        // Then — DTO содержит null, а отказ явно называет некорректное поле.
        Object actual = switch (field) {
            case "externalId" -> message.getExternalId();
            case "title" -> message.getTitle();
            default -> message.getPrice();
        };
        assertThat(actual).isNull();
        assertThat(failure).isInstanceOf(InvalidProductPriceMessageException.class).hasMessageContaining(field);
        assertThat(((InvalidProductPriceMessageException) failure).getField()).isEqualTo(field);
    }

    /**
     * Проверяет, что необязательный URL не получает подставленного значения.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldAcceptLegacyMessage_WhenUrlIsMissingOrNull(boolean explicitNull) {
        // Given — сообщение содержит обязательные поля без пригодного для сохранения URL.
        String json = explicitNull
                ? LEGACY_JSON.replace("\"url\":\"https://example.com/220\"", "\"url\":null")
                : LEGACY_JSON.replace(",\"url\":\"https://example.com/220\"", "");
        ProductPriceMessage message = deserialize(json);

        // When — выполняем обычную проверку сообщения.
        ProductPriceMessageValidator.validate(message);

        // Then — URL остаётся null, остальные значения сохранены.
        assertThat(message.getUrl()).isNull();
        assertThat(message.getPrice()).isEqualByComparingTo("12.340");
    }

    /**
     * Проверяет JSON null всего сообщения без неявного создания пустого DTO.
     */
    @Test
    void shouldRejectWithoutDefaults_WhenJsonMessageIsNull() {
        // Given — literal null является корректным JSON, но не сообщением о цене.
        ProductPriceMessage message = deserialize("null");

        // When — проверяем результат фактической десериализации.
        Throwable failure = catchThrowable(() -> ProductPriceMessageValidator.validate(message));

        // Then — нет подставленного объекта, и причина отказа относится ко всему сообщению.
        assertThat(message).isNull();
        assertThat(failure).isInstanceOf(InvalidProductPriceMessageException.class).hasMessageContaining("message");
    }

    private static Stream<Arguments> missingRequiredFields() {
        return Stream.of("externalId", "title", "price")
                .flatMap(field -> Stream.of(false, true).map(explicitNull -> Arguments.of(field, explicitNull)));
    }
}
