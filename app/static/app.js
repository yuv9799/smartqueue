const api = {
    async request(path, options = {}) {
        const response = await fetch(path, {
            headers: { "Content-Type": "application/json", ...(options.headers || {}) },
            ...options,
        });
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(body.detail || "Request failed");
        return body;
    },
};

const escapeHtml = (value) => String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");

const formatTime = (seconds) => {
    const value = Number(seconds || 0);
    if (value < 60) return `${value}s`;
    return `${Math.floor(value / 60)}m ${value % 60}s`;
};

function showToast(message, type = "success") {
    const toast = document.querySelector("#toast");
    toast.textContent = message;
    toast.className = `toast ${type}`;
    window.setTimeout(() => toast.classList.add("hidden"), 3200);
}

function renderStats(stats) {
    const cards = [
        ["Waiting now", stats.waiting, "customers"],
        ["Being served", stats.serving, "customers"],
        ["Average wait", `${stats.average_wait_minutes} min`, "observed"],
        ["Hourly throughput", stats.throughput_last_hour, "completed"],
    ];
    document.querySelector("#stats").innerHTML = cards.map(([label, value, note]) => `
        <article class="stat-card">
            <p>${escapeHtml(label)}</p>
            <strong>${escapeHtml(value)}</strong>
            <span>${escapeHtml(note)}</span>
        </article>
    `).join("");
}

function renderCounters(counters) {
    document.querySelector("#counters").innerHTML = counters.map((counter) => {
        const isOpen = counter.status === "open";
        const percent = Math.min(100, Math.round(Number(counter.workload_seconds) / 6));
        return `
            <div class="counter-card ${isOpen ? "" : "counter-closed"}">
                <div class="counter-title">
                    <div>
                        <strong>${escapeHtml(counter.name)}</strong>
                        <span>${counter.active_customers} assigned · ${formatTime(counter.workload_seconds)} load</span>
                    </div>
                    <span class="status-dot ${isOpen ? "open" : "closed"}">${escapeHtml(counter.status)}</span>
                </div>
                <div class="load-track"><span style="width:${percent}%"></span></div>
                <div class="counter-actions">
                    <button class="small-button" data-action="next" data-id="${counter.id}" ${isOpen ? "" : "disabled"}>Call next</button>
                    <button class="small-button muted" data-action="toggle" data-id="${counter.id}" data-status="${isOpen ? "closed" : "open"}">${isOpen ? "Close" : "Open"}</button>
                </div>
            </div>
        `;
    }).join("");
}

function renderQueue(queue) {
    const body = document.querySelector("#queue-body");
    const empty = document.querySelector("#empty-state");
    empty.classList.toggle("hidden", queue.length !== 0);
    body.innerHTML = queue.map((entry) => `
        <tr>
            <td><strong>${escapeHtml(entry.token)}</strong></td>
            <td>
                ${escapeHtml(entry.customer_name)}
                ${entry.priority_type === "assistance" ? '<span class="mini-badge">assistance</span>' : ""}
            </td>
            <td>${entry.item_count} items · ${escapeHtml(entry.payment_method.toUpperCase())}</td>
            <td>${formatTime(entry.predicted_service_seconds)}</td>
            <td>${escapeHtml(entry.counter_name)}</td>
            <td><span class="queue-status ${entry.status}">${escapeHtml(entry.status)}</span></td>
            <td>
                ${entry.status === "serving"
                    ? `<button class="small-button complete" data-action="complete" data-id="${entry.id}">Complete</button>`
                    : '<span class="muted-text">Await call</span>'}
            </td>
        </tr>
    `).join("");
}

async function refresh() {
    try {
        const state = await api.request("/api/state");
        renderStats(state.statistics);
        renderCounters(state.counters);
        renderQueue(state.queue);
        const badge = document.querySelector("#model-badge");
        badge.textContent = state.model.trained_model_loaded
            ? `ML: ${state.model.name}`
            : "ML fallback active";
        badge.classList.toggle("model-ready", state.model.trained_model_loaded);
        document.querySelector("#last-updated").textContent = `Updated ${new Date().toLocaleTimeString()}`;
    } catch (error) {
        showToast(error.message, "error");
    }
}

document.querySelector("#join-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
        const entry = await api.request("/api/queue/join", {
            method: "POST",
            body: JSON.stringify({
                customer_name: form.get("customer_name"),
                item_count: Number(form.get("item_count")),
                payment_method: form.get("payment_method"),
                priority_type: form.get("assistance") ? "assistance" : "regular",
            }),
        });
        const ticket = document.querySelector("#ticket");
        ticket.classList.remove("hidden");
        ticket.innerHTML = `
            <span>ASSIGNMENT READY</span>
            <strong>${escapeHtml(entry.token)}</strong>
            <p>Proceed to <b>${escapeHtml(entry.counter_name)}</b></p>
            <small>Estimated wait: ${formatTime(entry.estimated_wait_seconds)} · Checkout: ${formatTime(entry.predicted_service_seconds)}</small>
        `;
        event.currentTarget.reset();
        await refresh();
    } catch (error) {
        showToast(error.message, "error");
    }
});

document.addEventListener("click", async (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    button.disabled = true;
    try {
        if (button.dataset.action === "next") {
            const entry = await api.request(`/api/counters/${button.dataset.id}/next`, { method: "POST" });
            showToast(`${entry.token} called to the counter`);
        } else if (button.dataset.action === "complete") {
            const entry = await api.request(`/api/queue/${button.dataset.id}/complete`, { method: "POST" });
            showToast(`${entry.token} checkout completed`);
        } else if (button.dataset.action === "toggle") {
            await api.request(`/api/counters/${button.dataset.id}`, {
                method: "PATCH",
                body: JSON.stringify({ status: button.dataset.status }),
            });
            showToast(`Counter ${button.dataset.status}`);
        }
        await refresh();
    } catch (error) {
        showToast(error.message, "error");
    } finally {
        button.disabled = false;
    }
});

document.querySelector("#load-demo").addEventListener("click", async () => {
    try {
        const result = await api.request("/api/demo/load", { method: "POST" });
        showToast(`${result.customers_loaded} demo customers added`);
        await refresh();
    } catch (error) {
        showToast(error.message, "error");
    }
});

document.querySelector("#reset-demo").addEventListener("click", async () => {
    if (!window.confirm("Clear all prototype queue records?")) return;
    try {
        await api.request("/api/demo/reset", { method: "POST" });
        document.querySelector("#ticket").classList.add("hidden");
        showToast("Demo data reset");
        await refresh();
    } catch (error) {
        showToast(error.message, "error");
    }
});

refresh();
window.setInterval(refresh, 5000);

