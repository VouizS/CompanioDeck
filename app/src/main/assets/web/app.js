const STORAGE_GAMES = "companionDeckGamesV2";
const STORAGE_PROFILE = "companionDeckProfile";
const STORAGE_LIBRARY_STATE = "companionDeckLibraryStateV4";

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

const statusOptions = {
  "not-tested": "Não testado",
  "playable": "Jogável",
  "lagging": "Travando",
  "finished": "Finalizado"
};

const statusOrder = {
  "not-tested": 1,
  "playable": 2,
  "lagging": 3,
  "finished": 4
};

const fallbackConsoles = [
  { id: "gba", name: "Game Boy Advance", tier: "Leve", status: "planejado", extensions: ".gba", description: "Primeiro candidato para motor interno." },
  { id: "snes", name: "SNES", tier: "Leve", status: "planejado", extensions: ".sfc, .smc", description: "Sistema clássico, bom para modo Lite." },
  { id: "psp", name: "Portátil PSP", tier: "Avançado", status: "futuro", extensions: ".iso, .cso", description: "Meta futura com motor/core compatível." }
];

let draftGame = null;
let activeProfileId = "";
let libraryState = loadLibraryState();

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

function openContentUri(uri) {
  const bridge = native();
  if (bridge && bridge.openContentUri) bridge.openContentUri(uri);
  else showToast("Abertura externa disponível apenas no APK.");
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

function loadLibraryState() {
  try {
    const raw = localStorage.getItem(STORAGE_LIBRARY_STATE);
    const parsed = raw ? JSON.parse(raw) : {};
    return {
      search: parsed.search || "",
      platform: parsed.platform || "all",
      status: parsed.status || "all",
      sort: parsed.sort || "recent"
    };
  } catch (error) {
    return { search: "", platform: "all", status: "all", sort: "recent" };
  }
}

function saveLibraryState() {
  localStorage.setItem(STORAGE_LIBRARY_STATE, JSON.stringify(libraryState));
}

function normalizeGuess(guess) {
  if (!guess) return "manual";
  if (guess === "iso-manual" || guess === "archive-manual" || guess === "ps1-manual") return "manual";
  return guess;
}

function platformLabel(id) {
  const item = platformOptions.find((platform) => platform.id === id);
  return item ? item.label : "Escolher depois";
}

function statusLabel(id) {
  return statusOptions[id] || "Não testado";
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

function normalizeGame(game) {
  return {
    id: game.id,
    name: game.name || cleanGameName(game.fileName),
    platform: game.platform || "manual",
    fileName: game.fileName || "Arquivo selecionado",
    fileUri: game.fileUri || "",
    coverUri: game.coverUri || "",
    status: game.status || "not-tested",
    notes: game.notes || "",
    createdAt: game.createdAt || new Date().toISOString(),
    updatedAt: game.updatedAt || game.createdAt || new Date().toISOString()
  };
}

function addOrUpdateGame(game) {
  const games = getGames().map(normalizeGame);
  const normalized = normalizeGame({ ...game, updatedAt: new Date().toISOString() });
  const index = games.findIndex((item) => item.id === normalized.id);
  if (index >= 0) games[index] = normalized;
  else games.unshift(normalized);
  saveGames(games);
  renderGames();
}

function getGameById(id) {
  return getGames().map(normalizeGame).find((item) => item.id === id);
}

function coverHtml(game) {
  if (game.coverUri) {
    return `<img src="${escapeHtml(game.coverUri)}" alt="Capa de ${escapeHtml(game.name)}">`;
  }
  return `<span>${escapeHtml(initials(game.name))}</span>`;
}

function filteredAndSortedGames() {
  const search = libraryState.search.trim().toLowerCase();
  let games = getGames().map(normalizeGame);

  if (search) {
    games = games.filter((game) => {
      const haystack = `${game.name} ${game.fileName} ${platformLabel(game.platform)} ${statusLabel(game.status)} ${game.notes}`.toLowerCase();
      return haystack.includes(search);
    });
  }

  if (libraryState.platform !== "all") {
    games = games.filter((game) => game.platform === libraryState.platform);
  }

  if (libraryState.status !== "all") {
    games = games.filter((game) => game.status === libraryState.status);
  }

  games.sort((a, b) => {
    if (libraryState.sort === "name") {
      return a.name.localeCompare(b.name, "pt-BR", { sensitivity: "base" });
    }
    if (libraryState.sort === "status") {
      return (statusOrder[a.status] || 99) - (statusOrder[b.status] || 99)
        || a.name.localeCompare(b.name, "pt-BR", { sensitivity: "base" });
    }
    if (libraryState.sort === "platform") {
      return platformLabel(a.platform).localeCompare(platformLabel(b.platform), "pt-BR", { sensitivity: "base" })
        || a.name.localeCompare(b.name, "pt-BR", { sensitivity: "base" });
    }
    return new Date(b.updatedAt || b.createdAt).getTime() - new Date(a.updatedAt || a.createdAt).getTime();
  });

  return games;
}

function renderDashboard(allGames) {
  document.querySelector("#dashTotal").textContent = allGames.length;
  document.querySelector("#dashPlayable").textContent = allGames.filter((game) => game.status === "playable" || game.status === "finished").length;
  document.querySelector("#dashLagging").textContent = allGames.filter((game) => game.status === "lagging").length;
}

function renderConsoleCounters(allGames) {
  const row = document.querySelector("#consoleCounters");
  const counts = new Map();

  allGames.forEach((game) => {
    counts.set(game.platform, (counts.get(game.platform) || 0) + 1);
  });

  const chips = [
    { id: "all", label: `Todos (${allGames.length})` },
    ...Array.from(counts.entries())
      .sort((a, b) => platformLabel(a[0]).localeCompare(platformLabel(b[0]), "pt-BR", { sensitivity: "base" }))
      .map(([id, count]) => ({ id, label: `${platformLabel(id)} (${count})` }))
  ];

  row.innerHTML = chips.map((chip) => `
    <button class="filter-chip ${libraryState.platform === chip.id ? "active" : ""}" data-platform-chip="${escapeHtml(chip.id)}">
      ${escapeHtml(chip.label)}
    </button>
  `).join("");

  row.querySelectorAll("[data-platform-chip]").forEach((chip) => {
    chip.addEventListener("click", () => {
      libraryState.platform = chip.dataset.platformChip;
      document.querySelector("#filterPlatform").value = libraryState.platform;
      saveLibraryState();
      renderGames();
    });
  });
}

function renderGames() {
  const allGames = getGames().map(normalizeGame);
  saveGames(allGames);

  const games = filteredAndSortedGames();
  const grid = document.querySelector("#gameGrid");
  const empty = document.querySelector("#emptyGames");
  const emptyFiltered = document.querySelector("#emptyFilteredGames");

  renderDashboard(allGames);
  renderConsoleCounters(allGames);

  grid.innerHTML = "";
  empty.classList.toggle("hidden", allGames.length > 0);
  emptyFiltered.classList.toggle("hidden", !(allGames.length > 0 && games.length === 0));

  games.forEach((game) => {
    const card = document.createElement("article");
    card.className = "game-card";

    card.innerHTML = `
      <div class="cover-box">${coverHtml(game)}</div>
      <div class="game-info">
        <div class="game-meta">${escapeHtml(platformLabel(game.platform))}</div>
        <div class="game-title">${escapeHtml(game.name)}</div>
        <div class="status-pill ${escapeHtml(game.status)}">${escapeHtml(statusLabel(game.status))}</div>
        <div class="game-file">${escapeHtml(game.fileName || "Arquivo selecionado")}</div>
        <div class="game-actions">
          <button class="mini-button" data-action="cover" data-id="${escapeHtml(game.id)}">Capa</button>
          <button class="mini-button" data-action="details" data-id="${escapeHtml(game.id)}">Perfil</button>
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
  const games = getGames().map(normalizeGame);
  const game = games.find((item) => item.id === gameId);
  if (!game) return;

  if (action === "cover") {
    const bridge = native();
    if (bridge && bridge.pickCover) bridge.pickCover(game.id);
    else showToast("Seletor de capa indisponível nesta visualização.");
    return;
  }

  if (action === "details") {
    openProfile(game.id);
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
      libraryState.platform = item.id;
      document.querySelector("#filterPlatform").value = item.id;
      saveLibraryState();
      openTab("games");
      renderGames();
      showToast(`Filtro aplicado: ${item.name}.`);
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

function fillPlatformSelect(select, includeAll = false) {
  const options = includeAll
    ? [{ id: "all", label: "Todos os consoles", exts: "biblioteca" }, ...platformOptions]
    : platformOptions;

  select.innerHTML = options.map((item) => (
    `<option value="${escapeHtml(item.id)}">${escapeHtml(item.label)}${includeAll && item.id === "all" ? "" : " — " + escapeHtml(item.exts)}</option>`
  )).join("");
}

function setupLibraryTools() {
  const search = document.querySelector("#gameSearch");
  const platform = document.querySelector("#filterPlatform");
  const status = document.querySelector("#filterStatus");
  const sort = document.querySelector("#sortMode");

  fillPlatformSelect(platform, true);

  search.value = libraryState.search;
  platform.value = libraryState.platform;
  status.value = libraryState.status;
  sort.value = libraryState.sort;

  search.addEventListener("input", () => {
    libraryState.search = search.value;
    saveLibraryState();
    renderGames();
  });

  platform.addEventListener("change", () => {
    libraryState.platform = platform.value;
    saveLibraryState();
    renderGames();
  });

  status.addEventListener("change", () => {
    libraryState.status = status.value;
    saveLibraryState();
    renderGames();
  });

  sort.addEventListener("change", () => {
    libraryState.sort = sort.value;
    saveLibraryState();
    renderGames();
  });

  document.querySelector("#clearFiltersBtn").addEventListener("click", () => {
    libraryState = { search: "", platform: "all", status: "all", sort: "recent" };
    search.value = "";
    platform.value = "all";
    status.value = "all";
    sort.value = "recent";
    saveLibraryState();
    renderGames();
    showToast("Filtros limpos.");
  });
}

function setupDraft() {
  fillPlatformSelect(document.querySelector("#draftPlatform"));

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
      status: "not-tested",
      notes: "",
      createdAt: new Date().toISOString()
    };

    addOrUpdateGame(game);
    draftGame = null;
    document.querySelector("#gameDraft").classList.add("hidden");
    libraryState.platform = "all";
    libraryState.search = "";
    saveLibraryState();
    document.querySelector("#gameSearch").value = "";
    document.querySelector("#filterPlatform").value = "all";
    openTab("games");
    renderGames();
    showToast("Jogo salvo em Meus Jogos.");
  });

  document.querySelector("#cancelDraftBtn").addEventListener("click", () => {
    draftGame = null;
    document.querySelector("#gameDraft").classList.add("hidden");
  });
}

function setupProfileSheet() {
  fillPlatformSelect(document.querySelector("#profilePlatform"));

  document.querySelector("#closeProfileBtn").addEventListener("click", closeProfile);
  document.querySelector(".sheet-backdrop").addEventListener("click", closeProfile);

  document.querySelector("#profilePickCoverBtn").addEventListener("click", () => {
    if (!activeProfileId) return;
    const bridge = native();
    if (bridge && bridge.pickCover) bridge.pickCover(activeProfileId);
  });

  document.querySelector("#profileRemoveCoverBtn").addEventListener("click", () => {
    if (!activeProfileId) return;
    const games = getGames().map(normalizeGame);
    const index = games.findIndex((item) => item.id === activeProfileId);
    if (index < 0) return;
    games[index].coverUri = "";
    games[index].updatedAt = new Date().toISOString();
    saveGames(games);
    openProfile(activeProfileId);
    renderGames();
    showToast("Capa removida.");
  });

  document.querySelector("#profileSaveBtn").addEventListener("click", () => {
    if (!activeProfileId) return;
    const games = getGames().map(normalizeGame);
    const index = games.findIndex((item) => item.id === activeProfileId);
    if (index < 0) return;

    games[index].name = document.querySelector("#profileName").value.trim() || games[index].name;
    games[index].platform = document.querySelector("#profilePlatform").value || "manual";
    games[index].status = document.querySelector("#profileStatus").value || "not-tested";
    games[index].notes = document.querySelector("#profileNotes").value.trim();
    games[index].updatedAt = new Date().toISOString();

    saveGames(games);
    renderGames();
    openProfile(activeProfileId);
    showToast("Perfil salvo.");
  });

  document.querySelector("#profileOpenExternalBtn").addEventListener("click", () => {
    const game = getGameById(activeProfileId);
    if (!game) return;
    openContentUri(game.fileUri);
  });
}

function openProfile(gameId) {
  const game = getGameById(gameId);
  if (!game) return;

  activeProfileId = game.id;
  document.querySelector("#profileTitle").textContent = game.name;
  document.querySelector("#profileName").value = game.name;
  document.querySelector("#profilePlatform").value = game.platform || "manual";
  document.querySelector("#profileStatus").value = game.status || "not-tested";
  document.querySelector("#profileNotes").value = game.notes || "";
  document.querySelector("#profileFileInfo").textContent = `Arquivo: ${game.fileName}. Console: ${platformLabel(game.platform)}. Motor interno ainda futuro.`;

  const preview = document.querySelector("#profileCoverPreview");
  preview.innerHTML = coverHtml(game);

  const sheet = document.querySelector("#profileSheet");
  sheet.classList.remove("hidden");
  sheet.setAttribute("aria-hidden", "false");
}

function closeProfile() {
  activeProfileId = "";
  const sheet = document.querySelector("#profileSheet");
  sheet.classList.add("hidden");
  sheet.setAttribute("aria-hidden", "true");
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
    const games = getGames().map(normalizeGame);
    const index = games.findIndex((item) => item.id === gameId);
    if (index < 0) return;
    games[index].coverUri = coverUri;
    games[index].updatedAt = new Date().toISOString();
    saveGames(games);
    renderGames();
    if (activeProfileId === gameId) openProfile(gameId);
    openTab("games");
    showToast("Capa adicionada.");
  },

  closeProfileIfOpen() {
    if (!document.querySelector("#profileSheet").classList.contains("hidden")) {
      closeProfile();
      return true;
    }
    return false;
  }
};

async function boot() {
  setupTabs();
  setupProfiles();
  setupLibraryTools();
  setupDraft();
  setupProfileSheet();
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
