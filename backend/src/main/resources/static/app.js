const joinForm = document.getElementById("join-form");
const refreshButton = document.getElementById("refresh-button");
const historyApply = document.getElementById("history-apply");
const historyPrev = document.getElementById("history-prev");
const historyNext = document.getElementById("history-next");
const reportsRefresh = document.getElementById("reports-refresh");

joinForm.addEventListener("submit", joinQueue);
refreshButton.addEventListener("click", loadDashboard);
historyApply.addEventListener("click", () => loadHistory(0));
historyPrev.addEventListener("click", () => loadHistory(historyPage - 1));
historyNext.addEventListener("click", () => loadHistory(historyPage + 1));
reportsRefresh.addEventListener("click", loadReports);

let historyPage = 0;

document.addEventListener("DOMContentLoaded", () => {
    loadDashboard();
    loadHistory(0);
    loadReports();
    populateCounterFilter();

    // Automatically refresh the dashboard every 10 seconds.
    setInterval(loadDashboard, 10000);
});

async function request(url, options = {}) {
    const response = await fetch(url, options);

    let data = null;

    try {
        data = await response.json();
    } catch (error) {
        // Some unsuccessful responses may not contain JSON.
    }

    if (!response.ok) {
        throw new Error(
            data?.message || `Request failed with status ${response.status}`
        );
    }

    return data;
}

async function loadDashboard() {
    try {
        await Promise.all([
            loadStats(),
            loadCounters(),
            loadActiveQueue()
        ]);
    } catch (error) {
        showMessage(error.message, "error");
    }
}

async function loadStats() {
    const stats = await request("/api/dashboard/stats");

    document.getElementById("stat-active").textContent =
        stats.activeCustomers;

    document.getElementById("stat-waiting").textContent =
        stats.waitingCustomers;

    document.getElementById("stat-serving").textContent =
        stats.servingCustomers;

    document.getElementById("stat-completed").textContent =
        stats.completedCustomers;

    document.getElementById("stat-counters").textContent =
        `${stats.openCounters} / ${stats.totalCounters}`;

    document.getElementById("stat-predicted").textContent =
        formatSeconds(
            Math.round(stats.averagePredictedServiceSeconds)
        );

    document.getElementById("stat-actual").textContent =
        formatSeconds(
            Math.round(stats.averageActualServiceSeconds)
        );

    document.getElementById("stat-mae").textContent =
        formatSeconds(
            Math.round(stats.meanAbsoluteErrorSeconds)
        );
}

async function loadCounters() {
    const counters = await request("/api/counters");
    const counterList = document.getElementById("counter-list");

    if (counters.length === 0) {
        counterList.innerHTML = "<p>No checkout counters found.</p>";
        return;
    }

    counterList.innerHTML = counters.map(counter => `
        <div class="counter-card">
            <div>
                <h3>${escapeHtml(counter.name)}</h3>
                <p>Counter ID: ${counter.id}</p>
            </div>

            <div class="counter-right">
    <span class="counter-status ${counter.status.toLowerCase()}">
        ${counter.status}
    </span>

    <button
        class="action-button"
        onclick="startNextCustomer(${counter.id})"
        ${counter.status !== "OPEN" ? "disabled" : ""}
    >
        Start Next
    </button>

    <button
        class="action-button ${
        counter.status === "OPEN"
            ? "close-counter-button"
            : "open-counter-button"
    }"
        onclick="toggleCounterStatus(
            ${counter.id},
            '${counter.status}'
        )"
    >
        ${counter.status === "OPEN" ? "Close" : "Open"}
    </button>
</div>
        </div>
    `).join("");
}

async function loadActiveQueue() {
    const entries = await request("/api/queue/active");
    const queueBody = document.getElementById("queue-body");
    const activeCount = document.getElementById("active-count");

    activeCount.textContent =
        `${entries.length} ${entries.length === 1 ? "customer" : "customers"}`;

    if (entries.length === 0) {
        queueBody.innerHTML = `
            <tr>
                <td colspan="8" class="empty-state">
                    No customers are currently waiting or being served.
                </td>
            </tr>
        `;
        return;
    }

    queueBody.innerHTML = entries.map(entry => {
        const statusClass =
            entry.status === "SERVING"
                ? "status-serving"
                : "status-waiting";

        const action =
            entry.status === "SERVING"
                ? `
                    <button
                        class="action-button complete-button"
                        onclick="completeCustomer(${entry.id})"
                    >
                        Complete
                    </button>
                  `
                : `
                    <button
                        class="cancel-button"
                        onclick="cancelCustomer(${entry.id})"
                    >
                        Cancel
                    </button>
                  `;

        return `
            <tr>
                <td><strong>${escapeHtml(entry.token)}</strong></td>
                <td>${escapeHtml(entry.customerName)}</td>
                <td>${entry.itemCount}</td>
                <td>${escapeHtml(entry.counterName)}</td>
                <td>${entry.paymentMethod}</td>

                <td>
                    <span class="status-badge ${statusClass}">
                        ${entry.status}
                    </span>
                </td>

                <td>${formatSeconds(entry.predictedServiceSeconds)}</td>
                <td>${action}</td>
            </tr>
        `;
    }).join("");
}

async function joinQueue(event) {
    event.preventDefault();

    const submitButton =
        joinForm.querySelector("button[type='submit']");

    const requestBody = {
        customerName:
            document.getElementById("customerName").value.trim(),

        itemCount:
            Number(document.getElementById("itemCount").value),

        paymentMethod:
        document.getElementById("paymentMethod").value,

        priorityType:
        document.getElementById("priorityType").value
    };

    submitButton.disabled = true;
    submitButton.textContent = "Finding best counter...";

    try {
        const result = await request("/api/queue/join", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(requestBody)
        });

        document.getElementById("result-token").textContent =
            result.token;

        document.getElementById("result-counter").textContent =
            result.counterName;

        document.getElementById("result-wait").textContent =
            formatSeconds(result.estimatedWaitSeconds);

        document
            .getElementById("assignment-result")
            .classList.remove("hidden");

        joinForm.reset();

        showMessage(
            `${result.customerName} was assigned to ${result.counterName}.`,
            "success"
        );

        await loadDashboard();
    } catch (error) {
        showMessage(error.message, "error");
    } finally {
        submitButton.disabled = false;
        submitButton.textContent = "Find Best Counter";
    }
}

async function startNextCustomer(counterId) {
    try {
        const result = await request(
            `/api/queue/counters/${counterId}/next`,
            {
                method: "POST"
            }
        );

        showMessage(
            `${result.token} is now being served at ${result.counterName}.`,
            "success"
        );

        await loadDashboard();
    } catch (error) {
        showMessage(error.message, "error");
    }
}

async function toggleCounterStatus(
    counterId,
    currentStatus
) {
    const requestedStatus =
        currentStatus === "OPEN"
            ? "CLOSED"
            : "OPEN";

    try {
        const result = await request(
            `/api/counters/${counterId}/status`,
            {
                method: "PATCH",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    status: requestedStatus
                })
            }
        );

        showMessage(
            `${result.name} is now ${result.status}.`,
            "success"
        );

        await loadDashboard();
    } catch (error) {
        showMessage(error.message, "error");
    }
}

async function completeCustomer(entryId) {
    try {
        const result = await request(
            `/api/queue/${entryId}/complete`,
            {
                method: "POST"
            }
        );

        showMessage(
            `${result.token} checkout completed in ` +
            `${formatSeconds(result.actualServiceSeconds)}.`,
            "success"
        );

        await loadDashboard();
    } catch (error) {
        showMessage(error.message, "error");
    }
}

function formatSeconds(seconds) {
    if (seconds === null || seconds === undefined) {
        return "--";
    }

    if (seconds < 60) {
        return `${seconds} sec`;
    }

    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;

    return remainingSeconds === 0
        ? `${minutes} min`
        : `${minutes} min ${remainingSeconds} sec`;
}

function showMessage(message, type) {
    const messageBox = document.getElementById("message-box");

    messageBox.textContent = message;
    messageBox.className = `message-box message-${type}`;

    window.setTimeout(() => {
        messageBox.classList.add("hidden");
    }, 5000);
}

function escapeHtml(value) {
    const characters = {
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        "\"": "&quot;",
        "'": "&#039;"
    };

    return String(value ?? "").replace(
        /[&<>"']/g,
        character => characters[character]
    );
}

// ── Cancel a customer ─────────────────────────────────────────────────────

async function cancelCustomer(entryId) {
    try {
        const result = await request(
            `/api/queue/${entryId}/cancel`,
            { method: "POST" }
        );
        showMessage(
            `${result.token} was cancelled.`,
            "success"
        );
        await loadDashboard();
    } catch (error) {
        showMessage(error.message, "error");
    }
}

// ── History ────────────────────────────────────────────────────────────────

async function populateCounterFilter() {
    try {
        const counters = await request("/api/counters");
        const select = document.getElementById("history-counter");
        counters.forEach(c => {
            const opt = document.createElement("option");
            opt.value = c.id;
            opt.textContent = c.name;
            select.appendChild(opt);
        });
    } catch (_) {
        // silently skip if counters can't be loaded
    }
}

async function loadHistory(page = 0) {
    historyPage = page;
    const status = document.getElementById("history-status").value;
    const counterId = document.getElementById("history-counter").value;

    const params = new URLSearchParams({ page, size: 25 });
    if (status) params.set("status", status);
    if (counterId) params.set("counterId", counterId);

    try {
        const result = await request(`/api/queue/history?${params}`);

        const { content, totalPages, totalElements, number } = result;

        // Update pager buttons
        document.getElementById("history-prev").disabled = number === 0;
        document.getElementById("history-next").disabled =
            number >= totalPages - 1;
        document.getElementById("history-page-info").textContent =
            `Page ${number + 1} of ${totalPages} — ${totalElements} total`;

        const tbody = document.getElementById("history-body");

        if (content.length === 0) {
            tbody.innerHTML = `
                <tr><td colspan="8" class="empty-state">
                    No records match the current filter.
                </td></tr>`;
            return;
        }

        tbody.innerHTML = content.map(entry => {
            const statusClass =
                entry.status === "CANCELLED"  ? "status-cancelled"  :
                entry.status === "COMPLETED"  ? "status-completed"  :
                entry.status === "SERVING"    ? "status-serving"    :
                                                 "status-waiting";

            const cancelledAt = entry.cancelledAt
                ? formatDateTime(entry.cancelledAt)
                : "—";

            const wait = entry.serviceStartedAt && entry.arrivalTime
                ? formatSeconds(
                    Math.round(
                        (new Date(entry.serviceStartedAt) - new Date(entry.arrivalTime)) / 1000
                    )
                )
                : "—";

            const actualService = entry.actualServiceSeconds != null
                ? formatSeconds(entry.actualServiceSeconds)
                : "—";

            return `
                <tr>
                    <td><strong>${escapeHtml(entry.token)}</strong></td>
                    <td>${escapeHtml(entry.customerName)}</td>
                    <td>${escapeHtml(entry.counterName ?? "—")}</td>
                    <td>
                        <span class="status-badge ${statusClass}">
                            ${entry.status}
                        </span>
                    </td>
                    <td>${formatDateTime(entry.arrivalTime)}</td>
                    <td>${wait}</td>
                    <td>${actualService}</td>
                    <td>${cancelledAt}</td>
                </tr>`;
        }).join("");
    } catch (error) {
        const tbody = document.getElementById("history-body");
        tbody.innerHTML = `
            <tr><td colspan="8" class="empty-state">
                Failed to load history: ${escapeHtml(error.message)}
            </td></tr>`;
    }
}

// ── Reports / Analytics ────────────────────────────────────────────────────

async function loadReports() {
    try {
        const data = await request("/api/reports/overview");
        const container = document.getElementById("reports-content");

        const counterBars = data.counterUtilization
            .map(c => {
                const max = Math.max(...data.counterUtilization.map(x => x.completedCount), 1);
                const pct = (c.completedCount / max) * 100;
                return `
                    <div class="bar-row">
                        <span>${escapeHtml(c.counterName)}</span>
                        <div class="bar-track">
                            <div class="bar-fill" style="width:${pct}%"></div>
                        </div>
                        <span>${c.completedCount}</span>
                    </div>`;
            }).join("");

        const hourlyBars = Array.from({ length: 24 }, (_, h) => {
            const entry = data.hourlyDistribution.find(x => x.hour === h);
            const count = entry ? entry.count : 0;
            const max = Math.max(...data.hourlyDistribution.map(x => x.count), 1);
            const pct = (count / max) * 100;
            return `
                <div class="bar-row">
                    <span>${h.toString().padStart(2, "0")}:00</span>
                    <div class="bar-track">
                        <div class="bar-fill" style="width:${pct}%"></div>
                    </div>
                    <span>${count}</span>
                </div>`;
        }).join("");

        container.innerHTML = `
            <div class="report-card">
                <p>Completed (7d)</p>
                <h3>${data.totalServed}</h3>
            </div>
            <div class="report-card">
                <p>Cancelled (7d)</p>
                <h3>${data.totalCancelled}</h3>
            </div>
            <div class="report-card">
                <p>Avg Wait Time</p>
                <h3>${formatSeconds(Math.round(data.averageWaitSeconds ?? 0))}</h3>
                <span>actual wait in queue</span>
            </div>
            <div class="report-card">
                <p>Avg Service Time</p>
                <h3>${formatSeconds(Math.round(data.averageActualServiceSeconds ?? 0))}</h3>
                <span>actual duration at counter</span>
            </div>
            <div class="report-card">
                <p>Prediction MAE</p>
                <h3>${formatSeconds(Math.round(data.predictionMAESeconds ?? 0))}</h3>
                <span>mean absolute error</span>
            </div>
            <div class="report-card">
                <p>ML Accuracy</p>
                <h3>${data.totalServed > 0
                    ? Math.round((1 - (data.predictionMAESeconds ?? 0) / Math.max(data.averageActualServiceSeconds ?? 1, 1)) * 100)
                    : "—"}</h3>
                <span>1 − MAE / avg service</span>
            </div>

            <div class="report-section" style="grid-column: 1 / -1;">
                <h4>Counter Utilization</h4>
                <div class="bar-chart">${counterBars || "<p>No data.</p>"}</div>
            </div>

            <div class="report-section" style="grid-column: 1 / -1;">
                <h4>Hourly Throughput</h4>
                <div class="bar-chart">${hourlyBars}</div>
            </div>
        `;
    } catch (error) {
        document.getElementById("reports-content").innerHTML = `
            <p class="empty-state">Failed to load reports: ${escapeHtml(error.message)}</p>`;
    }
}

// ── Utility ────────────────────────────────────────────────────────────────

function formatDateTime(iso) {
    if (!iso) return "—";
    const d = new Date(iso);
    return d.toLocaleString("en-GB", {
        day: "2-digit",
        month: "short",
        hour: "2-digit",
        minute: "2-digit"
    });
}