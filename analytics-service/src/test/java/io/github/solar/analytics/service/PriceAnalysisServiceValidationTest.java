package io.github.solar.analytics.service;

import io.github.solar.analytics.entity.PriceHistory;
import io.github.solar.analytics.entity.Product;
import io.github.solar.analytics.repository.PriceHistoryRepository;
import io.github.solar.analytics.repository.ProductRepository;
import io.github.solar.common.dto.ProductPriceMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Проверяет требования BUG-02 через публичный метод аналитики без привязки к будущему validator.
 */
@ExtendWith(MockitoExtension.class)
class PriceAnalysisServiceValidationTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @InjectMocks
    private PriceAnalysisService priceAnalysisService;

    /**
     * Проверяет отказ до первого чтения или сохранения для обеих ветвей обработки товара.
     */
    @ParameterizedTest(name = "{0}; existingProduct={3}")
    @MethodSource("invalidMessages")
    void shouldRejectBeforeRepositoryAccess_WhenMessageIsInvalid(
            String scenario, ProductPriceMessage message, String field, boolean existingProduct) {
        // Given — поиск настроен для обеих ветвей, но валидация должна предотвратить сам вызов.
        lenient().when(productRepository.findByExternalId(nullable(String.class)))
                .thenReturn(existingProduct ? Optional.of(existingProduct()) : Optional.empty());

        // When — вызываем существующий публичный метод.
        Throwable failure = catchThrowable(() -> priceAnalysisService.analyzePrice(message));

        // Then — случайный NPE не считается валидацией; проверяем также отсутствие любых обращений.
        assertAll(
                () -> assertThat(failure).isInstanceOf(RuntimeException.class)
                        .isNotInstanceOf(NullPointerException.class)
                        .hasMessageContaining(field),
                () -> verifyNoInteractions(productRepository, priceHistoryRepository));
    }

    /**
     * Проверяет точное сохранение допустимых представлений BigDecimal при создании и обновлении.
     */
    @ParameterizedTest(name = "price={0}; existingProduct={1}")
    @MethodSource("validPrices")
    void shouldSaveExactValue_WhenPriceIsRepresentableWithTwoDecimalPlaces(
            String price, boolean existingProduct) {
        // Given — BigDecimal строится из строки без промежуточного double.
        ProductPriceMessage message = message("externalId", "steam-220");
        message.setPrice(new BigDecimal(price));
        when(productRepository.findByExternalId(message.getExternalId()))
                .thenReturn(existingProduct ? Optional.of(existingProduct()) : Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When — обрабатываем допустимую цену.
        priceAnalysisService.analyzePrice(message);

        // Then — обе записи и входной DTO сохраняют исходное числовое значение.
        ArgumentCaptor<Product> product = ArgumentCaptor.forClass(Product.class);
        ArgumentCaptor<PriceHistory> history = ArgumentCaptor.forClass(PriceHistory.class);
        verify(productRepository).save(product.capture());
        verify(priceHistoryRepository).save(history.capture());
        assertThat(product.getValue().getCurrentPrice()).isEqualByComparingTo(price);
        assertThat(history.getValue().getPrice()).isEqualByComparingTo(price);
        assertThat(message.getPrice()).isEqualTo(new BigDecimal(price));
    }

    /**
     * Проверяет, что завершающий ноль не создаёт ложного изменения цены.
     */
    @Test
    void shouldSkipPersistence_WhenOnlyPriceScaleDiffers() {
        // Given — численно одинаковые цены имеют разный scale.
        Product product = existingProduct();
        product.setCurrentPrice(new BigDecimal("12.34"));
        ProductPriceMessage message = message("externalId", "steam-220");
        message.setPrice(new BigDecimal("12.340"));
        when(productRepository.findByExternalId(message.getExternalId())).thenReturn(Optional.of(product));

        // When — сравниваем цены через публичный метод.
        priceAnalysisService.analyzePrice(message);

        // Then — происходит только поиск товара, без записи товара и истории.
        verify(productRepository).findByExternalId(message.getExternalId());
        org.mockito.Mockito.verifyNoMoreInteractions(productRepository);
        verifyNoInteractions(priceHistoryRepository);
        assertThat(product.getCurrentPrice()).isEqualTo(new BigDecimal("12.34"));
    }

    /**
     * Проверяет допустимые границы строк и необязательность URL.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldAcceptBoundaryLengths_WhenRequiredFieldsArePresent(boolean nullUrl) {
        // Given — обязательные строки имеют максимальную длину, URL отсутствует либо достигает лимита.
        ProductPriceMessage message = message("externalId", "x".repeat(255));
        message.setTitle("t".repeat(255));
        message.setUrl(nullUrl ? null : "u".repeat(512));
        when(productRepository.findByExternalId(message.getExternalId())).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When — обрабатываем сообщение на разрешённых границах.
        priceAnalysisService.analyzePrice(message);

        // Then — товар и история сохраняются.
        verify(productRepository).save(any(Product.class));
        verify(priceHistoryRepository).save(any(PriceHistory.class));
    }

    private static Stream<Arguments> invalidMessages() {
        return Stream.of(false, true).flatMap(existing -> Stream.of(
                Arguments.of("null message", null, "message", existing),
                Arguments.of("null externalId", message("externalId", null), "externalId", existing),
                Arguments.of("empty externalId", message("externalId", ""), "externalId", existing),
                Arguments.of("blank externalId", message("externalId", " \t\n"), "externalId", existing),
                Arguments.of("long externalId", message("externalId", "x".repeat(256)), "externalId", existing),
                Arguments.of("NUL externalId", message("externalId", "steam\u0000-220"), "externalId", existing),
                Arguments.of("null title", message("title", null), "title", existing),
                Arguments.of("empty title", message("title", ""), "title", existing),
                Arguments.of("blank title", message("title", " \t\n"), "title", existing),
                Arguments.of("long title", message("title", "t".repeat(256)), "title", existing),
                Arguments.of("NUL title", message("title", "Half\u0000-Life"), "title", existing),
                Arguments.of("null price", message("price", null), "price", existing),
                Arguments.of("negative price", message("price", "-0.01"), "price", existing),
                Arguments.of("over maximum price", message("price", "100000000"), "price", existing),
                Arguments.of("rounding required 12.345", message("price", "12.345"), "price", existing),
                Arguments.of("long url", message("url", "u".repeat(513)), "url", existing),
                Arguments.of("NUL url", message("url", "https://example.com/\u0000"), "url", existing)));
    }

    private static Stream<Arguments> validPrices() {
        return Stream.of(false, true).flatMap(existing ->
                Stream.of("0", "0.01", "12", "12.3", "12.34", "12.340", "99999999.99")
                        .map(price -> Arguments.of(price, existing)));
    }

    private static ProductPriceMessage message(String field, String value) {
        ProductPriceMessage message = ProductPriceMessage.builder()
                .externalId("steam-220")
                .title("Half-Life 2")
                .price(new BigDecimal("9.99"))
                .url("https://store.steampowered.com/app/220")
                .build();
        switch (field) {
            case "externalId" -> message.setExternalId(value);
            case "title" -> message.setTitle(value);
            case "price" -> message.setPrice(value == null ? null : new BigDecimal(value));
            case "url" -> message.setUrl(value);
            default -> throw new IllegalArgumentException("Неизвестное поле тестовых данных: " + field);
        }
        return message;
    }

    private static Product existingProduct() {
        return Product.builder()
                .id(1L)
                .externalId("steam-220")
                .title("Half-Life 2")
                .currentPrice(new BigDecimal("19.99"))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }
}
