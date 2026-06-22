package io.github.solar.analytics.repository;

import io.github.solar.analytics.entity.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Репозиторий для хранения истории изменения цен товаров.
 */
@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

}
