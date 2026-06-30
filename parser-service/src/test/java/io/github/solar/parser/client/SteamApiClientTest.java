package io.github.solar.parser.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.solar.parser.config.SteamParserProperties;
import io.github.solar.parser.dto.SteamAppData;
import io.github.solar.parser.exception.SteamApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-тесты HTTP-клиента Steam Store API {@link SteamApiClient}.
 */
@DisplayName("SteamApiClient")
class SteamApiClientTest {

	@RegisterExtension
	static WireMockExtension wireMock = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	private SteamApiClient steamApiClient;

	@BeforeEach
	void setUp() {
		SteamParserProperties properties = new SteamParserProperties();
		properties.setApiBaseUrl("http://localhost:" + wireMock.getPort() + "/api/appdetails");
		properties.setCountryCode("us");

		steamApiClient = new SteamApiClient(
				RestClient.builder().build(),
				new ObjectMapper(),
				properties
		);
	}

	/**
	 * Проверяет успешный разбор ответа Steam API с ценой товара.
	 */
	@Test
	void shouldReturnAppData_WhenSteamApiRespondsSuccessfully() {
		// Given — подготовка данных и моков
		wireMock.stubFor(get(urlPathEqualTo("/api/appdetails"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""
								{
								  "220": {
								    "success": true,
								    "data": {
								      "name": "Half-Life 2",
								      "steam_appid": 220,
								      "is_free": false,
								      "price_overview": {
								        "currency": "USD",
								        "initial": 999,
								        "final": 499,
								        "discount_percent": 50
								      }
								    }
								  }
								}
								""")));

		// When — выполнение тестируемого действия
		Optional<SteamAppData> result = steamApiClient.fetchAppDetails(220);

		// Then — проверка результатов
		assertThat(result).isPresent();
		assertThat(result.get().getName()).isEqualTo("Half-Life 2");
		assertThat(result.get().getSteamAppId()).isEqualTo(220);
		assertThat(result.get().getPriceOverview().getFinalPrice()).isEqualTo(499);
	}

	/**
	 * Проверяет возврат пустого результата при success=false от Steam API.
	 */
	@Test
	void shouldReturnEmpty_WhenSteamApiReturnsUnsuccessfulResponse() {
		// Given — подготовка данных и моков
		wireMock.stubFor(get(urlPathEqualTo("/api/appdetails"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""
								{
								  "999": {
								    "success": false
								  }
								}
								""")));

		// When — выполнение тестируемого действия
		Optional<SteamAppData> result = steamApiClient.fetchAppDetails(999);

		// Then — проверка результатов
		assertThat(result).isEmpty();
	}

	/**
	 * Проверяет выброс исключения при некорректном JSON-ответе.
	 */
	@Test
	void shouldThrowSteamApiException_WhenResponseIsInvalidJson() {
		// Given — подготовка данных и моков
		wireMock.stubFor(get(urlPathEqualTo("/api/appdetails"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("not-json")));

		// When / Then — выполнение и проверка исключения
		assertThatThrownBy(() -> steamApiClient.fetchAppDetails(220))
				.isInstanceOf(SteamApiException.class)
				.hasMessageContaining("appId=220");
	}
}
