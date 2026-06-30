package io.github.solar.parser.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.solar.parser.config.SteamParserProperties;
import io.github.solar.parser.dto.SteamAppData;
import io.github.solar.parser.exception.SteamApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * HTTP-клиент для запросов к Steam Store API ({@code /api/appdetails}).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SteamApiClient {

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final SteamParserProperties properties;

	/**
	 * Загружает детали приложения Steam по идентификатору.
	 *
	 * @param appId идентификатор приложения в Steam
	 * @return данные приложения, если API вернул успешный ответ
	 */
	public Optional<SteamAppData> fetchAppDetails(int appId) {
		String url = "%s?appids=%d&cc=%s&l=russian".formatted(
				properties.getApiBaseUrl(), appId, properties.getCountryCode());

		try {
			String responseBody = restClient.get()
					.uri(url)
					.retrieve()
					.body(String.class);

			return parseResponse(appId, responseBody);
		} catch (RestClientException e) {
			throw new SteamApiException("Ошибка HTTP-запроса к Steam API для appId=" + appId, e);
		}
	}

	private Optional<SteamAppData> parseResponse(int appId, String responseBody) {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode appNode = root.get(String.valueOf(appId));

			if (appNode == null || !appNode.path("success").asBoolean(false)) {
				log.warn("Steam API не вернул данные: appId={}", appId);
				return Optional.empty();
			}

			SteamAppData data = objectMapper.treeToValue(appNode.get("data"), SteamAppData.class);
			return Optional.ofNullable(data);
		} catch (Exception e) {
			throw new SteamApiException("Ошибка разбора ответа Steam API для appId=" + appId, e);
		}
	}
}
