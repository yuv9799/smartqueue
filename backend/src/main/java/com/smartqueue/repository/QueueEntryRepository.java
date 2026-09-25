package com.smartqueue.repository;

import com.smartqueue.model.QueueEntry;
import com.smartqueue.model.QueueStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QueueEntryRepository
        extends JpaRepository<QueueEntry, Long> {

    List<QueueEntry> findByStatusInOrderByAssignedAtAsc(
            Collection<QueueStatus> statuses);

    List<QueueEntry> findByCounterIdAndStatusInOrderByAssignedAtAsc(
            Long counterId,
            Collection<QueueStatus> statuses);

    List<QueueEntry> findByCounterIdAndStatusOrderByAssignedAtAsc(
            Long counterId,
            QueueStatus status);

    Optional<QueueEntry> findFirstByCounterIdAndStatus(
            Long counterId,
            QueueStatus status);

    long countByStatus(QueueStatus status);

    // ── History & filtering ────────────────────────────────────────────────

    Page<QueueEntry> findByStatusOrderByArrivalTimeDesc(
            QueueStatus status, Pageable pageable);

    Page<QueueEntry> findByCounterIdOrderByArrivalTimeDesc(
            Long counterId, Pageable pageable);

    Page<QueueEntry> findByCounterIdAndStatusOrderByArrivalTimeDesc(
            Long counterId, QueueStatus status, Pageable pageable);

    Page<QueueEntry> findByArrivalTimeBetweenOrderByArrivalTimeDesc(
            LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<QueueEntry> findByStatusAndArrivalTimeBetweenOrderByArrivalTimeDesc(
            QueueStatus status, LocalDateTime from, LocalDateTime to,
            Pageable pageable);

    Page<QueueEntry> findByCounterIdAndArrivalTimeBetweenOrderByArrivalTimeDesc(
            Long counterId, LocalDateTime from, LocalDateTime to,
            Pageable pageable);

    Page<QueueEntry> findByCounterIdAndStatusAndArrivalTimeBetweenOrderByArrivalTimeDesc(
            Long counterId, QueueStatus status,
            LocalDateTime from, LocalDateTime to,
            Pageable pageable);

    // ── Aggregate analytics ────────────────────────────────────────────────

    @Query(
        "select count(e) from QueueEntry e " +
        "where e.status = :status " +
        "  and e.arrivalTime between :from and :to"
    )
    long countByStatusAndArrivalTimeBetween(
            @Param("status") QueueStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(
        "select avg(e.actualServiceSeconds) from QueueEntry e " +
        "where e.status = 'COMPLETED' " +
        "  and e.actualServiceSeconds is not null"
    )
    Double averageActualServiceSecondsAllTime();

    @Query(
        "select avg(e.actualWaitSeconds) from QueueEntry e " +
        "where e.status = 'COMPLETED' " +
        "  and e.actualWaitSeconds is not null"
    )
    Double averageActualWaitSecondsAllTime();

    @Query(
        "select avg(e.predictedServiceSeconds) from QueueEntry e " +
        "where e.status = 'COMPLETED'"
    )
    Double averagePredictedServiceSecondsAllTime();

    @Query(
        "select avg(abs(e.actualServiceSeconds - e.predictedServiceSeconds)) " +
        "from QueueEntry e " +
        "where e.status = 'COMPLETED' " +
        "  and e.actualServiceSeconds is not null"
    )
    Double averagePredictionErrorSecondsAllTime();

    @Query(
        "select avg(e.actualServiceSeconds) from QueueEntry e " +
        "where e.status = 'COMPLETED' " +
        "  and e.actualServiceSeconds is not null " +
        "  and e.arrivalTime between :from and :to"
    )
    Double averageActualServiceSecondsBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(
        "select avg(e.actualWaitSeconds) from QueueEntry e " +
        "where e.status = 'COMPLETED' " +
        "  and e.actualWaitSeconds is not null " +
        "  and e.arrivalTime between :from and :to"
    )
    Double averageWaitSecondsBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(
        "select avg(e.predictedServiceSeconds) from QueueEntry e " +
        "where e.status = 'COMPLETED' " +
        "  and e.arrivalTime between :from and :to"
    )
    Double averagePredictedServiceSecondsBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(
        "select avg(abs(e.actualServiceSeconds - e.predictedServiceSeconds)) " +
        "from QueueEntry e " +
        "where e.status = 'COMPLETED' " +
        "  and e.actualServiceSeconds is not null " +
        "  and e.arrivalTime between :from and :to"
    )
    Double averagePredictionErrorSecondsBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(
        "select hour(e.arrivalTime), count(e) " +
        "from QueueEntry e " +
        "where e.status = 'COMPLETED' " +
        "  and e.arrivalTime between :from and :to " +
        "group by hour(e.arrivalTime) " +
        "order by hour(e.arrivalTime)"
    )
    List<Object[]> hourlyDistribution(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(
        "select e.counter.id, e.counter.name, count(e) " +
        "from QueueEntry e " +
        "where e.status = 'COMPLETED' " +
        "  and e.arrivalTime between :from and :to " +
        "  and e.counter is not null " +
        "group by e.counter.id, e.counter.name " +
        "order by e.counter.id"
    )
    List<Object[]> counterUtilization(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(
        "select avg(e.estimatedWaitSeconds) from QueueEntry e " +
        "where e.estimatedWaitSeconds is not null " +
        "  and e.arrivalTime between :from and :to"
    )
    Double averageEstimatedWaitSecondsBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(
        "select avg(e.abandonmentProbability) from QueueEntry e " +
        "where e.abandonmentProbability is not null " +
        "  and e.arrivalTime between :from and :to"
    )
    Double averageAbandonmentProbabilityBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(
        "select avg(abs(e.actualWaitSeconds - e.estimatedWaitSeconds)) " +
        "from QueueEntry e " +
        "where e.status = 'COMPLETED' " +
        "  and e.actualWaitSeconds is not null " +
        "  and e.estimatedWaitSeconds is not null " +
        "  and e.arrivalTime between :from and :to"
    )
    Double averageWaitPredictionErrorSecondsBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}