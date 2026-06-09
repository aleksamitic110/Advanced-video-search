const DEFAULT_BACKEND = "http://127.0.0.1:8000";

const state = {
  backendUrl: localStorage.getItem("backendUrl") || DEFAULT_BACKEND,
  apiKeyConfigured: false
};

document.addEventListener("DOMContentLoaded", async () => {
  document.getElementById("backendUrl").value = state.backendUrl;
  bindTabs();
  bindActions();
  await checkBackend();
  await loadBackendConfig();
  await refreshVideos();
});

function bindTabs() {
  document.querySelectorAll(".tab").forEach((button) => {
    button.addEventListener("click", () => {
      document.querySelectorAll(".tab").forEach((tab) => tab.classList.remove("active"));
      document.querySelectorAll(".workspace").forEach((panel) => panel.classList.remove("active"));
      button.classList.add("active");
      document.getElementById(`tab-${button.dataset.tab}`).classList.add("active");
    });
  });
}

function bindActions() {
  document.getElementById("saveConfig").addEventListener("click", saveConfig);
  document.getElementById("importVideos").addEventListener("click", importVideos);
  document.getElementById("textSearch").addEventListener("click", textSearch);
  document.getElementById("imageSearch").addEventListener("click", imageSearch);
  document.getElementById("semanticSearch").addEventListener("click", semanticSearch);
  document.getElementById("hybridSearch").addEventListener("click", hybridSearch);
  document.getElementById("refreshVideos").addEventListener("click", refreshVideos);
  document.getElementById("videoList").addEventListener("click", deleteVideo);
  document.getElementById("results").addEventListener("click", toggleFullText);
}

async function checkBackend() {
  state.backendUrl = normalizeBackendUrl(document.getElementById("backendUrl").value);
  try {
    await apiGet("/api/health");
    document.getElementById("healthBadge").textContent = "online";
    setStatus(`Backend connected: ${state.backendUrl}`);
  } catch (error) {
    document.getElementById("healthBadge").textContent = "offline";
    setStatus(`Backend unavailable at ${state.backendUrl}`, true);
  }
}

async function loadBackendConfig() {
  try {
    const config = await apiGet("/api/config");
    state.apiKeyConfigured = Boolean(config.youtube_api_key_configured);
    renderApiKeyState();
    document.getElementById("frameInterval").value = config.frame_interval_seconds || 8;
    document.getElementById("enableYtdlp").checked = Boolean(config.enable_ytdlp);
  } catch {
    // Health status already reports backend availability.
    state.apiKeyConfigured = false;
    renderApiKeyState();
  }
}

async function saveConfig() {
  state.backendUrl = normalizeBackendUrl(document.getElementById("backendUrl").value);
  localStorage.setItem("backendUrl", state.backendUrl);

  const payload = {
    frame_interval_seconds: Number(document.getElementById("frameInterval").value || 8),
    enable_ytdlp: document.getElementById("enableYtdlp").checked
  };
  const apiKey = document.getElementById("youtubeApiKey").value.trim();
  if (apiKey) {
    payload.youtube_api_key = apiKey;
  }

  try {
    await apiPostJson("/api/config", payload);
    document.getElementById("youtubeApiKey").value = "";
    await checkBackend();
    await loadBackendConfig();
    setStatus("Configuration saved.");
  } catch (error) {
    setStatus(error.message, true);
  }
}

async function refreshVideos() {
  try {
    const data = await apiGet("/api/videos");
    renderVideos(data.videos || []);
  } catch (error) {
    setStatus(error.message, true);
  }
}

async function importVideos() {
  const urls = document.getElementById("videoUrls").value
    .split(/\s+/)
    .map((item) => item.trim())
    .filter(Boolean);
  if (!urls.length) {
    setStatus("Paste at least one YouTube URL.", true);
    return;
  }
  await loadBackendConfig();
  if (!state.apiKeyConfigured) {
    setStatus("Save a YouTube API key first. Without it the backend can only index fallback title text and frames.", true);
    return;
  }
  setStatus("Importing videos. This can take time when frames are enabled...");
  try {
    const data = await apiPostJson("/api/videos/import", { urls });
    renderResults((data.results || []).map((result) => ({
      title: result.video_id || result.url,
      source_type: result.status,
      snippet: result.error || "Imported and indexed.",
      score: 0,
      youtube_timestamp_url: result.url,
      thumbnail_url: ""
    })));
    await refreshVideos();
    setStatus("Import finished.");
  } catch (error) {
    setStatus(error.message, true);
  }
}

function renderApiKeyState() {
  const badge = document.getElementById("apiKeyState");
  if (!badge) {
    return;
  }
  badge.textContent = state.apiKeyConfigured ? "saved" : "not saved";
  badge.classList.toggle("missing", !state.apiKeyConfigured);
}

async function textSearch() {
  const query = document.getElementById("textQuery").value.trim();
  const fields = [...document.querySelectorAll(".checks input:checked")].map((item) => item.value);
  if (!query) {
    setStatus("Enter a text query.", true);
    return;
  }
  try {
    const data = await apiPostJson("/api/search/text", { query, fields, limit: 20 });
    renderResults(data.results || []);
    setStatus(`Text search returned ${(data.results || []).length} results.`);
  } catch (error) {
    setStatus(error.message, true);
  }
}

async function imageSearch() {
  const file = document.getElementById("imageQuery").files[0];
  if (!file) {
    setStatus("Choose an image first.", true);
    return;
  }
  const form = new FormData();
  form.append("image", file);
  form.append("limit", "20");
  try {
    const data = await apiPostForm("/api/search/image", form);
    renderResults(data.results || []);
    setStatus(`Image search returned ${(data.results || []).length} results.`);
  } catch (error) {
    setStatus(error.message, true);
  }
}

async function semanticSearch() {
  const query = document.getElementById("semanticQuery").value.trim();
  if (!query) {
    setStatus("Enter a visual semantic query.", true);
    return;
  }
  try {
    const data = await apiPostJson("/api/search/visual-semantic", { query, limit: 20 });
    renderResults(data.results || []);
    setStatus(`Visual semantic search returned ${(data.results || []).length} results.`);
  } catch (error) {
    setStatus(error.message, true);
  }
}

async function hybridSearch() {
  const query = document.getElementById("hybridQuery").value.trim();
  const file = document.getElementById("hybridImage").files[0];
  if (!query && !file) {
    setStatus("Hybrid search needs text or image.", true);
    return;
  }
  const form = new FormData();
  form.append("query", query);
  form.append("limit", "20");
  form.append("text_weight", document.getElementById("textWeight").value);
  form.append("vector_weight", document.getElementById("vectorWeight").value);
  if (file) {
    form.append("image", file);
  }
  try {
    const data = await apiPostForm("/api/search/hybrid", form);
    renderResults(data.results || []);
    setStatus(`Hybrid search returned ${(data.results || []).length} results.`);
  } catch (error) {
    setStatus(error.message, true);
  }
}

function renderVideos(videos) {
  const container = document.getElementById("videoList");
  if (!videos.length) {
    container.innerHTML = `<p class="empty">No indexed videos yet.</p>`;
    return;
  }
  container.innerHTML = videos.map((video) => `
    <article class="video-item">
      <div class="video-row">
        <h3>${escapeHtml(video.title || video.video_id)}</h3>
        <button class="delete-video" type="button" data-video-id="${escapeAttribute(video.video_id)}">Delete</button>
      </div>
      <p class="meta">${escapeHtml(video.status)} | text ${video.text_doc_count || 0} | comments ${video.comment_count} | transcripts ${video.transcript_count} | frames ${video.frame_count}</p>
      ${video.error ? `<p class="readable">${escapeHtml(video.error)}</p>` : ""}
    </article>
  `).join("");
}

async function deleteVideo(event) {
  const button = event.target.closest(".delete-video");
  if (!button) {
    return;
  }
  const videoId = button.dataset.videoId;
  button.disabled = true;
  try {
    await apiDelete(`/api/videos/${encodeURIComponent(videoId)}`);
    await refreshVideos();
    setStatus(`Deleted ${videoId}.`);
  } catch (error) {
    button.disabled = false;
    setStatus(error.message, true);
  }
}

function renderResults(results) {
  const container = document.getElementById("results");
  document.getElementById("resultCount").textContent = String(results.length);
  if (!results.length) {
    container.innerHTML = `<p class="empty">No results.</p>`;
    return;
  }
  container.innerHTML = results.map((result) => {
    const imageUrl = result.frame_url
      ? `${state.backendUrl}${result.frame_url}`
      : (result.thumbnail_url || "");
    const timestamp = result.timestamp_seconds == null ? "video" : `${result.timestamp_seconds}s`;
    const fullText = result.full_text || result.snippet || "";
    const isLong = fullText.length > 520;
    const readableText = result.source_type === "comment" ? fullText : (result.snippet || fullText);
    return `
      <article class="result">
        ${imageUrl ? `<img class="thumb" src="${escapeAttribute(imageUrl)}" alt="">` : `<div class="thumb"></div>`}
        <div>
          <h3>${escapeHtml(result.title || result.video_id || "Result")}</h3>
          <p class="meta">${escapeHtml(formatSource(result))} | ${timestamp} | score ${Number(result.score || 0).toFixed(3)}</p>
          <p class="readable ${isLong ? "collapsed" : ""}">${highlightTerms(readableText, result.matched_terms || [])}</p>
          ${isLong ? `<button class="toggle-snippet" type="button">Show full text</button>` : ""}
          ${result.youtube_timestamp_url ? `<a class="open-link" href="${escapeAttribute(result.youtube_timestamp_url)}" target="_blank">Open in YouTube</a>` : ""}
        </div>
      </article>
    `;
  }).join("");
}

function formatSource(result) {
  if (result.source_type === "comment") {
    const number = result.source_id ? Number(result.source_id) + 1 : "";
    return number ? `comment #${number}` : "comment";
  }
  if (result.source_type === "metadata") {
    return "title";
  }
  if (result.source_type === "visual_semantic") {
    return "visual semantic";
  }
  return result.source_type || "match";
}

function toggleFullText(event) {
  const button = event.target.closest(".toggle-snippet");
  if (!button) {
    return;
  }
  const text = button.previousElementSibling;
  const collapsed = text.classList.toggle("collapsed");
  button.textContent = collapsed ? "Show full text" : "Show less";
}

async function apiGet(path) {
  return parseResponse(await fetch(`${state.backendUrl}${path}`));
}

async function apiPostJson(path, body) {
  return parseResponse(await fetch(`${state.backendUrl}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  }));
}

async function apiPostForm(path, form) {
  return parseResponse(await fetch(`${state.backendUrl}${path}`, {
    method: "POST",
    body: form
  }));
}

async function apiDelete(path) {
  return parseResponse(await fetch(`${state.backendUrl}${path}`, {
    method: "DELETE"
  }));
}

async function parseResponse(response) {
  const text = await response.text();
  const data = text ? JSON.parse(text) : {};
  if (!response.ok) {
    throw new Error(data.detail || response.statusText);
  }
  return data;
}

function normalizeBackendUrl(value) {
  return (value || DEFAULT_BACKEND).replace(/\/$/, "");
}

function setStatus(message, isError = false) {
  const status = document.getElementById("status");
  status.textContent = message;
  status.classList.toggle("error", isError);
}

function highlightTerms(text, terms) {
  const escaped = escapeHtml(text || "");
  const uniqueTerms = [...new Set((terms || []).filter(Boolean))]
    .sort((left, right) => right.length - left.length);
  if (!uniqueTerms.length) {
    return escaped;
  }
  const pattern = uniqueTerms.map(escapeRegExp).join("|");
  return escaped.replace(new RegExp(`\\b(${pattern})\\b`, "gi"), "<mark>$1</mark>");
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (char) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#039;"
  })[char]);
}

function escapeAttribute(value) {
  return escapeHtml(value).replace(/`/g, "&#096;");
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
