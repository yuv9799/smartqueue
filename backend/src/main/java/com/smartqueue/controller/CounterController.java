package com.smartqueue.controller;

import com.smartqueue.model.CheckoutCounter;
import com.smartqueue.repository.CheckoutCounterRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/counters")
public class CounterController {

    private final CheckoutCounterRepository counterRepository;

    public CounterController(

            CheckoutCounterRepository counterRepository) {
        this.counterRepository = counterRepository;
    }

    @GetMapping
    public List<CheckoutCounter> getAllCounters() {
        return counterRepository.findAll(Sort.by("id"));
    }
}
