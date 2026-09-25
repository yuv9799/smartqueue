package com.smartqueue.controller;

import com.smartqueue.dto.CounterStatusUpdateRequest;
import com.smartqueue.model.CheckoutCounter;
import com.smartqueue.service.CounterService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/counters")
public class CounterController {

    private final CounterService counterService;

    public CounterController(CounterService counterService) {
        this.counterService = counterService;
    }

    @GetMapping
    public List<CheckoutCounter> getCounters() {
        return counterService.getAllCounters();
    }

    @PatchMapping("/{counterId}/status")
    public CheckoutCounter updateCounterStatus(
            @PathVariable Long counterId,
            @Valid @RequestBody CounterStatusUpdateRequest request
    ) {
        return counterService.updateCounterStatus(
                counterId,
                request
        );
    }
}