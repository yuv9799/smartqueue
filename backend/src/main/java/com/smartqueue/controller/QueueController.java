package com.smartqueue.controller;

import com.smartqueue.dto.JoinQueueRequest;
import com.smartqueue.dto.QueueAssignmentResponse;
import com.smartqueue.dto.QueueEntryResponse;
import com.smartqueue.model.QueueStatus;
import com.smartqueue.service.QueueService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    public QueueAssignmentResponse joinQueue(
            @Valid @RequestBody JoinQueueRequest request
    ) {
        return queueService.joinQueue(request);
    }

    @GetMapping("/active")
    public List<QueueEntryResponse> getActiveQueue() {
        return queueService.getActiveQueue();
    }

    @GetMapping("/history")
    public Page<QueueEntryResponse> getHistory(
            @RequestParam(required = false) QueueStatus status,
            @RequestParam(required = false) Long counterId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return queueService.getHistory(status, counterId, from, to, page, size);
    }

    @PostMapping("/counters/{counterId}/next")
    public QueueEntryResponse startNextCustomer(
            @PathVariable Long counterId
    ) {
        return queueService.startNextCustomer(counterId);
    }

    @PostMapping("/{entryId}/complete")
    public QueueEntryResponse completeCustomer(
            @PathVariable Long entryId
    ) {
        return queueService.completeCustomer(entryId);
    }

    @PostMapping("/{entryId}/cancel")
    public QueueEntryResponse cancelCustomer(
            @PathVariable Long entryId
    ) {
        return queueService.cancelCustomer(entryId);
    }
}