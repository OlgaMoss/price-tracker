package io.github.solar.analytics.validation;

import io.github.solar.analytics.exception.InvalidProductPriceMessageException;
import io.github.solar.common.dto.ProductPriceMessage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Проверяет границы длины строк, содержащих символы за пределами BMP.
 */
class ProductPriceMessageValidatorTest {

    /**
     * Проверяет, что пара суррогатов UTF-16 учитывается как один Unicode code point.
     */
    @ParameterizedTest(name = "{0}: максимум {1} символов")
    @CsvSource({"externalId, 255", "title, 255", "url, 512"})
    void shouldCountCodePoints_WhenTextContainsSupplementaryCharacters(String field, int limit) {
        // Given — каждый символ занимает две кодовые единицы UTF-16.
        String supplementary = "\uD83D\uDE00";
        ProductPriceMessage atLimit = messageWithText(field, supplementary.repeat(limit));
        ProductPriceMessage overLimit = messageWithText(field, supplementary.repeat(limit + 1));

        // When — проверяем значения на границе и на один символ длиннее.
        Throwable atLimitFailure = catchThrowable(() -> ProductPriceMessageValidator.validate(atLimit));
        Throwable overLimitFailure = catchThrowable(() -> ProductPriceMessageValidator.validate(overLimit));

        // Then — лимит применяется к Unicode code points, а ошибка называет поле.
        assertThat(atLimitFailure).isNull();
        assertThat(overLimitFailure)
                .isInstanceOfSatisfying(InvalidProductPriceMessageException.class, failure ->
                        assertThat(failure.getField()).isEqualTo(field));
    }

    private static ProductPriceMessage messageWithText(String field, String text) {
        return ProductPriceMessage.builder()
                .externalId(field.equals("externalId") ? text : "steam-220")
                .title(field.equals("title") ? text : "Half-Life 2")
                .price(new BigDecimal("12.34"))
                .url(field.equals("url") ? text : null)
                .build();
    }
}
