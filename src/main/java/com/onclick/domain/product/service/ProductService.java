package com.onclick.domain.product.service;

import java.util.List;

import com.onclick.domain.product.dto.ProductCreateRequest;
import com.onclick.domain.product.dto.ProductResponse;
import com.onclick.domain.product.dto.ProductStatusUpdateRequest;
import com.onclick.domain.product.dto.ProductUpdateRequest;
import com.onclick.domain.product.entity.Product;
import com.onclick.domain.product.repository.ProductRepository;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final StoreAccessValidator storeAccessValidator;

    public ProductService(
            ProductRepository productRepository,
            StoreAccessValidator storeAccessValidator
    ) {
        this.productRepository = productRepository;
        this.storeAccessValidator = storeAccessValidator;
    }

    @Transactional
    public ProductResponse create(Jwt jwt, Long storeId, ProductCreateRequest request) {
        storeAccessValidator.validate(jwt, storeId);
        Product product = Product.create(storeId, normalizeName(request.name()), request.price());
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll(Jwt jwt, Long storeId) {
        storeAccessValidator.validate(jwt, storeId);
        return productRepository.findAllByStoreIdOrderByCreatedAtDesc(storeId).stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional
    public ProductResponse update(Jwt jwt, Long storeId, Long productId, ProductUpdateRequest request) {
        storeAccessValidator.validate(jwt, storeId);
        if (request.name() == null && request.price() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "수정할 상품 정보를 입력해 주세요.");
        }

        Product product = findProduct(storeId, productId);
        String name = request.name() == null ? null : normalizeName(request.name());
        product.update(name, request.price());
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse updateStatus(
            Jwt jwt,
            Long storeId,
            Long productId,
            ProductStatusUpdateRequest request
    ) {
        storeAccessValidator.validate(jwt, storeId);
        Product product = findProduct(storeId, productId);
        product.changeActive(request.active());
        return ProductResponse.from(product);
    }

    private Product findProduct(Long storeId, Long productId) {
        return productRepository.findByIdAndStoreId(productId, storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "상품 이름을 입력해 주세요.");
        }
        return normalized;
    }
}
