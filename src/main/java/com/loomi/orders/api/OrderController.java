package com.loomi.orders.api;

import com.loomi.orders.api.dto.OrderRequest;
import com.loomi.orders.api.dto.OrderResponse;
import com.loomi.orders.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
        OrderResponse response = orderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getById(@PathVariable("orderId") String orderId) {
        return ResponseEntity.ok(orderService.findById(orderId));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getByCustomer(
            @RequestParam(name = "customerId", required = false) String customerId) {

        List<OrderResponse> response;

        if (customerId == null || customerId.isBlank()) {
            response = orderService.findAll();
        } else {
            response = orderService.findByCustomer(customerId);
        }

        return ResponseEntity.ok(response);
    }

}
