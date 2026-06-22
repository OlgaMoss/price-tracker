package io.github.solar.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA-сущность товара Steam с актуальной ценой.
 * Индекс по {@code external_id} ускоряет поиск при каждом входящем сообщении из Kafka.
 */
@Entity
@Table(name = "products", indexes = {
		@Index(name = "idx_products_external_id", columnList = "external_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private String externalId;

	@Column(name = "title", nullable = false)
	private String title;

	@Column(name = "current_price", nullable = false, precision = 10, scale = 2)
	private BigDecimal currentPrice;

	@Column(name = "url", length = 512)
	private String url;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
}
