package com.smartlab.zippy.controller.order;

import com.smartlab.zippy.model.dto.web.request.order.OrderRequest;
import com.smartlab.zippy.model.dto.web.response.ApiResponse;
import com.smartlab.zippy.model.dto.web.response.order.OrderResponse;
import com.smartlab.zippy.service.order.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/create")
    public ApiResponse<OrderResponse> createOrder(@RequestBody OrderRequest orderRequest) {
        OrderResponse orderResponse = orderService.createOrder(orderRequest);
        return ApiResponse.success(orderResponse, "Order created successfully");
    }

    @GetMapping("/get")
    public ApiResponse<List<OrderResponse>> getOrder(@RequestParam String username) {
        List<OrderResponse> orderResponse = orderService.getOrderByIdentifier(username);
        return ApiResponse.success(orderResponse, "Order retrieved successfully");
    }
}
