package io.github.solar.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа микросервиса аналитики цен.
 * Слушает топик Kafka {@code raw-prices}, сохраняет историю в PostgreSQL
 * и определяет снижение цены для последующей публикации в {@code price-drops}.
 */
@SpringBootApplication
public class AnalyticsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AnalyticsServiceApplication.class, args);
	}

}
