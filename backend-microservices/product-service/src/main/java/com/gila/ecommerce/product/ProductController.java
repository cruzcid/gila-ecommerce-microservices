package com.gila.ecommerce.product;

import com.gila.ecommerce.product.dto.ProductRequest;
import com.gila.ecommerce.product.dto.ProductResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category) {

        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(productService.search(search));
        }
        if (category != null && !category.isBlank()) {
            return ResponseEntity.ok(productService.findByCategory(category));
        }
        return ResponseEntity.ok(productService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    public ResponseEntity<List<ProductResponse>> importCsv(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.importFromCsv(file));
    }

    /**
     * Internal endpoint called by order-service to adjust stock.
     * Not exposed publicly — only reachable within the Docker network.
     * delta < 0  → decrement (reserve stock on order creation)
     * delta > 0  → increment (restore stock on order cancellation)
     */
    @PatchMapping("/{id}/stock")
    public ResponseEntity<Void> adjustStock(
            @PathVariable Long id,
            @RequestBody StockAdjustRequest request) {
        productService.adjustStock(id, request.getDelta());
        return ResponseEntity.noContent().build();
    }

    @Getter
    @Setter
    public static class StockAdjustRequest {
        @NotNull
        private Integer delta;
    }
}
