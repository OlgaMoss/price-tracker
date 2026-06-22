package io.github.solar.analytics.exception;

/**
 * Исключение при ошибке обработки сообщения из Kafka в сервисе аналитики.
 */
public class KafkaMessageProcessingException extends RuntimeException {

	public KafkaMessageProcessingException(String message, Throwable cause) {
		super(message, cause);
	}
}
