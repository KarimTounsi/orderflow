package com.example.orderflow.product.service;

import com.example.orderflow.product.dto.ProductRequest;
import com.example.orderflow.product.dto.ProductResponse;
import com.example.orderflow.product.exception.ProductNotFoundException;
import com.example.orderflow.product.model.Product;
import com.example.orderflow.product.rag.ProductIndexService;
import com.example.orderflow.product.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductIndexService productIndexService;

    public ProductService(ProductRepository productRepository, ProductIndexService productIndexService) {
        this.productRepository = productRepository;
        this.productIndexService = productIndexService;
    }

    private void safeIndex(Product product) {
        try {
            productIndexService.index(product);
        } catch (Exception e) {
            log.warn("Vector indexing failed for product {} (search index can be rebuilt via reindex): {}",
                    product.getId(), e.getMessage());
        }
    }

    private void safeRemoveFromIndex(String productId) {
        try {
            productIndexService.removeFromIndex(productId);
        } catch (Exception e) {
            log.warn("Vector index removal failed for product {}: {}", productId, e.getMessage());
        }
    }

    @Cacheable(value = "products", key = "#id")
    public ProductResponse getById(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return ProductResponse.fromProduct(product);
    }

    public Page<ProductResponse> getAll(String category, String search, Pageable pageable) {
        boolean hasCategory = category != null && !category.isBlank();
        boolean hasSearch = search != null && !search.isBlank();

        Page<Product> page;
        if (hasCategory && hasSearch) {
            page = productRepository.findByCategoryAndNameContainingIgnoreCase(category, search, pageable);
        } else if (hasCategory) {
            page = productRepository.findByCategory(category, pageable);
        } else if (hasSearch) {
            page = productRepository.findByNameContainingIgnoreCase(search, pageable);
        } else {
            page = productRepository.findAll(pageable);
        }
        return page.map(ProductResponse::fromProduct);
    }

    public Page<ProductResponse> getByCategory(String category, Pageable pageable) {
        return productRepository.findByCategory(category, pageable)
                .map(ProductResponse::fromProduct);
    }

    public Page<ProductResponse> search(String name, Pageable pageable) {
        return productRepository.findByNameContainingIgnoreCase(name, pageable)
                .map(ProductResponse::fromProduct);
    }

    public ProductResponse create(ProductRequest request) {
        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .category(request.category())
                .imageUrl(request.imageUrl())
                .stock(request.stock())
                .attributes(request.attributes())
                .build();

        Product saved = productRepository.save(product);
        safeIndex(saved);
        return ProductResponse.fromProduct(saved);
    }

    @CachePut(value = "products", key = "#id")
    public ProductResponse update(String id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCategory(request.category());
        product.setImageUrl(request.imageUrl());
        product.setStock(request.stock());
        product.setAttributes(request.attributes());

        Product updated = productRepository.save(product);
        safeIndex(updated);
        return ProductResponse.fromProduct(updated);
    }

    @Caching(evict = {
            @CacheEvict(value = "products", key = "#id")
    })
    public void delete(String id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
        // Usuniety produkt nie moze wracac w wynikach wyszukiwania - czyscimy indeks wektorowy.
        safeRemoveFromIndex(id);
    }

    @CachePut(value = "products", key = "#id")
    public ProductResponse updateStock(String id, int newStock) {
        if (newStock < 0) {
            throw new IllegalArgumentException("Stock cannot be negative, got: " + newStock);
        }
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.setStock(newStock);
        Product updated = productRepository.save(product);
        return ProductResponse.fromProduct(updated);
    }
}
