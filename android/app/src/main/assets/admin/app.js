(function () {
  const loginView = document.getElementById("loginView");
  const mainView = document.getElementById("mainView");
  const logoutBtn = document.getElementById("logoutBtn");
  const loginError = document.getElementById("loginError");
  const toast = document.getElementById("toast");
  let authenticated = false;

  function showToast(msg) {
    toast.textContent = msg;
    toast.hidden = false;
    clearTimeout(showToast._t);
    showToast._t = setTimeout(() => { toast.hidden = true; }, 2200);
  }

  async function api(path, options = {}) {
    const opts = {
      credentials: "same-origin",
      headers: { "Content-Type": "application/json", ...(options.headers || {}) },
      ...options,
    };
    const res = await fetch(path, opts);
    let data = null;
    try { data = await res.json(); } catch (_) { data = null; }
    if (!res.ok) {
      const err = new Error((data && data.error) || res.statusText || "request_failed");
      err.status = res.status;
      err.data = data;
      throw err;
    }
    return data;
  }

  function setAuthed(on) {
    authenticated = on;
    loginView.hidden = on;
    mainView.hidden = !on;
    logoutBtn.hidden = !on;
  }

  document.getElementById("loginBtn").addEventListener("click", async () => {
    loginError.hidden = true;
    const token = document.getElementById("tokenInput").value.trim();
    try {
      await api("/api/login", { method: "POST", body: JSON.stringify({ token }) });
      setAuthed(true);
      await refreshAll();
      showToast("Signed in");
    } catch (e) {
      loginError.textContent = e.message === "invalid_token" ? "Invalid token" : "Could not sign in";
      loginError.hidden = false;
    }
  });

  logoutBtn.addEventListener("click", async () => {
    try { await api("/api/logout", { method: "POST", body: "{}" }); } catch (_) {}
    setAuthed(false);
  });

  document.querySelectorAll(".tab").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".tab").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      const tab = btn.dataset.tab;
      document.getElementById("todoPanel").hidden = tab !== "todo";
      document.getElementById("calendarPanel").hidden = tab !== "calendar";
      document.getElementById("servicesPanel").hidden = tab !== "services";
      document.getElementById("appearancePanel").hidden = tab !== "appearance";
      if (tab === "services") loadServicesTab();
      if (tab === "appearance") loadAppearance();
    });
  });

  let listedMonitors = [];
  let selectedIds = new Set();
  let labelOverrides = {};

  async function loadServicesTab() {
    await Promise.all([
      loadUptimeConfig(),
      loadHttpEndpoints(),
      loadDashboardPrefs(),
      loadWf1(),
      loadSmartTube(),
    ]);
  }

  async function loadUptimeConfig() {
    const data = await api("/api/uptime/config");
    selectedIds = new Set(data.selectedMonitorIds || []);
    labelOverrides = data.labels || {};
    document.getElementById("uptimeStatus").textContent = data.apiKeySet
      ? `API key set · ${selectedIds.size} monitor(s) selected · configured=${!!data.configured}`
      : "No API key yet — submit one for TV approval.";
    const pendingEl = document.getElementById("uptimePending");
    if (data.pending && data.pending.pending) {
      pendingEl.hidden = false;
      pendingEl.textContent = `Pending TV approval (id ${data.pending.id}, ${Math.round((data.pending.expiresInMs || 0) / 1000)}s left)`;
    } else {
      pendingEl.hidden = true;
    }
    renderMonitorList();
  }

  function renderMonitorList() {
    const host = document.getElementById("uptimeMonitorList");
    host.innerHTML = "";
    const source = listedMonitors.length
      ? listedMonitors
      : [...selectedIds].map((id) => ({ id, name: labelOverrides[id] || id, status: "?" }));
    if (!source.length) {
      host.innerHTML = "<p class='muted'>List monitors after the key is approved, or selected IDs will appear here.</p>";
      return;
    }
    source.forEach((m) => {
      const row = document.createElement("label");
      row.className = "check";
      const cb = document.createElement("input");
      cb.type = "checkbox";
      cb.checked = selectedIds.has(m.id);
      cb.addEventListener("change", () => {
        if (cb.checked) selectedIds.add(m.id);
        else selectedIds.delete(m.id);
      });
      const label = document.createElement("input");
      label.type = "text";
      label.placeholder = "Display label";
      label.value = labelOverrides[m.id] || m.name || "";
      label.addEventListener("change", () => {
        labelOverrides[m.id] = label.value.trim();
      });
      row.appendChild(cb);
      row.appendChild(document.createTextNode(` ${m.name || m.id} (${m.id}) `));
      row.appendChild(label);
      host.appendChild(row);
    });
  }

  document.getElementById("uptimeSubmitKey").addEventListener("click", async () => {
    const apiKey = document.getElementById("uptimeApiKey").value.trim();
    if (!apiKey) { showToast("Enter an API key"); return; }
    try {
      await api("/api/uptime/key", { method: "POST", body: JSON.stringify({ apiKey }) });
      document.getElementById("uptimeApiKey").value = "";
      showToast("Key sent — approve on the TV");
      await loadUptimeConfig();
    } catch (e) {
      showToast(e.message || "Failed");
    }
  });

  document.getElementById("uptimeTest").addEventListener("click", async () => {
    try {
      const body = {};
      const k = document.getElementById("uptimeApiKey").value.trim();
      if (k) body.apiKey = k;
      const data = await api("/api/uptime/test", { method: "POST", body: JSON.stringify(body) });
      showToast(`OK — ${data.monitorCount} monitors`);
    } catch (e) {
      showToast(e.message || "Test failed");
    }
  });

  document.getElementById("uptimeList").addEventListener("click", async () => {
    try {
      const data = await api("/api/uptime/monitors");
      listedMonitors = data.monitors || [];
      renderMonitorList();
      showToast(`Loaded ${listedMonitors.length} monitors`);
    } catch (e) {
      showToast(e.message || "List failed");
    }
  });

  document.getElementById("uptimeForm").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    try {
      const labels = {};
      selectedIds.forEach((id) => {
        if (labelOverrides[id]) labels[id] = labelOverrides[id];
      });
      await api("/api/uptime/selection", {
        method: "POST",
        body: JSON.stringify({
          selectedMonitorIds: [...selectedIds],
          labels,
        }),
      });
      showToast("Selection saved");
      await loadUptimeConfig();
    } catch (e) {
      showToast(e.message || "Failed");
    }
  });

  document.getElementById("uptimeClear").addEventListener("click", async () => {
    if (!confirm("Disconnect UptimeRobot (wipe key + selection)?")) return;
    try {
      await api("/api/uptime/config", { method: "DELETE" });
      listedMonitors = [];
      selectedIds = new Set();
      showToast("Disconnected");
      await loadUptimeConfig();
    } catch (e) {
      showToast(e.message || "Failed");
    }
  });

  async function loadHttpEndpoints() {
    const data = await api("/api/http/endpoints");
    const host = document.getElementById("httpList");
    host.innerHTML = "";
    (data.endpoints || []).forEach((ep) => {
      const row = document.createElement("div");
      row.className = "list-row";
      row.innerHTML = `<strong>${ep.name}</strong> <span class="muted">${ep.url}</span> · ${ep.enabled ? "on" : "off"}`;
      const del = document.createElement("button");
      del.className = "ghost";
      del.textContent = "Delete";
      del.addEventListener("click", async () => {
        await api(`/api/http/endpoints/${ep.id}`, { method: "DELETE" });
        showToast("Deleted");
        await loadHttpEndpoints();
      });
      const tog = document.createElement("button");
      tog.className = "ghost";
      tog.textContent = ep.enabled ? "Disable" : "Enable";
      tog.addEventListener("click", async () => {
        await api(`/api/http/endpoints/${ep.id}`, {
          method: "PATCH",
          body: JSON.stringify({ enabled: !ep.enabled }),
        });
        await loadHttpEndpoints();
      });
      row.appendChild(tog);
      row.appendChild(del);
      host.appendChild(row);
    });
  }

  document.getElementById("httpForm").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    try {
      await api("/api/http/endpoints", {
        method: "POST",
        body: JSON.stringify({
          name: document.getElementById("httpName").value,
          url: document.getElementById("httpUrl").value,
        }),
      });
      ev.target.reset();
      showToast("Endpoint added");
      await loadHttpEndpoints();
    } catch (e) {
      showToast(e.message || "Failed");
    }
  });

  document.getElementById("httpTest").addEventListener("click", async () => {
    const url = document.getElementById("httpUrl").value.trim();
    if (!url) { showToast("Enter a URL"); return; }
    try {
      const data = await api("/api/http/test", { method: "POST", body: JSON.stringify({ url }) });
      document.getElementById("httpTestResult").textContent =
        `${data.status}${data.responseTimeMs != null ? ` · ${data.responseTimeMs} ms` : ""}${data.error ? ` · ${data.error}` : ""}`;
    } catch (e) {
      document.getElementById("httpTestResult").textContent = e.message || "Failed";
    }
  });

  async function loadDashboardPrefs() {
    const data = await api("/api/dashboard");
    document.getElementById("dashServices").checked = !!data.servicesEnabled;
    document.getElementById("dashNowPlaying").checked = !!data.nowPlayingEnabled;
    document.getElementById("dashWf1").checked = !!data.wf1Enabled;
  }

  document.getElementById("dashSave").addEventListener("click", async () => {
    try {
      await api("/api/dashboard", {
        method: "POST",
        body: JSON.stringify({
          servicesEnabled: document.getElementById("dashServices").checked,
          nowPlayingEnabled: document.getElementById("dashNowPlaying").checked,
          wf1Enabled: document.getElementById("dashWf1").checked,
        }),
      });
      showToast("Dashboard prefs saved");
      await loadWf1();
    } catch (e) {
      showToast(e.message || "Failed");
    }
  });

  async function loadWf1() {
    const data = await api("/api/wf1/config");
    const status = document.getElementById("wf1Status");
    status.textContent = data.configured
      ? `Configured · ${data.baseUrl || "(url set)"} · enabled=${!!data.wf1Enabled}`
      : `Not configured · enabled=${!!data.wf1Enabled}`;
    if (data.baseUrl) document.getElementById("wf1BaseUrl").value = data.baseUrl;
    const pendingEl = document.getElementById("wf1Pending");
    if (data.pending && data.pending.pending) {
      pendingEl.hidden = false;
      pendingEl.textContent = `TV approval pending (${data.pending.kind || "secret"}) — expire in ${Math.round((data.pending.expiresInMs || 0) / 1000)}s`;
    } else {
      pendingEl.hidden = true;
    }
  }

  document.getElementById("wf1SaveUrl").addEventListener("click", async () => {
    try {
      const baseUrl = document.getElementById("wf1BaseUrl").value.trim();
      await api("/api/wf1/config", {
        method: "POST",
        body: JSON.stringify({ baseUrl, wf1Enabled: true }),
      });
      document.getElementById("dashWf1").checked = true;
      showToast("WF1 URL saved");
      await loadWf1();
      await loadDashboardPrefs();
    } catch (e) {
      showToast(e.message || "Failed");
    }
  });

  document.getElementById("wf1SubmitToken").addEventListener("click", async () => {
    try {
      const token = document.getElementById("wf1Token").value.trim();
      const baseUrl = document.getElementById("wf1BaseUrl").value.trim();
      if (!token) {
        showToast("Token required");
        return;
      }
      await api("/api/wf1/token", {
        method: "POST",
        body: JSON.stringify({ token, baseUrl: baseUrl || undefined }),
      });
      document.getElementById("wf1Token").value = "";
      showToast("Approve the WF1 token on the TV remote");
      await loadWf1();
    } catch (e) {
      showToast(e.message || "Failed");
    }
  });

  document.getElementById("wf1Clear").addEventListener("click", async () => {
    try {
      await api("/api/wf1/config", { method: "DELETE" });
      document.getElementById("wf1BaseUrl").value = "";
      document.getElementById("wf1Token").value = "";
      document.getElementById("dashWf1").checked = false;
      showToast("WF1 cleared");
      await loadWf1();
      await loadDashboardPrefs();
    } catch (e) {
      showToast(e.message || "Failed");
    }
  });

  async function loadSmartTube() {
    const data = await api("/api/smarttube");
    document.getElementById("smartTubeStatus").textContent =
      `${data.state || "?"} — ${data.detail || ""}`;
    const ol = document.getElementById("smartTubeSteps");
    ol.innerHTML = "";
    (data.setupSteps || []).forEach((s) => {
      const li = document.createElement("li");
      li.textContent = s;
      ol.appendChild(li);
    });
  }

  document.getElementById("taskForm").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    try {
      await api("/api/tasks", {
        method: "POST",
        body: JSON.stringify({
          title: document.getElementById("taskTitle").value,
          description: document.getElementById("taskDesc").value || null,
          priority: document.getElementById("taskPriority").value,
          dueDate: document.getElementById("taskDue").value || null,
        }),
      });
      ev.target.reset();
      showToast("Task added");
      await loadTasks();
    } catch (e) {
      showToast(e.message || "Failed");
    }
  });

  document.getElementById("eventAllDay").addEventListener("change", (ev) => {
    const disabled = ev.target.checked;
    document.getElementById("eventStart").disabled = disabled;
    document.getElementById("eventEnd").disabled = disabled;
  });

  document.getElementById("eventForm").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const allDay = document.getElementById("eventAllDay").checked;
    try {
      await api("/api/events", {
        method: "POST",
        body: JSON.stringify({
          title: document.getElementById("eventTitle").value,
          date: document.getElementById("eventDate").value,
          allDay,
          startTime: allDay ? null : (document.getElementById("eventStart").value || null),
          endTime: allDay ? null : (document.getElementById("eventEnd").value || null),
          description: document.getElementById("eventDesc").value || null,
        }),
      });
      ev.target.reset();
      showToast("Event added");
      await loadEvents();
    } catch (e) {
      showToast(e.message || "Failed");
    }
  });

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  async function loadTasks() {
    const data = await api("/api/tasks");
    const pending = data.tasks.filter((t) => t.status === "pending");
    const completed = data.tasks.filter((t) => t.status === "completed");
    document.getElementById("pendingList").innerHTML = pending.map(taskItem).join("") || empty("No pending tasks");
    document.getElementById("completedList").innerHTML = completed.map(taskItem).join("") || empty("No completed tasks");
    bindTaskActions();
  }

  function empty(text) {
    return `<p class="muted">${esc(text)}</p>`;
  }

  function taskItem(t) {
    const actions = t.status === "pending"
      ? `<button data-act="complete" data-id="${esc(t.id)}">Complete</button>
         <button data-act="edit" data-id="${esc(t.id)}">Edit</button>
         <button class="danger" data-act="delete" data-id="${esc(t.id)}">Delete</button>`
      : `<button data-act="reopen" data-id="${esc(t.id)}">Reopen</button>
         <button class="danger" data-act="delete" data-id="${esc(t.id)}">Delete</button>`;
    return `<div class="item">
      <div class="item-title">${esc(t.title)} <span class="badge ${esc(t.priority)}">${esc(t.priority)}</span></div>
      <div class="meta">${esc(t.description || "")}${t.dueDate ? " · due " + esc(t.dueDate) : ""}</div>
      <div class="actions">${actions}</div>
    </div>`;
  }

  function bindTaskActions() {
    document.querySelectorAll("#todoPanel [data-act]").forEach((btn) => {
      btn.onclick = async () => {
        const id = btn.dataset.id;
        const act = btn.dataset.act;
        try {
          if (act === "complete") await api(`/api/tasks/${id}/complete`, { method: "POST", body: "{}" });
          if (act === "reopen") await api(`/api/tasks/${id}/reopen`, { method: "POST", body: "{}" });
          if (act === "delete") {
            if (!confirm("Delete this task?")) return;
            await api(`/api/tasks/${id}`, { method: "DELETE" });
          }
          if (act === "edit") {
            const title = prompt("Title");
            if (!title) return;
            const priority = prompt("Priority (low/normal/high)", "normal");
            await api(`/api/tasks/${id}`, {
              method: "PATCH",
              body: JSON.stringify({ title, priority }),
            });
          }
          showToast("Updated");
          await loadTasks();
        } catch (e) {
          showToast(e.message || "Failed");
        }
      };
    });
  }

  async function loadEvents() {
    const data = await api("/api/events");
    const today = new Date().toISOString().slice(0, 10);
    const upcoming = data.events
      .filter((e) => e.date >= today)
      .sort((a, b) => (a.date + (a.startTime || "")).localeCompare(b.date + (b.startTime || "")));
    document.getElementById("eventList").innerHTML =
      upcoming.map(eventItem).join("") || empty("Nothing scheduled");
    bindEventActions();
  }

  function eventItem(e) {
    const when = e.allDay ? "All day" : [e.startTime, e.endTime].filter(Boolean).join("–") || "—";
    return `<div class="item">
      <div class="item-title">${esc(e.title)}</div>
      <div class="meta">${esc(e.date)} · ${esc(when)}${e.description ? " · " + esc(e.description) : ""}</div>
      <div class="actions">
        <button data-act="edit-event" data-id="${esc(e.id)}">Edit</button>
        <button class="danger" data-act="delete-event" data-id="${esc(e.id)}">Delete</button>
      </div>
    </div>`;
  }

  function bindEventActions() {
    document.querySelectorAll("#calendarPanel [data-act]").forEach((btn) => {
      btn.onclick = async () => {
        const id = btn.dataset.id;
        try {
          if (btn.dataset.act === "delete-event") {
            if (!confirm("Delete this event?")) return;
            await api(`/api/events/${id}`, { method: "DELETE" });
          }
          if (btn.dataset.act === "edit-event") {
            const title = prompt("Title");
            if (!title) return;
            const date = prompt("Date YYYY-MM-DD");
            if (!date) return;
            await api(`/api/events/${id}`, {
              method: "PATCH",
              body: JSON.stringify({ title, date }),
            });
          }
          showToast("Updated");
          await loadEvents();
        } catch (e) {
          showToast(e.message || "Failed");
        }
      };
    });
  }

  function thumbUrl(id) {
    return `/api/appearance/background/${encodeURIComponent(id)}/thumb`;
  }

  async function loadAppearance() {
    const data = await api("/api/appearance");
    const grid = document.getElementById("appearanceBgGrid");
    const items = [...(data.bundled || []), ...(data.custom || [])];
    grid.innerHTML = items.map((item) => {
      const selected = item.id === data.backgroundId ? "selected" : "";
      const del = item.id.startsWith("custom:")
        ? `<button type="button" class="ghost tiny" data-del-bg="${item.id}">Remove</button>`
        : "";
      return `<button type="button" class="bg-card ${selected}" data-bg="${item.id}">
        <img src="${thumbUrl(item.id)}" alt="" />
        <span>${item.label}</span>
        ${del}
      </button>`;
    }).join("");

    grid.querySelectorAll("[data-bg]").forEach((btn) => {
      btn.addEventListener("click", async (ev) => {
        if (ev.target.closest("[data-del-bg]")) return;
        try {
          await api("/api/appearance", {
            method: "POST",
            body: JSON.stringify({ backgroundId: btn.dataset.bg }),
          });
          showToast("Background updated");
          await loadAppearance();
        } catch (e) {
          showToast(e.message || "Failed");
        }
      });
    });
    grid.querySelectorAll("[data-del-bg]").forEach((btn) => {
      btn.addEventListener("click", async (ev) => {
        ev.stopPropagation();
        try {
          const id = btn.dataset.delBg;
          await api(`/api/appearance/background/${encodeURIComponent(id)}`, { method: "DELETE" });
          showToast("Removed");
          await loadAppearance();
        } catch (e) {
          showToast(e.message || "Failed");
        }
      });
    });

    const dimRow = document.getElementById("appearanceDimRow");
    dimRow.innerHTML = (data.dimOptions || [0, 20, 40, 60]).map((d) =>
      `<button type="button" class="choice ${d === data.dimPercent ? "selected" : ""}" data-dim="${d}">${d}%</button>`
    ).join("");
    dimRow.querySelectorAll("[data-dim]").forEach((btn) => {
      btn.onclick = async () => {
        await api("/api/appearance", {
          method: "POST",
          body: JSON.stringify({ dimPercent: Number(btn.dataset.dim) }),
        });
        showToast("Dim updated");
        await loadAppearance();
      };
    });

    const styleRow = document.getElementById("appearanceStyleRow");
    const labels = { glass: "Glass", solid: "Solid", minimal: "Minimal" };
    styleRow.innerHTML = (data.cardStyles || ["glass", "solid", "minimal"]).map((s) =>
      `<button type="button" class="choice ${s === data.cardStyle ? "selected" : ""}" data-style="${s}">${labels[s] || s}</button>`
    ).join("");
    styleRow.querySelectorAll("[data-style]").forEach((btn) => {
      btn.onclick = async () => {
        await api("/api/appearance", {
          method: "POST",
          body: JSON.stringify({ cardStyle: btn.dataset.style }),
        });
        showToast("Card style updated");
        await loadAppearance();
      };
    });
  }

  document.getElementById("appearanceUpload").addEventListener("click", async () => {
    const err = document.getElementById("appearanceUploadError");
    err.hidden = true;
    const input = document.getElementById("appearanceFile");
    const file = input.files && input.files[0];
    if (!file) {
      err.textContent = "Choose an image first";
      err.hidden = false;
      return;
    }
    try {
      const form = new FormData();
      form.append("file", file, file.name);
      const res = await fetch("/api/appearance/background", {
        method: "POST",
        credentials: "same-origin",
        body: form,
      });
      let data = null;
      try { data = await res.json(); } catch (_) {}
      if (!res.ok) throw new Error((data && data.error) || "upload_failed");
      input.value = "";
      showToast("Uploaded");
      await loadAppearance();
    } catch (e) {
      err.textContent = e.message || "Upload failed";
      err.hidden = false;
    }
  });

  document.getElementById("appearanceRestore").addEventListener("click", async () => {
    await api("/api/appearance", {
      method: "POST",
      body: JSON.stringify({ restoreDefault: true }),
    });
    showToast("Defaults restored");
    await loadAppearance();
  });

  async function refreshAll() {
    await Promise.all([loadTasks(), loadEvents()]);
  }

  // Probe existing session
  api("/api/tasks")
    .then(async () => {
      setAuthed(true);
      await refreshAll();
    })
    .catch(() => setAuthed(false));

  // Default event date = today
  document.getElementById("eventDate").value = new Date().toISOString().slice(0, 10);
})();
