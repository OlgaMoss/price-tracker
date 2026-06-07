package io.github.solar.analytics.repository;

import io.github.solar.analytics.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<io.github.solar.analytics.entity.Product> findByExternalId(String externalId);
}
