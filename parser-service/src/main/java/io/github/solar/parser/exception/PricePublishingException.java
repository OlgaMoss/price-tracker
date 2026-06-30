package io.github.solar.parser.exception;

/**
 * Исключение при ошибке публикации цены в Kafka.
 */
public class PricePublishingException extends RuntimeException {

	public PricePublishingException(String message, Throwable cause) {
		super(message, cause);
	}
}
