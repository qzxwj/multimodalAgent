const state = {
  auth: { username: "student", password: "student123" },
  sessionId: null,
  sending: false,
  isAdmin: false,
  modelName: "multimodalAgent-qwen2.5-7b-ft:latest",
  latestReports: []
};

const $ = (selector) => document.querySelector(selector);

const els = {
  serviceState: $("#serviceState"),
  modelState: $("#modelState"),
  runtimeModel: $("#runtimeModel"),
  loginForm: $("#loginForm"),
  username: $("#username"),
  password: $("#password"),
  loginState: $("#loginState"),
  accountPanel: $("#accountPanel"),
  activeAccount: $("#activeAccount"),
  activeRole: $("#activeRole"),
  switchAccount: $("#switchAccount"),
  studentView: $("#studentView"),
  adminView: $("#adminView"),
  profileText: $("#profileText"),
  sessionBadge: $("#sessionBadge"),
  messages: $("#messages"),
  pipelineSteps: $("#pipelineSteps"),
  chatForm: $("#chatForm"),
  messageInput: $("#messageInput"),
  audioInput: $("#audioInput"),
  imageInput: $("#imageInput"),
  videoInput: $("#videoInput"),
  attachmentState: $("#attachmentState"),
  clearAttachments: $("#clearAttachments"),
  newSessionButton: $("#newSessionButton"),
  sendButton: $("#sendButton"),
  adminRefresh: $("#adminRefresh"),
  adminStats: $("#adminStats"),
  queueCount: $("#queueCount"),
  adminReportRows: $("#adminReportRows"),
  excelRows: $("#excelRows"),
  emailRows: $("#emailRows"),
  knowledgeUploadForm: $("#knowledgeUploadForm"),
  knowledgeFile: $("#knowledgeFile"),
  knowledgeUploadState: $("#knowledgeUploadState"),
  detailOverlay: $("#detailOverlay"),
  detailKicker: $("#detailKicker"),
  detailTitle: $("#detailTitle"),
  detailMeta: $("#detailMeta"),
  detailBody: $("#detailBody"),
  closeDetail: $("#closeDetail")
};

const pipeline = [
  ["input", "Receive input"],
  ["fusion", "Multimodal parsing"],
  ["router", "Route intent"],
  ["rag", "Retrieve support"],
  ["mcp", "Record loop"],
  ["stream", "Generate response"]
];

function authHeader() {
  return `Basic ${btoa(`${state.auth.username}:${state.auth.password}`)}`;
}

async function api(path, options = {}) {
  const headers = { Authorization: authHeader(), ...(options.headers || {}) };
  const response = await fetch(path, { ...options, headers });
  if (!response.ok) {
    throw new Error(await response.text() || `${response.status} ${response.statusText}`);
  }
  return response;
}

function tone(element, value) {
  element.classList.remove("ok", "warn", "danger", "active");
  if (value) element.classList.add(value);
}

function setService(text, value) {
  els.serviceState.textContent = text;
  tone(els.serviceState, value);
}

function displayModelName(model) {
  return (model || "").includes("multimodalAgent-qwen2.5-7b-ft") ? "Fine-tuned Qwen2.5-7B" : (model || "Unknown model");
}

function setModel(status) {
  state.modelName = status.model || state.modelName;
  const label = status.realModelEnabled ? `${status.provider} / ${displayModelName(state.modelName)}` : "mock / offline demo";
  els.modelState.textContent = label;
  els.runtimeModel.textContent = displayModelName(state.modelName);
  tone(els.modelState, status.realModelEnabled ? "ok" : "warn");
}

function selectedFiles() {
  return [
    ["audio", "Audio", els.audioInput.files?.[0]],
    ["image", "Image", els.imageInput.files?.[0]],
    ["video", "Video", els.videoInput.files?.[0]]
  ].filter(([, , file]) => file);
}

function updateAttachments() {
  const files = selectedFiles();
  els.clearAttachments.hidden = files.length === 0;
  els.attachmentState.textContent = files.length
    ? files.map(([, label, file]) => `${label} / ${file.name}`).join("    ")
    : "No attachments";
  els.attachmentState.classList.toggle("active", files.length > 0);
}

function clearAttachments() {
  els.audioInput.value = "";
  els.imageInput.value = "";
  els.videoInput.value = "";
  updateAttachments();
}

function renderPipeline(activeKey = "") {
  els.pipelineSteps.innerHTML = "";
  pipeline.forEach(([key, label], index) => {
    const item = document.createElement("div");
    item.className = `pipeline-step ${key === activeKey ? "active" : ""}`;
    item.innerHTML = `<span>${String(index + 1).padStart(2, "0")}</span><strong>${label}</strong>`;
    els.pipelineSteps.append(item);
  });
}

function setSession(text, value) {
  const labels = {
    READY: "Ready",
    RUNNING: "Responding",
    FAILED: "Retry needed"
  };
  els.sessionBadge.textContent = labels[text] || text;
  tone(els.sessionBadge, value);
}

function addMessage(role, content = "") {
  const card = document.createElement("article");
  card.className = `message-card ${role}`;
  card.dataset.raw = content;
  const label = role === "user" ? "You said" : displayModelName(state.modelName);
  card.innerHTML = `<header><span>${label}</span></header><div class="message-content"></div>`;
  card.querySelector(".message-content").textContent = content;
  els.messages.append(card);
  els.messages.scrollTop = els.messages.scrollHeight;
  return card;
}

function updateAssistant(card, text) {
  card.dataset.raw = text;
  card.querySelector(".message-content").textContent = text;
  els.messages.scrollTop = els.messages.scrollHeight;
}

function renderEmptyConversation() {
  els.messages.innerHTML = `
    <section class="empty-state">
      <p class="kicker">Ready</p>
      <h2>You can start with one sentence</h2>
      <p>This space can help you sort through stress, low mood, sleep difficulties, and study planning. You can also add audio, images, or video so the system can understand the situation more fully.</p>
    </section>
  `;
}

function clearEmpty() {
  els.messages.querySelector(".empty-state")?.remove();
}

function startNewSession() {
  state.sessionId = null;
  clearAttachments();
  renderPipeline();
  setSession("READY");
  renderEmptyConversation();
  els.messageInput.focus();
}

function parseSse(buffer, onEvent) {
  const blocks = buffer.split("\n\n");
  const rest = blocks.pop() || "";
  for (const block of blocks) {
    const data = block.split("\n").find((line) => line.startsWith("data:"));
    if (data) onEvent(JSON.parse(data.slice(5)));
  }
  return rest;
}

async function sendChat(event) {
  event.preventDefault();
  if (state.sending || state.isAdmin) return;
  const message = els.messageInput.value.trim();
  const files = selectedFiles();
  if (!message && !files.length) return;

  state.sending = true;
  els.sendButton.disabled = true;
  els.messageInput.value = "";
  clearEmpty();
  setSession("RUNNING", "warn");
  renderPipeline("input");

  const visibleInput = [
    message || "The student uploaded multimodal content",
    ...files.map(([, label, file]) => `${label}: ${file.name}`)
  ].join("\n");
  addMessage("user", visibleInput);
  const assistant = addMessage("assistant", "");

  try {
    const response = files.length ? await sendMultimodal(message, files) : await sendText(message);
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    let output = "";
    renderPipeline(files.length ? "fusion" : "router");

    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      buffer = parseSse(buffer, (eventData) => {
        if (eventData.type === "meta") {
          state.sessionId = eventData.sessionId;
          renderPipeline("rag");
        }
        if (eventData.type === "token") {
          output += eventData.content;
          updateAssistant(assistant, output);
          renderPipeline("stream");
        }
        if (eventData.type === "error") {
          output = eventData.content || "The model did not return content yet.";
          updateAssistant(assistant, output);
          renderPipeline("stream");
        }
      });
    }

    if (!output) updateAssistant(assistant, "The model did not return content yet.");
    renderPipeline("mcp");
    setTimeout(() => renderPipeline("stream"), 280);
    setSession("READY", "ok");
  } catch (error) {
    updateAssistant(assistant, "Request failed. Please make sure the backend service is running.");
    setSession("FAILED", "danger");
  } finally {
    state.sending = false;
    els.sendButton.disabled = false;
    clearAttachments();
    els.messageInput.focus();
  }
}

function sendText(message) {
  return api("/api/chat/stream", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ sessionId: state.sessionId, message })
  });
}

function sendMultimodal(message, files) {
  const body = new FormData();
  body.append("message", message || "The student uploaded multimodal content and would like support.");
  if (state.sessionId) body.append("sessionId", state.sessionId);
  files.forEach(([key, , file]) => body.append(key, file));
  return api("/api/chat/multimodal/stream", { method: "POST", body });
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : "";
}

function riskTone(risk) {
  if (risk === "HIGH" || risk === "FAILED") return "danger";
  if (risk === "MEDIUM" || risk === "PENDING") return "warn";
  if (risk === "LOW" || risk === "SUCCESS") return "ok";
  return "";
}

function statCard(label, value, kind) {
  const node = document.createElement("article");
  node.className = `stat-card ${kind || ""}`;
  node.innerHTML = `<strong>${value}</strong><span>${label}</span>`;
  return node;
}

function renderAdminStats(reports, excelRecords, alerts) {
  els.adminStats.innerHTML = "";
  const high = reports.filter((item) => item.riskLevel === "HIGH").length;
  const medium = reports.filter((item) => item.riskLevel === "MEDIUM").length;
  const mailFailed = alerts.filter((item) => item.status === "FAILED").length;
  els.queueCount.textContent = high;
  els.adminStats.append(
    statCard("Total reports", reports.length),
    statCard("High risk", high, "danger"),
    statCard("Needs attention", medium, "warn"),
    statCard("Alert failures", mailFailed, mailFailed ? "danger" : "ok"),
    statCard("Excel records", excelRecords.length, "ok")
  );
}

function emptyRecord(text) {
  const node = document.createElement("p");
  node.className = "empty-record";
  node.textContent = text;
  return node;
}

function recordButton(title, badge, meta, summary, onClick) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "record-card";
  button.innerHTML = `
    <div><strong>${escapeHtml(title)}</strong><span class="${riskTone(badge)}">${escapeHtml(badge || "SKIPPED")}</span></div>
    <small>${escapeHtml(meta || "")}</small>
    <p>${escapeHtml(summary || "No summary")}</p>
  `;
  button.addEventListener("click", onClick);
  return button;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function renderReportRows(reports) {
  els.adminReportRows.innerHTML = "";
  if (!reports.length) {
    els.adminReportRows.append(emptyRecord("No risk records"));
    return;
  }
  reports.slice(0, 24).forEach((item) => {
    els.adminReportRows.append(recordButton(
      `${item.username} / ${item.emotion}`,
      item.riskLevel,
      `${item.intent} · ${formatDate(item.createdAt)}`,
      item.summary,
      () => item.sessionId ? openConversation(item) : openRecord("Report detail", item)
    ));
  });
}

function renderExcelRows(records) {
  els.excelRows.innerHTML = "";
  if (!records.length) {
    els.excelRows.append(emptyRecord("No Excel records"));
    return;
  }
  records.slice(0, 24).forEach((item) => {
    els.excelRows.append(recordButton(
      `#${item.reportId} / ${item.username}`,
      item.excelStatus,
      `${item.emotion} · ${item.riskLevel} · ${formatDate(item.createdAt)}`,
      item.summary || item.content,
      () => openRecord("Excel record", item)
    ));
  });
}

function renderEmailRows(records) {
  els.emailRows.innerHTML = "";
  if (!records.length) {
    els.emailRows.append(emptyRecord("No alert records"));
    return;
  }
  records.slice(0, 24).forEach((item) => {
    els.emailRows.append(recordButton(
      `#${item.reportId} / ${item.recipient}`,
      item.status,
      `${item.riskLevel} · ${item.attempts} attempts · ${formatDate(item.updatedAt)}`,
      item.errorMessage || item.summary,
      () => openRecord("Alert delivery", item)
    ));
  });
}

function detailRow(label, value) {
  const row = document.createElement("div");
  row.className = "detail-row";
  row.innerHTML = `<span>${escapeHtml(label)}</span><strong>${escapeHtml(value ?? "N/A")}</strong>`;
  return row;
}

function openRecord(title, record) {
  els.detailOverlay.hidden = false;
  els.detailKicker.textContent = "Record detail";
  els.detailTitle.textContent = title;
  els.detailMeta.textContent = formatDate(record.createdAt || record.updatedAt);
  els.detailBody.innerHTML = "";
  Object.entries(record).forEach(([key, value]) => {
    if (value !== null && value !== undefined && typeof value !== "object") {
      els.detailBody.append(detailRow(key, value));
    }
  });
}

async function openConversation(report) {
  els.detailOverlay.hidden = false;
  els.detailKicker.textContent = `${report.username} / ${report.sessionId}`;
  els.detailTitle.textContent = "Full conversation";
  els.detailMeta.textContent = "Counselor view";
  els.detailBody.innerHTML = `<p class="empty-record">Loading...</p>`;
  try {
    const response = await api(`/api/admin/conversations/${encodeURIComponent(report.sessionId)}`);
    const data = await response.json();
    els.detailBody.innerHTML = "";
    data.messages.forEach((message) => {
      const card = document.createElement("article");
      card.className = `conversation-card ${message.role.toLowerCase()}`;
      card.innerHTML = `<header><strong>${message.role}</strong><span>${formatDate(message.createdAt)}</span></header><p>${escapeHtml(message.content)}</p>`;
      els.detailBody.append(card);
    });
  } catch (error) {
    els.detailBody.innerHTML = `<p class="empty-record">Failed to load</p>`;
  }
}

function closeDetail() {
  els.detailOverlay.hidden = true;
}

async function loadReports() {
  const response = await api("/api/admin/reports");
  return response.json();
}

async function loadExcelRecords() {
  const response = await api("/api/admin/excel-records");
  return response.json();
}

async function loadAlertRecords() {
  const response = await api("/api/admin/alerts");
  return response.json();
}

async function loadAdminData() {
  const [reports, excelRecords, alerts] = await Promise.all([
    loadReports(),
    loadExcelRecords(),
    loadAlertRecords()
  ]);
  state.latestReports = reports;
  renderAdminStats(reports, excelRecords, alerts);
  renderReportRows(reports);
  renderExcelRows(excelRecords);
  renderEmailRows(alerts);
}

async function uploadKnowledge(event) {
  event.preventDefault();
  const file = els.knowledgeFile.files?.[0];
  if (!file) {
    els.knowledgeUploadState.textContent = "Please choose a file";
    return;
  }
  const body = new FormData();
  body.append("file", file);
  els.knowledgeUploadState.textContent = "Ingesting";
  try {
    const response = await api("/api/admin/knowledge/file", { method: "POST", body });
    const data = await response.json();
    els.knowledgeUploadState.textContent = `${data.source} / ${data.chunks} chunks`;
    els.knowledgeFile.value = "";
  } catch (error) {
    els.knowledgeUploadState.textContent = "Ingestion failed";
  }
}

function showLoggedOut() {
  state.isAdmin = false;
  els.loginForm.hidden = false;
  els.accountPanel.hidden = true;
  els.studentView.hidden = false;
  els.adminView.hidden = true;
  renderEmptyConversation();
  renderPipeline();
}

function isAdmin(profile) {
  return profile.roles?.some((role) => role.authority === "ROLE_ADMIN");
}

async function loadProfile() {
  const response = await api("/api/profile");
  const profile = await response.json();
  state.isAdmin = isAdmin(profile);
  const accountName = state.isAdmin ? (profile.displayName || profile.username) : profile.username;
  els.loginForm.hidden = true;
  els.accountPanel.hidden = false;
  els.activeAccount.textContent = accountName;
  els.activeRole.textContent = state.isAdmin ? "Counselor account" : "Student account";

  if (state.isAdmin) {
    els.studentView.hidden = true;
    els.adminView.hidden = false;
    await loadAdminData();
  } else {
    els.studentView.hidden = false;
    els.adminView.hidden = true;
    els.profileText.textContent = profile.username;
  }
  els.loginState.textContent = "Signed in";
}

async function loadAgentStatus() {
  const response = await api("/api/agent/status");
  setModel(await response.json());
}

async function checkHealth() {
  try {
    const response = await fetch("/actuator/health");
    const body = await response.json();
    setService(body.status === "UP" ? "Service online" : `Service ${body.status}`, body.status === "UP" ? "ok" : "warn");
  } catch (error) {
    setService("Service unavailable", "danger");
  }
}

async function login(event) {
  event?.preventDefault();
  state.auth.username = els.username.value.trim();
  state.auth.password = els.password.value;
  try {
    await loadProfile();
    await loadAgentStatus();
  } catch (error) {
    showLoggedOut();
    els.loginState.textContent = "Invalid username or password";
  }
}

els.loginForm.addEventListener("submit", login);
els.switchAccount.addEventListener("click", () => {
  showLoggedOut();
  els.username.focus();
});
els.chatForm.addEventListener("submit", sendChat);
els.audioInput.addEventListener("change", updateAttachments);
els.imageInput.addEventListener("change", updateAttachments);
els.videoInput.addEventListener("change", updateAttachments);
els.clearAttachments.addEventListener("click", clearAttachments);
els.newSessionButton.addEventListener("click", startNewSession);
els.adminRefresh.addEventListener("click", loadAdminData);
els.knowledgeUploadForm.addEventListener("submit", uploadKnowledge);
els.closeDetail.addEventListener("click", closeDetail);
els.detailOverlay.addEventListener("click", (event) => {
  if (event.target === els.detailOverlay) closeDetail();
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !els.detailOverlay.hidden) closeDetail();
});
document.addEventListener("click", (event) => {
  const prompt = event.target.closest("[data-prompt]");
  if (prompt && !state.isAdmin) {
    els.messageInput.value = prompt.dataset.prompt;
    els.messageInput.focus();
  }
});

checkHealth();
renderPipeline();
renderEmptyConversation();
login();
