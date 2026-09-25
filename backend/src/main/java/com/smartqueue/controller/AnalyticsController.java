package com.smartqueue.controller;

import com.smartqueue.dto.AnalyticsOverviewResponse;
import com.smartqueue.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Manager-facing report endpoints.
 */
@RestController
@RequestMapping("/api/reports")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Overview report for an optional time window.
     *
     * <p>Defaults to the last 7 days when {@code from}/{@code to} are absent.
     */
    @GetMapping("/overview")
    public AnalyticsOverviewResponse overview(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to
    ) {
        return analyticsService.overview(from, to);
    }
}
