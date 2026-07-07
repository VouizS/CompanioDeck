package com.sw.companiodeck;

public final class NativeCoreBridge {
    private NativeCoreBridge() {}

    public static final class CoreInfo {
        public final String platformId;
        public final String platformLabel;
        public final String userStatus;
        public final String detailStatus;
        public final boolean internalTarget;
        public final boolean externalFirst;

        public CoreInfo(String platformId, String platformLabel, String userStatus, String detailStatus, boolean internalTarget, boolean externalFirst) {
            this.platformId = platformId;
            this.platformLabel = platformLabel;
            this.userStatus = userStatus;
            this.detailStatus = detailStatus;
            this.internalTarget = internalTarget;
            this.externalFirst = externalFirst;
        }
    }

    public static CoreInfo[] allCores() {
        return new CoreInfo[] {
                new CoreInfo("gbc", "Game Boy / Color", "Preparando player interno", "Primeiro alvo nativo/offline para .gb/.gbc.", true, false),
                new CoreInfo("gba", "Game Boy Advance", "Na fila", "Segundo alvo leve após GB/GBC.", true, false),
                new CoreInfo("snes", "SNES", "Na fila", "Candidato clássico; exige cuidado com áudio e compatibilidade.", true, false),
                new CoreInfo("psp", "Portátil PSP", "Usar app compatível por enquanto", "Fase avançada. PPSSPP segue como fallback.", false, true),
                new CoreInfo("ps1", "PS1", "Usar app compatível por enquanto", "Exige validação de BIOS/compatibilidade do usuário.", false, true),
                new CoreInfo("n64", "Nintendo 64", "Usar app compatível por enquanto", "Fase futura com fallback externo.", false, true),
                new CoreInfo("cubewii", "Cube/Wii", "Usar app compatível por enquanto", "Fase extrema para aparelhos fortes. Dolphin segue como fallback.", false, true),
                new CoreInfo("ps2", "Console PS2", "Usar app compatível por enquanto", "Fase extrema. Pode exigir BIOS própria do usuário.", false, true),
                new CoreInfo("manual", "Escolher depois", "Definir console", "Escolha o sistema no perfil para receber a rota correta.", false, true)
        };
    }

    public static CoreInfo infoForPlatform(String platformId) {
        if (platformId == null) platformId = "manual";
        for (CoreInfo info : allCores()) {
            if (platformId.equals(info.platformId)) return info;
        }
        return allCores()[allCores().length - 1];
    }

    public static String runtimeMessage(String platformId) {
        CoreInfo info = infoForPlatform(platformId);
        if (info.internalTarget) return "Player interno preparado para este sistema.";
        return "Este sistema usa fallback externo por enquanto.";
    }
}
