package com.example.orderflow.product.config;

import com.example.orderflow.product.model.Product;
import com.example.orderflow.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    record ProductSeed(String name, String description, BigDecimal price,
                       String category, String imageUrl, int stock) {}

    @Override
    public void run(ApplicationArguments args) throws Exception {
        long existing = productRepository.count();
        if (existing > 0) {
            log.info("Product seed skipped - collection already has {} product(s)", existing);
            return;
        }

        try (InputStream in = new ClassPathResource("seed/products.json").getInputStream()) {
            List<ProductSeed> seeds = objectMapper.readValue(in, new TypeReference<List<ProductSeed>>() {});
            List<Product> products = seeds.stream()
                    .map(s -> Product.builder()
                            .name(s.name())
                            .description(s.description())
                            .price(s.price())
                            .category(s.category())
                            .imageUrl(s.imageUrl())
                            .stock(s.stock())
                            .build())
                    .toList();
            productRepository.saveAll(products);
            log.info("Product seed: inserted {} products into empty collection", products.size());
        }
    }
}
