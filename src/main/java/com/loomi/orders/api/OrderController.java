package com.loomi.orders.api;

import com.loomi.orders.api.dto.OrderRequest;
import com.loomi.orders.api.dto.OrderResponse;
import com.loomi.orders.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody OrderRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        OrderResponse response = orderService.create(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getById(@PathVariable("orderId") String orderId) {
        return ResponseEntity.ok(orderService.findById(orderId));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getByCustomer(
            @RequestParam(name = "customerId", required = false) String customerId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        List<OrderResponse> response;

        if (customerId == null || customerId.isBlank()) {
            response = orderService.findAll(page, size);
        } else {
            response = orderService.findByCustomer(customerId, page, size);
        }

        return ResponseEntity.ok(response);
    }

}
