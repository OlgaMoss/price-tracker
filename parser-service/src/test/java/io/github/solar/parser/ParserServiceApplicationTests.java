package io.github.solar.parser;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke-тест загрузки контекста Spring Boot для parser-service.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "raw-prices")
@TestPropertySource(properties = {
		"steam.parser.run-once=false",
		"steam.parser.app-ids=220",
		"steam.parser.request-delay-ms=0"
})
class ParserServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}
