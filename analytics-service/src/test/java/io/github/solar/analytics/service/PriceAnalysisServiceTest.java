package io.github.solar.analytics.service;

import io.github.solar.analytics.entity.PriceHistory;
import io.github.solar.analytics.entity.Product;
import io.github.solar.analytics.repository.PriceHistoryRepository;
import io.github.solar.analytics.repository.ProductRepository;
import io.github.solar.common.dto.ProductPriceMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты сервиса аналитики цен {@link PriceAnalysisService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PriceAnalysisService")
class PriceAnalysisServiceTest {

	private static final String EXTERNAL_ID = "steam-220";
	private static final String TITLE = "Half-Life 2";
	private static final String URL = "https://store.steampowered.com/app/220";

	@Mock
	private ProductRepository productRepository;

	@Mock
	private PriceHistoryRepository priceHistoryRepository;

	@InjectMocks
	private PriceAnalysisService priceAnalysisService;

	/**
	 * Проверяет создание нового товара и первой записи истории цены.
	 */
	@Test
	void shouldSaveProductAndHistory_WhenProductNotFound() {
		// Given — подготовка данных и моков
		ProductPriceMessage message = message(new BigDecimal("9.99"));
		when(productRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());
		when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
			Product product = invocation.getArgument(0);
			product.setId(1L);
			return product;
		});

		// When — выполнение тестируемого действия
		priceAnalysisService.analyzePrice(message);

		// Then — проверка результатов
		ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
		verify(productRepository).save(productCaptor.capture());

		Product savedProduct = productCaptor.getValue();
		assertThat(savedProduct.getExternalId()).isEqualTo(EXTERNAL_ID);
		assertThat(savedProduct.getTitle()).isEqualTo(TITLE);
		assertThat(savedProduct.getCurrentPrice()).isEqualByComparingTo("9.99");
		assertThat(savedProduct.getUrl()).isEqualTo(URL);
		assertThat(savedProduct.getUpdatedAt()).isNotNull();

		ArgumentCaptor<PriceHistory> historyCaptor = ArgumentCaptor.forClass(PriceHistory.class);
		verify(priceHistoryRepository).save(historyCaptor.capture());

		PriceHistory savedHistory = historyCaptor.getValue();
		assertThat(savedHistory.getProduct()).isSameAs(savedProduct);
		assertThat(savedHistory.getPrice()).isEqualByComparingTo("9.99");
		assertThat(savedHistory.getCheckedAt()).isNotNull();
	}

	/**
	 * Проверяет обновление цены и запись в историю при снижении стоимости.
	 */
	@Test
	void shouldUpdateProductAndSaveHistory_WhenPriceDropped() {
		// Given — подготовка данных и моков
		Product existingProduct = existingProduct(new BigDecimal("19.99"));
		ProductPriceMessage message = message(new BigDecimal("9.99"));
		when(productRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(existingProduct));
		when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// When — выполнение тестируемого действия
		priceAnalysisService.analyzePrice(message);

		// Then — проверка результатов
		ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
		verify(productRepository).save(productCaptor.capture());
		assertThat(productCaptor.getValue().getCurrentPrice()).isEqualByComparingTo("9.99");

		ArgumentCaptor<PriceHistory> historyCaptor = ArgumentCaptor.forClass(PriceHistory.class);
		verify(priceHistoryRepository).save(historyCaptor.capture());
		assertThat(historyCaptor.getValue().getPrice()).isEqualByComparingTo("9.99");
	}

	/**
	 * Проверяет отсутствие записей в БД при неизменной цене.
	 */
	@Test
	void shouldSkipPersistence_WhenPriceUnchanged() {
		// Given — подготовка данных и моков
		Product existingProduct = existingProduct(new BigDecimal("9.99"));
		ProductPriceMessage message = message(new BigDecimal("9.99"));
		when(productRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(existingProduct));

		// When — выполнение тестируемого действия
		priceAnalysisService.analyzePrice(message);

		// Then — проверка результатов
		verify(productRepository, never()).save(any());
		verify(priceHistoryRepository, never()).save(any());
	}

	/**
	 * Проверяет обновление цены и историю при росте стоимости.
	 */
	@Test
	void shouldUpdateProductAndSaveHistory_WhenPriceIncreased() {
		// Given — подготовка данных и моков
		Product existingProduct = existingProduct(new BigDecimal("9.99"));
		ProductPriceMessage message = message(new BigDecimal("14.99"));
		when(productRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(existingProduct));
		when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// When — выполнение тестируемого действия
		priceAnalysisService.analyzePrice(message);

		// Then — проверка результатов
		ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
		verify(productRepository).save(productCaptor.capture());
		assertThat(productCaptor.getValue().getCurrentPrice()).isEqualByComparingTo("14.99");

		ArgumentCaptor<PriceHistory> historyCaptor = ArgumentCaptor.forClass(PriceHistory.class);
		verify(priceHistoryRepository).save(historyCaptor.capture());
		assertThat(historyCaptor.getValue().getPrice()).isEqualByComparingTo("14.99");
	}

	private static ProductPriceMessage message(BigDecimal price) {
		return ProductPriceMessage.builder()
				.externalId(EXTERNAL_ID)
				.title(TITLE)
				.price(price)
				.url(URL)
				.build();
	}

	private static Product existingProduct(BigDecimal currentPrice) {
		return Product.builder()
				.id(1L)
				.externalId(EXTERNAL_ID)
				.title(TITLE)
				.currentPrice(currentPrice)
				.url(URL)
				.updatedAt(LocalDateTime.now().minusDays(1))
				.build();
	}
}
