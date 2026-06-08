package io.github.solar.analytics.service;

import io.github.solar.analytics.dto.ProductPriceMessage;
import io.github.solar.analytics.entity.PriceHistory;
import io.github.solar.analytics.entity.Product;
import io.github.solar.analytics.repository.PriceHistoryRepository;
import io.github.solar.analytics.repository.ProductRepository;
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

@ExtendWith(MockitoExtension.class)
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

    @Test
    void analyzePrice_whenProductNotFound_savesNewProductAndPriceHistory() {
        ProductPriceMessage message = message(new BigDecimal("9.99"));

        when(productRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(1L);
            return product;
        });

        priceAnalysisService.analyzePrice(message);

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

    @Test
    void analyzePrice_whenPriceDropped_updatesProductAndSavesHistory() {
        Product existingProduct = existingProduct(new BigDecimal("19.99"));
        ProductPriceMessage message = message(new BigDecimal("9.99"));

        when(productRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        priceAnalysisService.analyzePrice(message);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());

        Product updatedProduct = productCaptor.getValue();
        assertThat(updatedProduct.getCurrentPrice()).isEqualByComparingTo("9.99");
        assertThat(updatedProduct.getUpdatedAt()).isNotNull();

        ArgumentCaptor<PriceHistory> historyCaptor = ArgumentCaptor.forClass(PriceHistory.class);
        verify(priceHistoryRepository).save(historyCaptor.capture());

        PriceHistory savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getProduct()).isSameAs(existingProduct);
        assertThat(savedHistory.getPrice()).isEqualByComparingTo("9.99");
    }

    @Test
    void analyzePrice_whenPriceUnchanged_doesNotUpdateProductOrSaveHistory() {
        Product existingProduct = existingProduct(new BigDecimal("9.99"));
        ProductPriceMessage message = message(new BigDecimal("9.99"));

        when(productRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(existingProduct));

        priceAnalysisService.analyzePrice(message);

        verify(productRepository, never()).save(any());
        verify(priceHistoryRepository, never()).save(any());
    }

    @Test
    void analyzePrice_whenPriceIncreased_updatesProductAndSavesHistory() {
        Product existingProduct = existingProduct(new BigDecimal("9.99"));
        ProductPriceMessage message = message(new BigDecimal("14.99"));

        when(productRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        priceAnalysisService.analyzePrice(message);

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
