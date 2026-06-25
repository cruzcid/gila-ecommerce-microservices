package com.gila.ecommerce.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Set;

/**
 * Simple WebFlux reverse-proxy.
 * Routes /api/** requests to the correct downstream service based on path prefix.
 */
@RestController
public class ProxyController {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            HttpHeaders.HOST,
            HttpHeaders.TRANSFER_ENCODING,
            HttpHeaders.CONNECTION,
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailers",
            "upgrade"
    );

    private final WebClient webClient;
    private final String productServiceUrl;
    private final String orderServiceUrl;
    private final String paymentServiceUrl;

    public ProxyController(
            WebClient.Builder builder,
            @Value("${product.service.url}") String productServiceUrl,
            @Value("${order.service.url}") String orderServiceUrl,
            @Value("${payment.service.url}") String paymentServiceUrl) {
        this.webClient = builder.build();
        this.productServiceUrl = productServiceUrl;
        this.orderServiceUrl   = orderServiceUrl;
        this.paymentServiceUrl = paymentServiceUrl;
    }

    @RequestMapping("/api/**")
    public Mono<ResponseEntity<byte[]>> proxy(ServerHttpRequest request) {
        String path  = request.getPath().value();
        String query = request.getURI().getRawQuery();
        String target = resolveTarget(path) + path + (query != null ? "?" + query : "");

        return webClient
                .method(request.getMethod())
                .uri(URI.create(target))
                .headers(h -> request.getHeaders().forEach((name, values) -> {
                    if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                        h.addAll(name, values);
                    }
                }))
                .body(request.getBody(), org.springframework.core.io.buffer.DataBuffer.class)
                .exchangeToMono(resp -> {
                    HttpStatus status = HttpStatus.resolve(resp.statusCode().value());
                    HttpHeaders responseHeaders = new HttpHeaders();
                    resp.headers().asHttpHeaders().forEach((name, values) -> {
                        if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                            responseHeaders.addAll(name, values);
                        }
                    });
                    return resp.bodyToMono(byte[].class)
                            .defaultIfEmpty(new byte[0])
                            .map(body -> ResponseEntity
                                    .status(status != null ? status : HttpStatus.BAD_GATEWAY)
                                    .headers(responseHeaders)
                                    .body(body));
                });
    }

    private String resolveTarget(String path) {
        if (path.startsWith("/api/products") || path.startsWith("/api/import-logs")) {
            return productServiceUrl;
        }
        if (path.startsWith("/api/orders")) {
            return orderServiceUrl;
        }
        if (path.startsWith("/api/payments")) {
            return paymentServiceUrl;
        }
        return productServiceUrl;
    }
}
