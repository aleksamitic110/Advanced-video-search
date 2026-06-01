const DEFAULT_BACKEND = "http://127.0.0.1:8000";

document.addEventListener("DOMContentLoaded", async () => {
  const stored = await chrome.storage.sync.get(["backendUrl"]);
  document.getElementById("backendUrl").value = stored.backendUrl || DEFAULT_BACKEND;
  await loadBackendConfig();
  document.getElementById("optionsForm").addEventListener("submit", saveSettings);
});

async function loadBackendConfig() {
  const backendUrl = document.getElementById("backendUrl").value.replace(/\/$/, "") || DEFAULT_BACKEND;
  try {
    const response = await fetch(`${backendUrl}/api/config`);
    if (!response.ok) {
      return;
    }
    const config = await response.json();
    document.getElementById("frameInterval").value = config.frame_interval_seconds || 8;
    document.getElementById("enableYtdlp").checked = Boolean(config.enable_ytdlp);
    setMessage(config.youtube_api_key_configured ? "Backend has a YouTube API key configured." : "");
  } catch (error) {
    setMessage("Backend is not running yet.");
  }
}

async function saveSettings(event) {
  event.preventDefault();
  const backendUrl = document.getElementById("backendUrl").value.replace(/\/$/, "") || DEFAULT_BACKEND;
  const youtubeApiKey = document.getElementById("youtubeApiKey").value.trim();
  const frameInterval = Number(document.getElementById("frameInterval").value || 8);
  const enableYtdlp = document.getElementById("enableYtdlp").checked;

  await chrome.storage.sync.set({ backendUrl });

  const payload = {
    frame_interval_seconds: frameInterval,
    enable_ytdlp: enableYtdlp
  };
  if (youtubeApiKey) {
    payload.youtube_api_key = youtubeApiKey;
  }

  try {
    const response = await fetch(`${backendUrl}/api/config`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (!response.ok) {
      const data = await response.json();
      throw new Error(data.detail || response.statusText);
    }
    setMessage("Settings saved.");
    document.getElementById("youtubeApiKey").value = "";
  } catch (error) {
    setMessage(`Saved extension URL, but backend config failed: ${error.message}`);
  }
}

function setMessage(message) {
  document.getElementById("message").textContent = message;
}
