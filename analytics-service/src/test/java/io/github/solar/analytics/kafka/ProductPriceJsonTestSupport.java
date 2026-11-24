package io.github.solar.analytics.kafka;

import io.github.solar.common.dto.ProductPriceMessage;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Десериализует тестовый JSON штатным Kafka JsonDeserializer с настройками production YAML.
 */
public final class ProductPriceJsonTestSupport {

    private ProductPriceJsonTestSupport() {
    }

    /**
     * Читает JSON без type headers так же, как старое сообщение общего Kafka-контракта.
     *
     * @param json исходное представление сообщения
     * @return DTO после фактического Kafka JsonDeserializer
     */
    public static ProductPriceMessage deserialize(String json) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        Properties properties = yaml.getObject();
        assertThat(properties).isNotNull();
        String prefix = "spring.kafka.consumer.properties.";
        assertThat(properties.getProperty(prefix + "spring.deserializer.value.delegate.class"))
                .isEqualTo(JsonDeserializer.class.getName());
        Map<String, Object> config = new HashMap<>();
        properties.forEach((key, value) -> {
            String name = key.toString();
            if (name.startsWith(prefix)) {
                config.put(name.substring(prefix.length()), value);
            }
        });
        try (JsonDeserializer<ProductPriceMessage> deserializer = new JsonDeserializer<>()) {
            deserializer.configure(config, false);
            return deserializer.deserialize("raw-prices", new RecordHeaders(), json.getBytes(StandardCharsets.UTF_8));
        }
    }
}
