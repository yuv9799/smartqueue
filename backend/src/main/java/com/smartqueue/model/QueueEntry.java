package com.smartqueue.model;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "queue_entries")
public class QueueEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 20)
    private String token;

    @Column(nullable = false, length = 60)
    private String customerName;

    @Column(nullable = false)
    private int itemCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PriorityType priorityType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private QueueStatus status;

    @Column(nullable = false)
    private int predictedServiceSeconds;

    @Column(nullable = false, length = 50)
    private String predictionSource;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "counter_id", nullable = false)
    private CheckoutCounter counter;

    @Column(nullable = false)
    private LocalDateTime arrivalTime;

    @Column(nullable = false)
    private LocalDateTime assignedAt;

    private LocalDateTime serviceStartedAt;

    private LocalDateTime serviceCompletedAt;

    private Integer actualServiceSeconds;

    /** Seconds between arrival and service start. Computed when service starts. */
    private Integer actualWaitSeconds;

    /**
     * ML-predicted wait time in seconds, stored at join time.
     * Enables analytics: predicted wait vs actual wait.
     */
    private Long estimatedWaitSeconds;

    /**
     * ML-predicted abandonment probability (0.0–1.0), stored at join time.
     * Enables analytics: abandonment rate vs predicted risk.
     */
    private Double abandonmentProbability;

    private LocalDateTime cancelledAt;

    protected QueueEntry() {
        // Required by JPA
    }

    public QueueEntry(
            String customerName,
            int itemCount,
            PaymentMethod paymentMethod,
            PriorityType priorityType,
            int predictedServiceSeconds,
            String predictionSource,
            CheckoutCounter counter,
            Long estimatedWaitSeconds,
            Double abandonmentProbability) {

        this.customerName = customerName;
        this.itemCount = itemCount;
        this.paymentMethod = paymentMethod;
        this.priorityType = priorityType;
        this.predictedServiceSeconds = predictedServiceSeconds;
        this.predictionSource = predictionSource;
        this.counter = counter;
        this.estimatedWaitSeconds = estimatedWaitSeconds;
        this.abandonmentProbability = abandonmentProbability;
        this.status = QueueStatus.WAITING;

        LocalDateTime now = LocalDateTime.now();
        this.arrivalTime = now;
        this.assignedAt = now;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void startService() {
        if (status != QueueStatus.WAITING) {
            throw new IllegalStateException(
                    "Only a waiting customer can start service");
        }

        this.status = QueueStatus.SERVING;
        this.serviceStartedAt = LocalDateTime.now();

        long waitSeconds = Duration.between(
                arrivalTime,
                serviceStartedAt
        ).toSeconds();
        this.actualWaitSeconds = (int) Math.max(0, waitSeconds);
    }

    public void completeService() {
        if (status != QueueStatus.SERVING) {
            throw new IllegalStateException(
                    "Only a customer being served can be completed");
        }

        this.serviceCompletedAt = LocalDateTime.now();
        this.status = QueueStatus.COMPLETED;

        long duration = Duration.between(
                serviceStartedAt,
                serviceCompletedAt
        ).toSeconds();

        this.actualServiceSeconds = (int) Math.max(1, duration);
    }

    public void cancel() {
        if (status == QueueStatus.COMPLETED) {
            throw new IllegalStateException(
                    "A completed customer cannot be cancelled");
        }

        if (status == QueueStatus.CANCELLED) {
            throw new IllegalStateException(
                    "This customer has already been cancelled");
        }

        this.status = QueueStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public String getCustomerName() {
        return customerName;
    }

    public int getItemCount() {
        return itemCount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public PriorityType getPriorityType() {
        return priorityType;
    }

    public QueueStatus getStatus() {
        return status;
    }

    public int getPredictedServiceSeconds() {
        return predictedServiceSeconds;
    }

    public String getPredictionSource() {
        return predictionSource;
    }

    public CheckoutCounter getCounter() {
        return counter;
    }

    public LocalDateTime getArrivalTime() {
        return arrivalTime;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public LocalDateTime getServiceStartedAt() {
        return serviceStartedAt;
    }

    public LocalDateTime getServiceCompletedAt() {
        return serviceCompletedAt;
    }

    public Integer getActualServiceSeconds() {
        return actualServiceSeconds;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public Integer getActualWaitSeconds() {
        return actualWaitSeconds;
    }

    public Long getEstimatedWaitSeconds() {
        return estimatedWaitSeconds;
    }

    public Double getAbandonmentProbability() {
        return abandonmentProbability;
    }
}
