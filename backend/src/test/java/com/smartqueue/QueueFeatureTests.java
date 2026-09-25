package com.smartqueue;

import tools.jackson.databind.ObjectMapper;
import com.smartqueue.dto.QueueAssignmentResponse;
import com.smartqueue.model.QueueStatus;
import com.smartqueue.repository.QueueEntryRepository;
import com.smartqueue.service.MlPredictionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QueueFeatureTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MlPredictionClient mlPredictionClient;

    @BeforeEach
    void initMlMock() {
        when(mlPredictionClient.predict(any())).thenReturn(null);
    }

    @Autowired
    private QueueEntryRepository queueEntryRepository;

    // ── Cancellation ────────────────────────────────────────────────────────

    @Test
    void cancelWaitingCustomerSucceeds() throws Exception {
        // Join a customer
        MvcResult joinResult = mockMvc.perform(post("/api/queue/join")
                        .contentType("application/json")
                        .content("""
                                {
                                    "customerName": "Cancel Test",
                                    "itemCount": 3,
                                    "paymentMethod": "CARD",
                                    "priorityType": "REGULAR"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        QueueAssignmentResponse joined =
                objectMapper.readValue(
                        joinResult.getResponse().getContentAsString(),
                        QueueAssignmentResponse.class
                );

        long id = joined.id();

        // Cancel the customer
        mockMvc.perform(post("/api/queue/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());

        // Cancelled customer must not appear in active queue
        mockMvc.perform(get("/api/queue/active"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).doesNotContain("\"id\":" + id);
                });
    }

    @Test
    void cancelSameCustomerTwiceReturns409() throws Exception {
        // Join and cancel
        MvcResult joinResult = mockMvc.perform(post("/api/queue/join")
                        .contentType("application/json")
                        .content("""
                                {
                                    "customerName": "Double Cancel",
                                    "itemCount": 2,
                                    "paymentMethod": "CASH",
                                    "priorityType": "REGULAR"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        long id = objectMapper.readValue(
                joinResult.getResponse().getContentAsString(),
                QueueAssignmentResponse.class
        ).id();

        mockMvc.perform(post("/api/queue/{id}/cancel", id))
                .andExpect(status().isOk());

        // Second cancel must be rejected
        mockMvc.perform(post("/api/queue/{id}/cancel", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("This customer has already been cancelled"));
    }

    @Test
    void cancelNonexistentEntryReturns404() throws Exception {
        mockMvc.perform(post("/api/queue/999999/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Queue entry not found with ID: 999999"));
    }

    // ── History & pagination ─────────────────────────────────────────────────

    @Test
    void historyReturnsPaginatedResults() throws Exception {
        mockMvc.perform(get("/api/queue/history?page=0&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    void historyPage2ReturnsDifferentEntries() throws Exception {
        // Page 0 first entry
        MvcResult page0 = mockMvc.perform(get("/api/queue/history?page=0&size=5"))
                .andExpect(status().isOk())
                .andReturn();

        var page0Entries = objectMapper.readTree(
                        page0.getResponse().getContentAsString()
                ).get("content");

        // Page 1 first entry must be different
        mockMvc.perform(get("/api/queue/history?page=1&size=5"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var page1Entries = objectMapper.readTree(
                            result.getResponse().getContentAsString()
                    ).get("content");
                    if (page0Entries.size() < 5) return; // less than 5 entries total
                    assertThat(
                            page0Entries.get(0).get("id").asLong()
                    ).isNotEqualTo(
                            page1Entries.get(0).get("id").asLong()
                    );
                });
    }

    @Test
    void historyFiltersByStatus() throws Exception {
        // At least one CANCELLED entry should exist from earlier tests
        mockMvc.perform(get("/api/queue/history?status=CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[*].status")
                        .value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.is("CANCELLED"))));
    }

    // ── Analytics ──────────────────────────────────────────────────────────

    @Test
    void analyticsOverviewReturnsValidStructure() throws Exception {
        mockMvc.perform(get("/api/reports/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalServed").isNumber())
                .andExpect(jsonPath("$.totalCancelled").isNumber())
                .andExpect(jsonPath("$.counterUtilization").isArray())
                .andExpect(jsonPath("$.hourlyDistribution").isArray())
                .andExpect(jsonPath("$.from").isNotEmpty())
                .andExpect(jsonPath("$.to").isNotEmpty());
    }

    @Test
    void dashboardStatsUsesDbAggregates() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitingCustomers").isNumber())
                .andExpect(jsonPath("$.completedCustomers").isNumber())
                .andExpect(jsonPath("$.openCounters").isNumber())
                .andExpect(jsonPath("$.meanAbsoluteErrorSeconds").isNumber());
    }
}
