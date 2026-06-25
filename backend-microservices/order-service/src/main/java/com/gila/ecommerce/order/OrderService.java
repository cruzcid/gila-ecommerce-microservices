package com.gila.ecommerce.order;

import com.gila.ecommerce.order.client.ProductClient;
import com.gila.ecommerce.order.client.ProductClient.ProductDTO;
import com.gila.ecommerce.order.exception.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public List<OrderResponse> findAll() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(OrderResponse::from)
                .toList();
    }

    public OrderResponse findById(Long id) {
        return OrderResponse.from(getOrderOrThrow(id));
    }

    @Transactional
    public OrderResponse create(OrderRequest request) {
        Order order = Order.builder()
                .customerName(request.getCustomerName())
                .customerEmail(request.getCustomerEmail())
                .status(OrderStatus.PENDING)
                .build();

        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (OrderRequest.OrderItemRequest itemRequest : request.getItems()) {
            Long productId = itemRequest.getProductId();

            // Fetch product from product-service
            ProductDTO product = productClient.getProduct(productId);
            if (product == null) {
                throw new IllegalArgumentException("Product not found with id: " + productId);
            }
            if (product.stock() < itemRequest.getQuantity()) {
                throw new IllegalArgumentException(
                        "Insufficient stock for product: " + product.name()
                        + " (available: " + product.stock() + ")");
            }

            BigDecimal subtotal = product.price()
                    .multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            // Build snapshot — no cross-service FK
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .productId(product.id())
                    .productName(product.name())
                    .sku(product.sku())
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.price())
                    .subtotal(subtotal)
                    .build();

            items.add(item);
            total = total.add(subtotal);

            // Deduct stock in product-service
            productClient.adjustStock(productId, -itemRequest.getQuantity());
        }

        order.setItems(items);
        order.setTotalAmount(total);

        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse markAsPaid(Long id) {
        Order order = getOrderOrThrow(id);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING orders can be marked as PAID");
        }
        order.setStatus(OrderStatus.PAID);
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse cancel(Long id) {
        Order order = getOrderOrThrow(id);
        if (order.getStatus() == OrderStatus.PAID) {
            throw new IllegalArgumentException("PAID orders cannot be cancelled");
        }
        order.setStatus(OrderStatus.CANCELLED);

        // Restore stock in product-service for every line item
        for (OrderItem item : order.getItems()) {
            productClient.adjustStock(item.getProductId(), item.getQuantity());
        }

        return OrderResponse.from(orderRepository.save(order));
    }

    private Order getOrderOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }
}
