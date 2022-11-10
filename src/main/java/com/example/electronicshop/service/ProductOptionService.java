package com.example.electronicshop.service;

import com.example.electronicshop.config.CloudinaryConfig;
import com.example.electronicshop.config.Constant;
import com.example.electronicshop.models.ResponseObject;
import com.example.electronicshop.models.product.Product;
import com.example.electronicshop.models.product.ProductOption;
import com.example.electronicshop.models.product.ProductSelects;
import com.example.electronicshop.notification.AppException;
import com.example.electronicshop.notification.NotFoundException;
import com.example.electronicshop.communication.request.ProductOptionReq;
import com.example.electronicshop.repository.ProductOptionRepository;
import com.example.electronicshop.repository.ProductRepository;
import com.mongodb.MongoWriteException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class ProductOptionService {
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;

    @Transactional
    public ResponseEntity<?> addOption(String productId , ProductOptionReq req) {
        Optional<ProductOption> checkOption = productOptionRepository.findByNameAndVariantsColorAndProductId(
                req.getName(), req.getValue(), new ObjectId(productId));
        if (checkOption.isPresent()) {
            throw new AppException(HttpStatus.CONFLICT.value(),
                    String.format("Name: %s, value: %s, product id: %s already exists",
                            req.getName(), req.getValue(), productId));
        }
        Optional<ProductOption> option = productOptionRepository.findByNameAndProduct_Id(req.getName(), new ObjectId(productId));
        Optional<Product> product = productRepository.findProductByIdAndState(productId, Constant.ENABLE);
        if (product.isEmpty()) throw new NotFoundException("Can not found product with id: "+productId);
        // case does not exist size
        if (option.isEmpty()) {
            ProductOption productOption = new ProductOption(req.getName());
            productOption.setProduct(product.get());
            processVariant(productOption, req.getValue(), req.getStock(), product.get());
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new ResponseObject("true", "Add product option success", productOption));
        } else {
            processVariant(option.get(), req.getValue(), req.getStock(), product.get());
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new ResponseObject("true", "Add product option success", option.get()));
        }
    }

    public void processVariant (ProductOption productOption ,String value, Long stock, Product product) {
        ProductSelects newValue = new ProductSelects(UUID.randomUUID(), value, stock);
        productOption.getSelects().add(newValue);
        try {
            productOptionRepository.save(productOption);
        } catch (MongoWriteException e) {
            log.error(e.getMessage());
            throw new AppException(HttpStatus.CONFLICT.value(), "Color already exists");
        }
    }

    public ResponseEntity<?> findOptionById(String id) {
        Optional<ProductOption> productOption = productOptionRepository.findById(id);
        if (productOption.isPresent())
            return ResponseEntity.status(HttpStatus.OK).body(
                    new ResponseObject("true", "Get product option success", productOption.get()));
        throw new NotFoundException("Can not found product option with id: "+id);
    }

    public ResponseEntity<?> findOptionByProductId(String id) {
        List<ProductOption> productOptions = productOptionRepository.findAllByProduct_Id(new ObjectId(id));
        if (productOptions.size() > 0) {
            return ResponseEntity.status(HttpStatus.OK).body(
                    new ResponseObject("true", "Get product option success", productOptions));
        } throw new NotFoundException("Can not found any product option with id: "+id);
    }

    @Transactional
    public ResponseEntity<?> updateOptionVariant(String id, String value, ProductOptionReq req) {
        Optional<ProductOption> productOption = productOptionRepository.findByIdAndVariantColor(id, value);
        if (productOption.isPresent()) {
            productOption.get().setName(req.getName());
            productOption.get().getSelects().forEach(variant -> {
                if (variant.getValue().equals(value)) {
                    variant.setStock(req.getStock());
                    if (!variant.getValue().equals(req.getValue())) {
                        variant.setValue(req.getValue());
                    }
                }
            });
            try {
                productOptionRepository.save(productOption.get());
                return ResponseEntity.status(HttpStatus.OK).body(
                        new ResponseObject("true", "Update product option success", productOption.get()));
            } catch (Exception e) {
                log.error(e.getMessage());
                throw new AppException(HttpStatus.EXPECTATION_FAILED.value(), "Error when update option");
            }

        } throw new NotFoundException("Can not found product option with id: "+id);
    }
}