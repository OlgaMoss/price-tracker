package io.github.solar.parser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.solar.common.dto.ProductPriceMessage;
import io.github.solar.parser.client.SteamApiClient;
import io.github.solar.parser.config.SteamParserProperties;
import io.github.solar.parser.dto.SteamAppData;
import io.github.solar.parser.dto.SteamPriceOverview;
import io.github.solar.parser.exception.PricePublishingException;
import io.github.solar.parser.exception.SteamApiException;
import io.github.solar.parser.kafka.RawPriceProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Unit-тесты оркестратора парсинга {@link SteamPriceParsingService}.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("SteamPriceParsingService")
class SteamPriceParsingServiceTest {

	@Mock
	private SteamApiClient steamApiClient;

	@Mock
	private RawPriceProducer rawPriceProducer;

	@Mock
	private SteamParserProperties properties;

	@InjectMocks
	private SteamPriceParsingService parsingService;

	/**
	 * Проверяет распространение ошибки Steam API без подмены ошибкой неполной цены.
	 */
	@Test
	void shouldPropagateSteamApiException_WhenFetchingFails() {
		// Given — ошибка клиента для первой игры
		SteamApiException failure = new SteamApiException("Ошибка Steam API для appId=220");
		when(properties.getAppIds()).thenReturn(List.of(220, 570));
		when(steamApiClient.fetchAppDetails(220)).thenThrow(failure);

		// When — запуск обхода
		Throwable error = catchThrowable(parsingService::parseAndPublishAll);

		// Then — исходное исключение не подавляется, недостоверная цена не публикуется
		assertThat(error).isSameAs(failure);
		verify(rawPriceProducer, never()).publish(any());
	}

	/**
	 * Проверяет распространение ошибки Kafka и отсутствие запроса следующей игры.
	 */
	@Test
	void shouldPropagatePricePublishingExceptionAndStop_WhenPublishingFails() {
		// Given — окончательная ошибка producer для первой игры
		PricePublishingException failure = new PricePublishingException(
				"Не удалось опубликовать цену: steam-220", new IllegalStateException("Kafka unavailable"));
		when(properties.getAppIds()).thenReturn(List.of(220, 570));
		when(steamApiClient.fetchAppDetails(220)).thenReturn(Optional.of(paidApp(220, "First game", 499)));
		doThrow(failure).when(rawPriceProducer).publish(any(ProductPriceMessage.class));

		// When — запуск обхода
		Throwable error = catchThrowable(parsingService::parseAndPublishAll);

		// Then — исходная ошибка доходит до вызывающего кода, обход остановлен
		assertThat(error).isSameAs(failure);
		verify(rawPriceProducer).publish(any(ProductPriceMessage.class));
		verify(steamApiClient, never()).fetchAppDetails(570);
	}

	/**
	 * Проверяет отсутствие публикации неполной цены и продолжение обработки следующей игры.
	 */
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"missing | price_overview",
			"null | price_overview",
			"{} | final",
			"{\"currency\":\"USD\"} | final",
			"{\"final\":null} | final"
	})
	void shouldSkipIncompletePriceAndContinue_WhenPaidResponseIncomplete(String overview, String reason) throws Exception {
		// Given — синтетический ответ; ID в data намеренно отличается от запрошенного 220
		when(properties.getAppIds()).thenReturn(List.of(220, 570));
		when(properties.getRequestDelayMs()).thenReturn(0L);
		when(steamApiClient.fetchAppDetails(220)).thenReturn(Optional.of(incompletePaidApp(overview)));
		when(steamApiClient.fetchAppDetails(570)).thenReturn(Optional.of(freeApp(570, "Next game")));

		// When — обход неполного и следующего корректного наблюдения
		parsingService.parseAndPublishAll();

		// Then — опубликована только следующая игра
		ArgumentCaptor<ProductPriceMessage> message = ArgumentCaptor.forClass(ProductPriceMessage.class);
		verify(rawPriceProducer).publish(message.capture());
		assertThat(message.getValue().getExternalId()).isEqualTo("steam-570");
		assertThat(message.getValue().getPrice()).isEqualByComparingTo("0.00");
		verify(steamApiClient).fetchAppDetails(570);
	}

	/**
	 * Проверяет ошибку с запрошенным appId и конкретным отсутствующим полем цены.
	 */
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"missing | price_overview",
			"null | price_overview",
			"{} | final",
			"{\"currency\":\"USD\"} | final",
			"{\"final\":null} | final"
	})
	void shouldLogRequestedAppIdAndReason_WhenPaidResponseIncomplete(
			String overview, String reason, CapturedOutput output) throws Exception {
		// Given — синтетический ответ с data.steam_appid=999 при запросе appId=220
		when(properties.getAppIds()).thenReturn(List.of(220));
		when(steamApiClient.fetchAppDetails(220)).thenReturn(Optional.of(incompletePaidApp(overview)));

		// When — обработка неполной цены
		parsingService.parseAndPublishAll();

		// Then — диагностическая строка содержит уровень ошибки, запрошенный ID и причину
		assertThat(output.getAll().lines().anyMatch(line ->
				line.contains("ERROR") && line.contains("appId=220") && line.contains(reason)))
				.as("Ошибка должна содержать запрошенный appId=220 и причину %s", reason).isTrue();
	}

	private static SteamAppData incompletePaidApp(String overview) throws Exception {
		String priceProperty = "missing".equals(overview) ? "" : ",\"price_overview\":" + overview;
		return new ObjectMapper().readValue(
				"{\"steam_appid\":999,\"is_free\":false" + priceProperty + "}", SteamAppData.class);
	}

	/**
	 * Проверяет публикацию цен для всех успешно полученных приложений.
	 */
	@Test
	void shouldPublishAllPrices_WhenSteamApiReturnsData() {
		// Given — подготовка данных и моков
		when(properties.getAppIds()).thenReturn(List.of(220, 570));
		when(properties.getCountryCode()).thenReturn("us");
		when(properties.getRequestDelayMs()).thenReturn(0L);
		when(steamApiClient.fetchAppDetails(220)).thenReturn(Optional.of(paidApp(220, "Half-Life 2", 499)));
		when(steamApiClient.fetchAppDetails(570)).thenReturn(Optional.of(freeApp(570, "Dota 2")));

		// When — выполнение тестируемого действия
		parsingService.parseAndPublishAll();

		// Then — проверка результатов
		verify(rawPriceProducer, times(2)).publish(any(ProductPriceMessage.class));
	}

	/**
	 * Проверяет пропуск приложения при отсутствии данных от Steam API.
	 */
	@Test
	void shouldSkipApp_WhenSteamApiReturnsEmpty() {
		// Given — подготовка данных и моков
		when(properties.getAppIds()).thenReturn(List.of(220));
		when(properties.getCountryCode()).thenReturn("us");
		when(steamApiClient.fetchAppDetails(220)).thenReturn(Optional.empty());

		// When — выполнение тестируемого действия
		parsingService.parseAndPublishAll();

		// Then — проверка результатов
		verify(rawPriceProducer, never()).publish(any());
	}

	private static SteamAppData paidApp(int appId, String name, int finalCents) {
		SteamPriceOverview overview = new SteamPriceOverview();
		overview.setFinalPrice(finalCents);

		SteamAppData appData = new SteamAppData();
		appData.setSteamAppId(appId);
		appData.setName(name);
		appData.setFree(false);
		appData.setPriceOverview(overview);
		return appData;
	}

	private static SteamAppData freeApp(int appId, String name) {
		SteamAppData appData = new SteamAppData();
		appData.setSteamAppId(appId);
		appData.setName(name);
		appData.setFree(true);
		return appData;
	}
}
