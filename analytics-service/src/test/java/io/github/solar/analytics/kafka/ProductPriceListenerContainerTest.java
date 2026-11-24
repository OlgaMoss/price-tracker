package io.github.solar.analytics.kafka;

import io.github.solar.analytics.exception.InvalidProductPriceMessageException;
import io.github.solar.analytics.exception.KafkaMessageProcessingException;
import io.github.solar.analytics.repository.PriceHistoryRepository;
import io.github.solar.analytics.repository.ProductRepository;
import io.github.solar.analytics.service.PriceAnalysisService;
import io.github.solar.common.dto.ProductPriceMessage;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.MethodKafkaListenerEndpoint;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.mock.MockConsumerFactory;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.solar.analytics.kafka.ProductPriceJsonTestSupport.deserialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Проверяет цепочку причин от production-listener через реальный Spring Kafka container и messaging adapter.
 * Endpoint настраивается вручную, MockConsumer подаёт готовый DTO, а сервис работает с mock-репозиториями
 * без транзакционного proxy. Interceptor наблюдает ошибки. Доставка с брокера, автоконфигурация Spring Boot,
 * recovery и подтверждение offset этим тестом не проверяются.
 */
class ProductPriceListenerContainerTest {

    /**
     * Проверяет различимость validation и инфраструктурного отказа через фактические обёртки Spring Kafka.
     *
     * @param infrastructureFailure проверять инфраструктурный отказ вместо ошибки точности цены
     * @throws Exception если container не доставил запись за отведённое время
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveFailureCategory_WhenRecordPassesThroughListenerContainer(boolean infrastructureFailure)
            throws Exception {
        // Given — реальный сервис и production-метод; mock-репозиторий моделирует только отказ БД.
        ProductRepository products = mock(ProductRepository.class);
        PriceHistoryRepository history = mock(PriceHistoryRepository.class);
        ProductPriceConsumer listener = new ProductPriceConsumer(new PriceAnalysisService(products, history));
        DataAccessResourceFailureException databaseFailure =
                new DataAccessResourceFailureException("PostgreSQL unavailable");
        if (infrastructureFailure) {
            when(products.findByExternalId("steam-220")).thenThrow(databaseFailure);
        }
        ProductPriceMessage message = deserialize(
                "{\"externalId\":\"steam-220\",\"title\":\"Half-Life 2\",\"price\":"
                        + (infrastructureFailure ? "12.340" : "12.345") + "}");
        Method method = ProductPriceConsumer.class.getMethod("listenProductPrices", ProductPriceMessage.class);
        KafkaListener annotation = method.getAnnotation(KafkaListener.class);
        String topic = annotation.topics()[0];
        TopicPartition partition = new TopicPartition(topic, 0);
        MockConsumer<String, ProductPriceMessage> transport = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        transport.schedulePollTask(() -> {
            transport.updateBeginningOffsets(Map.of(partition, 0L));
            transport.updateEndOffsets(Map.of(partition, 1L));
            transport.rebalance(List.of(partition));
            transport.addRecord(new ConsumerRecord<>(topic, 0, 0, "steam-220", message));
        });
        CompletableFuture<Exception> observedFailure = new CompletableFuture<>();
        AtomicInteger successfulInvocations = new AtomicInteger();
        ConcurrentKafkaListenerContainerFactory<String, ProductPriceMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new MockConsumerFactory<>(() -> transport));
        factory.setRecordInterceptor(new RecordInterceptor<>() {
            @Override
            public ConsumerRecord<String, ProductPriceMessage> intercept(
                    ConsumerRecord<String, ProductPriceMessage> record, Consumer<String, ProductPriceMessage> consumer) {
                return record;
            }

            @Override
            public void success(ConsumerRecord<String, ProductPriceMessage> record,
                                Consumer<String, ProductPriceMessage> consumer) {
                successfulInvocations.incrementAndGet();
            }

            @Override
            public void failure(ConsumerRecord<String, ProductPriceMessage> record, Exception exception,
                                Consumer<String, ProductPriceMessage> consumer) {
                observedFailure.complete(exception);
            }
        });
        DefaultMessageHandlerMethodFactory handlers = new DefaultMessageHandlerMethodFactory();
        handlers.afterPropertiesSet();
        MethodKafkaListenerEndpoint<String, ProductPriceMessage> endpoint = new MethodKafkaListenerEndpoint<>();
        endpoint.setId("bug-02-classification-" + infrastructureFailure);
        endpoint.setGroupId(annotation.groupId());
        endpoint.setTopics(topic);
        endpoint.setBean(listener);
        endpoint.setMethod(method);
        endpoint.setMessageHandlerMethodFactory(handlers);
        var container = factory.createListenerContainer(endpoint);

        // When — запись проходит poll loop, messaging adapter и production-listener в consumer thread.
        Exception failure;
        try {
            container.start();
            failure = observedFailure.get(10, TimeUnit.SECONDS);
        } finally {
            container.stop();
        }

        // Then — запись не признана успешной; framework-обёртка не теряет категорию исходной ошибки.
        assertThat(successfulInvocations.get()).isZero();
        assertThat(failure).isInstanceOf(ListenerExecutionFailedException.class);
        List<Throwable> causes = causeChain(failure);
        if (infrastructureFailure) {
            assertThat(causes).contains(databaseFailure);
            assertThat(causes).anyMatch(KafkaMessageProcessingException.class::isInstance);
            assertThat(causes).noneMatch(InvalidProductPriceMessageException.class::isInstance);
        } else {
            assertThat(failure).hasRootCauseInstanceOf(InvalidProductPriceMessageException.class);
            assertThat(causes).noneMatch(KafkaMessageProcessingException.class::isInstance);
            assertThat(causes).anySatisfy(cause -> {
                assertThat(cause).isInstanceOf(InvalidProductPriceMessageException.class);
                assertThat(((InvalidProductPriceMessageException) cause).getField()).isEqualTo("price");
            });
            verifyNoInteractions(products, history);
        }
    }

    private static List<Throwable> causeChain(Throwable failure) {
        List<Throwable> causes = new ArrayList<>();
        for (Throwable current = failure; current != null && !causes.contains(current); current = current.getCause()) {
            causes.add(current);
        }
        return causes;
    }
}
