package com.sw.companiodeck;

public final class NativeCoreBridge {
    private NativeCoreBridge() {}

    public static final class CoreInfo {
        public final String platformId;
        public final String platformLabel;
        public final String coreName;
        public final String tier;
        public final String status;
        public final int progress;
        public final String description;
        public final boolean nativeCandidate;
        public final boolean externalFirst;

        public CoreInfo(
                String platformId,
                String platformLabel,
                String coreName,
                String tier,
                String status,
                int progress,
                String description,
                boolean nativeCandidate,
                boolean externalFirst
        ) {
            this.platformId = platformId;
            this.platformLabel = platformLabel;
            this.coreName = coreName;
            this.tier = tier;
            this.status = status;
            this.progress = progress;
            this.description = description;
            this.nativeCandidate = nativeCandidate;
            this.externalFirst = externalFirst;
        }
    }

    public static CoreInfo[] allCores() {
        return new CoreInfo[] {
                new CoreInfo("gbc", "Game Boy / Color", "GB/GBC Native Slot", "Leve", "Primeiro alvo real", 45,
                        "Primeiro alvo nativo/offline do Companion Deck. Preparado para .gb/.gbc, sem CDN e sem core web.",
                        true, false),
                new CoreInfo("gba", "Game Boy Advance", "GBA Native Slot", "Leve", "Próximo candidato", 28,
                        "Candidato forte para segundo motor leve, depois de estabilizar GB/GBC.",
                        true, false),
                new CoreInfo("snes", "SNES", "SNES Native Slot", "Leve/Médio", "Candidato clássico", 24,
                        "Bom candidato, mas exige mais cuidado com áudio, timing e compatibilidade.",
                        true, false),
                new CoreInfo("psp", "Portátil PSP", "PSP Bridge Slot", "Avançado", "Fallback externo primeiro", 12,
                        "Fase avançada. PPSSPP segue como fallback enquanto não houver integração nativa adequada.",
                        false, true),
                new CoreInfo("cubewii", "Cube/Wii", "Dolphin Bridge Slot", "Extremo", "Fallback externo primeiro", 8,
                        "Fase extrema para aparelhos fortes. Dolphin segue como referência externa.",
                        false, true),
                new CoreInfo("ps2", "Console PS2", "PS2 Extreme Slot", "Extremo", "Fallback externo primeiro", 5,
                        "Fase extrema, pode exigir BIOS própria do usuário e aparelho forte. Não é alvo inicial.",
                        false, true),
                new CoreInfo("manual", "Escolher depois", "Manual Slot", "Manual", "Definir console", 0,
                        "Defina o console no perfil do jogo para receber rota correta de motor.",
                        false, true)
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
        if (info.nativeCandidate) {
            return info.coreName + ": slot nativo preparado. O próximo passo é ligar o core real offline.";
        }
        return info.coreName + ": ainda sem motor interno. Use fallback externo enquanto este console não entra.";
    }
}
