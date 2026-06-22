package io.github.solar.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke-тест загрузки контекста Spring Boot для analytics-service.
 */
@SpringBootTest
@ActiveProfiles("test")
class AnalyticsServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}
