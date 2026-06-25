package com.gila.ecommerce.order.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * HTTP client that talks to product-service.
 * Used by OrderService to fetch product details and adjust stock.
 */
@Component
public class ProductClient {

    private final RestClient restClient;

    public ProductClient(@Value("${product.service.url}") String productServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(productServiceUrl)
                .build();
    }

    /**
     * Fetch a product by ID. Returns null when the product does not exist.
     */
    public ProductDTO getProduct(Long id) {
        try {
            return restClient.get()
                    .uri("/api/products/{id}", id)
                    .retrieve()
                    .body(ProductDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    /**
     * Adjust the stock of a product by {@code delta} units.
     * Pass a negative value to decrement, positive to restore.
     *
     * @throws IllegalArgumentException if the product-service reports insufficient stock
     */
    public void adjustStock(Long productId, int delta) {
        restClient.patch()
                .uri("/api/products/{id}/stock", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new StockAdjustRequest(delta))
                .retrieve()
                .toBodilessEntity();
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record ProductDTO(
            Long id,
            String name,
            String sku,
            String category,
            BigDecimal price,
            Integer stock) {}

    @Getter
    @Setter
    public static class StockAdjustRequest {
        private final Integer delta;

        public StockAdjustRequest(Integer delta) {
            this.delta = delta;
        }
    }
}
