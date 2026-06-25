package com.gila.ecommerce.product;

import com.gila.ecommerce.product.dto.ProductRequest;
import com.gila.ecommerce.product.dto.ProductResponse;
import com.gila.ecommerce.product.exception.DuplicateSkuException;
import com.gila.ecommerce.product.exception.ProductNotFoundException;
import com.gila.ecommerce.product.importlog.ImportLogService;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ImportLogService importLogService;

    public List<ProductResponse> findAll() {
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    public ProductResponse findById(Long id) {
        return ProductResponse.from(getProductOrThrow(id));
    }

    public List<ProductResponse> search(String query) {
        return productRepository.search(query).stream()
                .map(ProductResponse::from)
                .toList();
    }

    public List<ProductResponse> findByCategory(String category) {
        return productRepository.findByCategory(category).stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }
        return ProductResponse.from(productRepository.save(toEntity(request)));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getProductOrThrow(id);
        if (!product.getSku().equals(request.getSku())
                && productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }
        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setWeightKg(request.getWeightKg());
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public void delete(Long id) {
        getProductOrThrow(id);
        productRepository.deleteById(id);
    }

    /**
     * Called by order-service to adjust stock atomically.
     * Use a negative delta to decrement (e.g., -2), positive to restore (+2).
     */
    @Transactional
    public void adjustStock(Long id, int delta) {
        Product product = getProductOrThrow(id);
        int newStock = product.getStock() + delta;
        if (newStock < 0) {
            throw new IllegalArgumentException(
                    "Insufficient stock for product: " + product.getName()
                    + " (available: " + product.getStock() + ")");
        }
        product.setStock(newStock);
        productRepository.save(product);
    }

    public List<ProductResponse> importFromCsv(MultipartFile file) {
        List<ProductResponse> imported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String[] headers = reader.readNext();
            if (headers == null) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            String[] row;
            int lineNumber = 1;
            while ((row = reader.readNext()) != null) {
                lineNumber++;
                try {
                    String sku = getValue(row, 1);
                    if (productRepository.existsBySku(sku)) {
                        skipped.add("Line " + lineNumber + ": SKU '" + sku + "' already exists");
                        continue;
                    }
                    Product product = Product.builder()
                            .name(getValue(row, 0))
                            .sku(sku)
                            .description(getValue(row, 2))
                            .category(getValue(row, 3))
                            .price(parseDecimal(getValue(row, 4)))
                            .stock(Integer.parseInt(getValue(row, 5).trim()))
                            .weightKg(row.length > 6 && !getValue(row, 6).isBlank()
                                    ? new BigDecimal(getValue(row, 6).trim()) : null)
                            .build();
                    imported.add(ProductResponse.from(productRepository.save(product)));
                } catch (Exception e) {
                    errors.add("Line " + lineNumber + ": " + e.getMessage());
                }
            }
        } catch (IOException | CsvValidationException e) {
            throw new IllegalArgumentException("Failed to parse CSV: " + e.getMessage());
        }

        importLogService.log(file.getOriginalFilename(), imported.size(), skipped, errors);
        return imported;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Product getProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private Product toEntity(ProductRequest req) {
        return Product.builder()
                .name(req.getName())
                .sku(req.getSku())
                .description(req.getDescription())
                .category(req.getCategory())
                .price(req.getPrice())
                .stock(req.getStock())
                .weightKg(req.getWeightKg())
                .build();
    }

    private String getValue(String[] row, int index) {
        return (index < row.length) ? row[index].trim() : "";
    }

    private BigDecimal parseDecimal(String raw) {
        return new BigDecimal(raw.trim().replace("$", "").replace(",", ""));
    }
}
