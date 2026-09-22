const joinForm = document.getElementById("join-form");
const refreshButton = document.getElementById("refresh-button");

joinForm.addEventListener("submit", joinQueue);
refreshButton.addEventListener("click", loadDashboard);

document.addEventListener("DOMContentLoaded", () => {
    loadDashboard();

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
            loadCounters(),
            loadActiveQueue()
        ]);
    } catch (error) {
        showMessage(error.message, "error");
    }
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
                : "<span>Waiting</span>";

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