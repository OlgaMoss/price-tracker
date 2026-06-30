package io.github.solar.parser.service;

import io.github.solar.common.dto.ProductPriceMessage;
import io.github.solar.parser.client.SteamApiClient;
import io.github.solar.parser.config.SteamParserProperties;
import io.github.solar.parser.dto.SteamAppData;
import io.github.solar.parser.dto.SteamPriceOverview;
import io.github.solar.parser.kafka.RawPriceProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты оркестратора парсинга {@link SteamPriceParsingService}.
 */
@ExtendWith(MockitoExtension.class)
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
