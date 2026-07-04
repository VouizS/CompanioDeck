const STORAGE_GAMES = "companionDeckGamesV2";
const STORAGE_PROFILE = "companionDeckProfile";

const legalNotice = `O Companion Deck não fornece jogos, ROMs, ISOs ou BIOS.
O usuário deve adicionar apenas arquivos que possui legalmente.
As capas são adicionadas manualmente pelo jogador e ficam salvas apenas como referência local.
O Code Deck é voltado a jogos offline, treino e formatos compatíveis.
O app não injeta código, não altera memória de outros aplicativos e não oferece suporte a cheats online.`;

const platformOptions = [
  { id: "gba", label: "Game Boy Advance", exts: ".gba" },
  { id: "snes", label: "SNES", exts: ".sfc, .smc" },
  { id: "gbc", label: "Game Boy / Color", exts: ".gb, .gbc" },
  { id: "megadrive", label: "Mega Drive", exts: ".md, .gen, .smd" },
  { id: "psp", label: "Portátil PSP", exts: ".iso, .cso" },
  { id: "ps1", label: "PS1", exts: ".bin, .cue, .chd" },
  { id: "n64", label: "Nintendo 64", exts: ".z64, .n64, .v64" },
  { id: "cubewii", label: "Cube/Wii", exts: ".iso, .gcm, .wbfs, .rvz" },
  { id: "ps2", label: "Console PS2", exts: ".iso, .chd" },
  { id: "manual", label: "Escolher depois", exts: "manual" }
];

const fallbackConsoles = [
  { id: "gba", name: "Game Boy Advance", tier: "Leve", status: "planejado", extensions: ".gba", description: "Primeiro candidato para motor interno." },
  { id: "snes", name: "SNES", tier: "Leve", status: "planejado", extensions: ".sfc, .smc", description: "Sistema clássico, bom para modo Lite." },
  { id: "psp", name: "Portátil PSP", tier: "Avançado", status: "futuro", extensions: ".iso, .cso", description: "Meta futura com motor/core compatível." }
];

let draftGame = null;

function native() {
  return window.CompanionDeckNative || null;
}

function showToast(message) {
  const bridge = native();
  if (bridge && bridge.toast) bridge.toast(message);
  else alert(message);
}

function copyText(label, text) {
  const bridge = native();
  if (bridge && bridge.copyToClipboard) {
    bridge.copyToClipboard(label, text);
    return;
  }
  navigator.clipboard?.writeText(text).then(() => alert("Copiado."));
}

function openUrl(url) {
  const bridge = native();
  if (bridge && bridge.openOfficialUrl) bridge.openOfficialUrl(url);
  else window.open(url, "_blank");
}

async function loadJson(path, fallback) {
  try {
    const response = await fetch(path);
    if (!response.ok) throw new Error("Falha ao carregar " + path);
    return await response.json();
  } catch (error) {
    return fallback;
  }
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function normalizeGuess(guess) {
  if (!guess) return "manual";
  if (guess === "iso-manual" || guess === "archive-manual" || guess === "ps1-manual") return "manual";
  return guess;
}

function platformLabel(id) {
  const item = platformOptions.find((platform) => platform.id === id);
  return item ? item.label : "Manual";
}

function cleanGameName(fileName) {
  return String(fileName || "Jogo selecionado")
    .replace(/\.[^/.]+$/, "")
    .replace(/[_-]+/g, " ")
    .replace(/\s+/g, " ")
    .trim() || "Jogo selecionado";
}

function initials(name) {
  const words = String(name || "CD").replace(/[^a-zA-Z0-9À-ÿ ]/g, " ").trim().split(/\s+/).filter(Boolean);
  if (!words.length) return "CD";
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[1][0]).toUpperCase();
}

function getGames() {
  try {
    const raw = localStorage.getItem(STORAGE_GAMES);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch (error) {
    return [];
  }
}

function saveGames(games) {
  localStorage.setItem(STORAGE_GAMES, JSON.stringify(games));
}

function addOrUpdateGame(game) {
  const games = getGames();
  const index = games.findIndex((item) => item.id === game.id);
  if (index >= 0) games[index] = game;
  else games.unshift(game);
  saveGames(games);
  renderGames();
}

function renderGames() {
  const games = getGames();
  const grid = document.querySelector("#gameGrid");
  const empty = document.querySelector("#emptyGames");
  grid.innerHTML = "";
  empty.classList.toggle("hidden", games.length > 0);

  games.forEach((game) => {
    const card = document.createElement("article");
    card.className = "game-card";

    const cover = game.coverUri
      ? `<img src="${escapeHtml(game.coverUri)}" alt="Capa de ${escapeHtml(game.name)}">`
      : `<span>${escapeHtml(initials(game.name))}</span>`;

    card.innerHTML = `
      <div class="cover-box">${cover}</div>
      <div class="game-info">
        <div class="game-meta">${escapeHtml(platformLabel(game.platform))}</div>
        <div class="game-title">${escapeHtml(game.name)}</div>
        <div class="game-file">${escapeHtml(game.fileName || "Arquivo selecionado")}</div>
        <div class="game-actions">
          <button class="mini-button" data-action="cover" data-id="${escapeHtml(game.id)}">Adicionar capa</button>
          <button class="mini-button" data-action="details" data-id="${escapeHtml(game.id)}">Detalhes</button>
          <button class="mini-button danger" data-action="remove" data-id="${escapeHtml(game.id)}">Remover</button>
        </div>
      </div>
    `;

    grid.appendChild(card);
  });

  grid.querySelectorAll("[data-action]").forEach((button) => {
    button.addEventListener("click", () => handleGameAction(button.dataset.action, button.dataset.id));
  });
}

function handleGameAction(action, gameId) {
  const games = getGames();
  const game = games.find((item) => item.id === gameId);
  if (!game) return;

  if (action === "cover") {
    const bridge = native();
    if (bridge && bridge.pickCover) bridge.pickCover(game.id);
    else showToast("Seletor de capa indisponível nesta visualização.");
    return;
  }

  if (action === "details") {
    showToast(`${game.name} • ${platformLabel(game.platform)} • motor futuro`);
    return;
  }

  if (action === "remove") {
    if (!confirm(`Remover "${game.name}" de Meus Jogos?`)) return;
    saveGames(games.filter((item) => item.id !== gameId));
    renderGames();
    showToast("Jogo removido.");
  }
}

function renderConsoles(consoles) {
  const grid = document.querySelector("#consoleGrid");
  grid.innerHTML = "";

  consoles.forEach((item) => {
    const card = document.createElement("article");
    card.className = "console-card";
    card.innerHTML = `
      <div>
        <span>${escapeHtml(item.extensions || "manual")}</span>
        <strong>${escapeHtml(item.name)}</strong>
        <p>${escapeHtml(item.description)}</p>
      </div>
      <em class="badge">${escapeHtml(item.tier)}</em>
    `;
    card.addEventListener("click", () => {
      showToast(`${item.name}: motor ${item.status}.`);
    });
    grid.appendChild(card);
  });
}

function renderCodes(codes) {
  const list = document.querySelector("#codeList");
  list.innerHTML = "";

  codes.forEach((item) => {
    const card = document.createElement("article");
    card.className = "info-card";
    card.innerHTML = `
      <h3>${escapeHtml(item.title)}</h3>
      <p><strong>${escapeHtml(item.game)}</strong> • ${escapeHtml(item.format)}<br>${escapeHtml(item.description)}</p>
      <button class="link-button">Copiar exemplo</button>
    `;
    card.querySelector("button").addEventListener("click", () => {
      copyText(item.title, item.code);
    });
    list.appendChild(card);
  });
}

function setupTabs() {
  document.querySelectorAll(".tab").forEach((tab) => {
    tab.addEventListener("click", () => {
      document.querySelectorAll(".tab").forEach((item) => item.classList.remove("active"));
      document.querySelectorAll(".tab-panel").forEach((panel) => panel.classList.remove("active"));
      tab.classList.add("active");
      document.querySelector(`#tab-${tab.dataset.tab}`)?.classList.add("active");
    });
  });
}

function openTab(name) {
  const tab = document.querySelector(`.tab[data-tab="${name}"]`);
  if (tab) tab.click();
}

function setupProfiles() {
  document.querySelectorAll(".status-card").forEach((card) => {
    card.addEventListener("click", () => {
      document.querySelectorAll(".status-card").forEach((item) => item.classList.remove("active"));
      card.classList.add("active");
      localStorage.setItem(STORAGE_PROFILE, card.dataset.profile || "lite");
    });
  });

  const saved = localStorage.getItem(STORAGE_PROFILE);
  if (saved) {
    const target = document.querySelector(`[data-profile="${saved}"]`);
    if (target) target.click();
  }
}

function setupDraft() {
  const select = document.querySelector("#draftPlatform");
  select.innerHTML = platformOptions.map((item) => (
    `<option value="${escapeHtml(item.id)}">${escapeHtml(item.label)} — ${escapeHtml(item.exts)}</option>`
  )).join("");

  document.querySelector("#saveGameBtn").addEventListener("click", () => {
    if (!draftGame) return;
    const name = document.querySelector("#draftName").value.trim() || cleanGameName(draftGame.fileName);
    const platform = document.querySelector("#draftPlatform").value || "manual";

    const game = {
      id: `game-${Date.now()}-${Math.random().toString(16).slice(2)}`,
      name,
      platform,
      fileName: draftGame.fileName,
      fileUri: draftGame.fileUri,
      coverUri: "",
      createdAt: new Date().toISOString()
    };

    addOrUpdateGame(game);
    draftGame = null;
    document.querySelector("#gameDraft").classList.add("hidden");
    openTab("games");
    showToast("Jogo salvo em Meus Jogos.");
  });

  document.querySelector("#cancelDraftBtn").addEventListener("click", () => {
    draftGame = null;
    document.querySelector("#gameDraft").classList.add("hidden");
  });
}

function setupActions() {
  document.querySelector("#pickRomBtn")?.addEventListener("click", () => {
    const bridge = native();
    if (bridge && bridge.pickRom) bridge.pickRom();
    else showToast("Seletor Android indisponível nesta visualização.");
  });

  document.querySelector("#copyNoticeBtn")?.addEventListener("click", () => {
    copyText("Aviso Companion Deck", legalNotice);
  });

  document.querySelectorAll("[data-url]").forEach((button) => {
    button.addEventListener("click", () => openUrl(button.dataset.url));
  });
}

window.CompanionDeckUI = {
  onRomPicked(fileName, uri, platformGuess) {
    const normalized = normalizeGuess(platformGuess);
    draftGame = {
      fileName: fileName || "Jogo selecionado",
      fileUri: uri || "",
      platformGuess: normalized
    };

    document.querySelector("#draftName").value = cleanGameName(draftGame.fileName);
    document.querySelector("#draftPlatform").value = platformOptions.some((item) => item.id === normalized) ? normalized : "manual";

    const guessText = normalized === "manual"
      ? "Escolha manualmente o sistema antes de salvar."
      : `Sugestão detectada: ${platformLabel(normalized)}.`;

    document.querySelector("#draftFileInfo").textContent = `${guessText} Arquivo: ${draftGame.fileName}`;
    document.querySelector("#gameDraft").classList.remove("hidden");
    document.querySelector("#gameDraft").scrollIntoView({ behavior: "smooth", block: "start" });
  },

  onCoverPicked(gameId, coverUri) {
    if (!gameId || !coverUri) return;
    const games = getGames();
    const index = games.findIndex((item) => item.id === gameId);
    if (index < 0) return;
    games[index].coverUri = coverUri;
    saveGames(games);
    renderGames();
    openTab("games");
    showToast("Capa adicionada.");
  }
};

async function boot() {
  setupTabs();
  setupProfiles();
  setupDraft();
  setupActions();

  const [consoles, codes] = await Promise.all([
    loadJson("./data/consoles.json", fallbackConsoles),
    loadJson("./data/codes.json", [])
  ]);

  renderConsoles(consoles);
  renderCodes(codes);
  renderGames();
}

boot();
