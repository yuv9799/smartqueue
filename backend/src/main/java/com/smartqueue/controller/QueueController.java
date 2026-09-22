package com.smartqueue.controller;

import com.smartqueue.dto.JoinQueueRequest;
import com.smartqueue.dto.QueueAssignmentResponse;
import com.smartqueue.dto.QueueEntryResponse;
import com.smartqueue.service.QueueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
}