package com.smartqueue.model;

import jakarta.persistence.*;
@Entity
@Table(name = "checkout_counters")
public class CheckoutCounter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CounterStatus status;

    protected CheckoutCounter() {
        // Required by JPA
    }

    public CheckoutCounter(String name, CounterStatus status) {
        this.name = name;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CounterStatus getStatus() {
        return status;
    }

    public void setStatus(CounterStatus status) {
        this.status = status;
    }
}
