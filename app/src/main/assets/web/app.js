const fallbackConsoles = [
  {
    id: "gba",
    name: "Game Boy Advance",
    tier: "Leve",
    status: "planejado",
    extensions: ".gba",
    description: "Primeiro candidato para motor interno por ser leve e ideal para prova de conceito."
  },
  {
    id: "snes",
    name: "SNES",
    tier: "Leve",
    status: "planejado",
    extensions: ".sfc, .smc",
    description: "Sistema clássico, bom para modo Lite e catálogo inicial."
  },
  {
    id: "psp",
    name: "Portátil PSP",
    tier: "Avançado",
    status: "futuro",
    extensions: ".iso, .cso",
    description: "Meta futura com motor/core compatível, respeitando licenças e créditos."
  },
  {
    id: "ps2",
    name: "Console PS2",
    tier: "Extremo",
    status: "experimental futuro",
    extensions: ".iso, .chd",
    description: "Meta avançada. Pode exigir BIOS própria do usuário e aparelho forte."
  }
];

const legalNotice = `O Companion Deck não fornece jogos, ROMs, ISOs ou BIOS.
O usuário deve adicionar apenas arquivos que possui legalmente.
O Code Deck é voltado a jogos offline, treino e formatos compatíveis.
O app não injeta código, não altera memória de outros aplicativos e não oferece suporte a cheats online.`;

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

function renderConsoles(consoles) {
  const grid = document.querySelector("#consoleGrid");
  grid.innerHTML = "";

  consoles.forEach((item) => {
    const card = document.createElement("article");
    card.className = "console-card";
    card.innerHTML = `
      <div>
        <span>${item.extensions || "manual"}</span>
        <strong>${item.name}</strong>
        <p>${item.description}</p>
      </div>
      <em class="badge">${item.tier}</em>
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
      <h3>${item.title}</h3>
      <p><strong>${item.game}</strong> • ${item.format}<br>${item.description}</p>
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

function setupProfiles() {
  document.querySelectorAll(".status-card").forEach((card) => {
    card.addEventListener("click", () => {
      document.querySelectorAll(".status-card").forEach((item) => item.classList.remove("active"));
      card.classList.add("active");
      localStorage.setItem("companionDeckProfile", card.dataset.profile || "lite");
    });
  });

  const saved = localStorage.getItem("companionDeckProfile");
  if (saved) {
    const target = document.querySelector(`[data-profile="${saved}"]`);
    if (target) target.click();
  }
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
  onRomPicked(name, uri, platformGuess) {
    const box = document.querySelector("#lastPicked");
    const title = document.querySelector("#pickedName");
    const hint = document.querySelector("#pickedHint");

    box.classList.remove("hidden");
    title.textContent = name || "Jogo selecionado";

    const platformText = platformGuess && platformGuess !== "manual"
      ? `Sugestão: ${platformGuess}.`
      : "Sistema ainda precisa ser escolhido manualmente.";

    hint.textContent = `${platformText} Na v0.1 o app salva a base visual; motor interno entra em fase futura.`;
    localStorage.setItem("lastPickedName", name || "");
    localStorage.setItem("lastPickedUri", uri || "");
    localStorage.setItem("lastPickedPlatform", platformGuess || "manual");
  }
};

async function boot() {
  setupTabs();
  setupProfiles();
  setupActions();

  const [consoles, codes] = await Promise.all([
    loadJson("./data/consoles.json", fallbackConsoles),
    loadJson("./data/codes.json", [])
  ]);

  renderConsoles(consoles);
  renderCodes(codes);

  const lastName = localStorage.getItem("lastPickedName");
  if (lastName) {
    window.CompanionDeckUI.onRomPicked(
      lastName,
      localStorage.getItem("lastPickedUri") || "",
      localStorage.getItem("lastPickedPlatform") || "manual"
    );
  }
}

boot();
