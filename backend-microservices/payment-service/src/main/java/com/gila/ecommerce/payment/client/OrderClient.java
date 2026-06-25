package com.gila.ecommerce.payment.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * HTTP client that talks to order-service.
 * Used by PaymentController to mark an order as PAID after simulated payment.
 */
@Component
public class OrderClient {

    private final RestClient restClient;

    public OrderClient(@Value("${order.service.url}") String orderServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(orderServiceUrl)
                .build();
    }

    public OrderDTO markAsPaid(Long orderId) {
        return restClient.patch()
                .uri("/api/orders/{id}/pay", orderId)
                .retrieve()
                .body(OrderDTO.class);
    }

    // ── DTO mirror of order-service's OrderResponse ───────────────────────

    public record OrderDTO(
            Long id,
            String customerName,
            String customerEmail,
            String status,
            BigDecimal totalAmount,
            List<OrderItemDTO> items,
            LocalDateTime createdAt) {}

    public record OrderItemDTO(
            Long productId,
            String productName,
            String sku,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal) {}
}
