package io.github.solar.analytics.kafka;

import io.github.solar.analytics.exception.InvalidProductPriceMessageException;
import io.github.solar.analytics.repository.PriceHistoryRepository;
import io.github.solar.analytics.repository.ProductRepository;
import io.github.solar.analytics.service.PriceAnalysisService;
import io.github.solar.common.dto.ProductPriceMessage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Проверяет специализированную ошибку валидации на границе listener после реализации BUG-02.
 */
class ProductPriceConsumerValidationTest {

    /**
     * Проверяет сохранение исходного объекта ошибки, включая обработку null-сообщения.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRethrowSameValidationFailure_WhenServiceRejectsMessage(boolean nullMessage) {
        // Given — сервис возвращает специализированную ошибку, которую нельзя скрыть обёрткой.
        PriceAnalysisService service = mock(PriceAnalysisService.class);
        ProductPriceConsumer consumer = new ProductPriceConsumer(service);
        ProductPriceMessage message = nullMessage ? null : invalidPriceMessage();
        String field = nullMessage ? "message" : "price";
        InvalidProductPriceMessageException original =
                new InvalidProductPriceMessageException(field, "контрольная причина отказа");
        doThrow(original).when(service).analyzePrice(message);

        // When — ошибка пересекает границу listener.
        Throwable failure = catchThrowable(() -> consumer.listenProductPrices(message));

        // Then — тип, поле, причина и идентичность объекта сохранены.
        assertThat(failure).isSameAs(original).hasMessage(field + ": контрольная причина отказа");
        assertThat(original.getField()).isEqualTo(field);
    }

    /**
     * Проверяет реальный путь listener → сервис → validator без вызовов репозиториев.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldExposeValidationFailure_WhenRealServiceRejectsMessage(boolean nullMessage) {
        // Given — реальный сервис использует mock-репозитории только как контроль отсутствия обращений.
        ProductRepository products = mock(ProductRepository.class);
        PriceHistoryRepository history = mock(PriceHistoryRepository.class);
        ProductPriceConsumer consumer = new ProductPriceConsumer(new PriceAnalysisService(products, history));
        ProductPriceMessage message = nullMessage ? null : invalidPriceMessage();

        // When — проверяем null-сообщение либо цену, требующую округления.
        Throwable failure = catchThrowable(() -> consumer.listenProductPrices(message));

        // Then — listener оставляет специализированный тип и понятную причину отказа.
        assertThat(failure).isInstanceOf(InvalidProductPriceMessageException.class);
        InvalidProductPriceMessageException validationFailure = (InvalidProductPriceMessageException) failure;
        assertThat(validationFailure.getField()).isEqualTo(nullMessage ? "message" : "price");
        assertThat(validationFailure).hasMessageContaining(nullMessage ? "сообщение обязательно" : "без округления");
        verifyNoInteractions(products, history);
    }

    private static ProductPriceMessage invalidPriceMessage() {
        return ProductPriceMessage.builder()
                .externalId("steam-220")
                .title("Half-Life 2")
                .price(new BigDecimal("12.345"))
                .build();
    }
}
