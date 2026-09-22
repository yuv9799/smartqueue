package com.smartqueue.repository;

import com.smartqueue.model.CheckoutCounter;
import com.smartqueue.model.CounterStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckoutCounterRepository
        extends JpaRepository<CheckoutCounter, Long> {

    List<CheckoutCounter> findByStatus(CounterStatus status);

    boolean existsByName(String name);
}

