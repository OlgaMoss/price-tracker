package io.github.solar.analytics.kafka;

import io.github.solar.analytics.exception.KafkaMessageProcessingException;
import io.github.solar.analytics.service.PriceAnalysisService;
import io.github.solar.common.dto.ProductPriceMessage;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Проверяет делегирование и сохранение причин ошибок на публичной границе Kafka listener.
 */
class ProductPriceConsumerTest {

    private final PriceAnalysisService priceAnalysisService = mock(PriceAnalysisService.class);
    private final ProductPriceConsumer consumer = new ProductPriceConsumer(priceAnalysisService);

    /**
     * Проверяет передачу исходного сообщения сервису без изменения цены.
     */
    @Test
    void shouldDelegateOriginalMessage_WhenMessageIsValid() {
        // Given — цена с допустимым завершающим нулём.
        ProductPriceMessage message = message();

        // When — вызываем listener без Kafka-контейнера.
        consumer.listenProductPrices(message);

        // Then — сервис получает тот же объект ровно один раз.
        verify(priceAnalysisService).analyzePrice(message);
        verifyNoMoreInteractions(priceAnalysisService);
        assertThat(message.getPrice()).isEqualTo(new BigDecimal("12.340"));
    }

    /**
     * Проверяет сохранение исходного объекта инфраструктурной ошибки через обёртку listener.
     */
    @Test
    void shouldPreserveOriginalCause_WhenDatabaseIsUnavailable() {
        // Given — сервис сообщает об инфраструктурном отказе.
        ProductPriceMessage message = message();
        RuntimeException original = new DataAccessResourceFailureException("PostgreSQL unavailable");
        doThrow(original).when(priceAnalysisService).analyzePrice(message);

        // When — исключение пересекает границу listener.
        Throwable failure = catchThrowable(() -> consumer.listenProductPrices(message));

        // Then — существующая обёртка сохраняет исходное исключение.
        assertThat(failure).isInstanceOf(KafkaMessageProcessingException.class).hasCause(original);
    }

    /**
     * Проверяет сохранение причины неизвестной ошибки без признания её ошибкой содержимого.
     */
    @Test
    void shouldPreserveOriginalCause_WhenUnexpectedFailureOccurs() {
        // Given — тип ошибки не относится к заранее известной категории.
        ProductPriceMessage message = message();
        RuntimeException original = new IllegalStateException("Unexpected analysis failure");
        doThrow(original).when(priceAnalysisService).analyzePrice(message);

        // When — вызываем listener.
        Throwable failure = catchThrowable(() -> consumer.listenProductPrices(message));

        // Then — исходный объект остаётся доступен через cause.
        assertThat(failure).isInstanceOf(KafkaMessageProcessingException.class).hasCause(original);
    }

    /**
     * Проверяет null-safe делегирование и сохранение ошибки сервиса без будущих production-классов.
     */
    @Test
    void shouldPreserveServiceFailure_WhenMessageIsNull() {
        // Given — маркер представляет ошибку проверки null-сообщения внутри сервиса.
        RuntimeException original = new RuntimeException("message: сообщение обязательно");
        doThrow(original).when(priceAnalysisService).analyzePrice(null);

        // When — null не должен ломать логирование до обращения к сервису или в catch.
        Throwable failure = catchThrowable(() -> consumer.listenProductPrices(null));

        // Then — проверяем идентичность причины независимо от наличия транспортной обёртки.
        assertAll(
                () -> assertThat(causeChain(failure)).contains(original),
                () -> verify(priceAnalysisService).analyzePrice(null));
    }

    private static List<Throwable> causeChain(Throwable failure) {
        List<Throwable> causes = new ArrayList<>();
        for (Throwable current = failure; current != null && !causes.contains(current); current = current.getCause()) {
            causes.add(current);
        }
        return causes;
    }

    private static ProductPriceMessage message() {
        return ProductPriceMessage.builder()
                .externalId("steam-220")
                .title("Half-Life 2")
                .price(new BigDecimal("12.340"))
                .build();
    }
}
