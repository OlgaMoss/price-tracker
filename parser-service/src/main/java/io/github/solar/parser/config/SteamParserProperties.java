package io.github.solar.parser.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Конфигурация парсера Steam: список приложений, регион, расписание и параметры Kafka.
 */
@Data
@ConfigurationProperties(prefix = "steam.parser")
public class SteamParserProperties {

	private List<Integer> appIds = new ArrayList<>();
	private String countryCode = "ru";
	private String scheduleCron = "0 */15 * * * *";
	private String kafkaTopic = "raw-prices";
	private long requestDelayMs = 1_500L;
	private boolean runOnce = false;
	private String apiBaseUrl = "https://store.steampowered.com/api/appdetails";
}
