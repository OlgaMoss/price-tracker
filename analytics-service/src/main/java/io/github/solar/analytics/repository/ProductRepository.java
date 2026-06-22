package io.github.solar.analytics.repository;

import io.github.solar.analytics.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Репозиторий для доступа к товарам Steam в PostgreSQL.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

	Optional<Product> findByExternalId(String externalId);
}
