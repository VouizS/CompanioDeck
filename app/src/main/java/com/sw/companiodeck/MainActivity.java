package com.sw.companiodeck;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_ROM = 1101;
    private static final String VERSION_LABEL = "v1.0-r1 • native runtime";
    private static final String PREFS = "companion_deck_native_prefs";
    private static final String KEY_GAMES = "games_native_v1";
    private static final String[] PLATFORM_IDS = {"gbc", "gba", "snes", "psp", "ps1", "n64", "cubewii", "ps2", "manual"};
    private static final String[] PLATFORM_LABELS = {"Game Boy / Color", "Game Boy Advance", "SNES", "Portátil PSP", "PS1", "Nintendo 64", "Cube/Wii", "Console PS2", "Escolher depois"};
    private static final String[] STATUS_IDS = {"not-tested", "playable", "lagging", "finished"};
    private static final String[] STATUS_LABELS = {"Não testado", "Jogável", "Travando", "Finalizado"};

    private final ArrayList<GameEntry> games = new ArrayList<>();
    private String currentTab = "games";
    private GameEntry activePlayerGame;
    private boolean inPlayer = false;
    private boolean sidebarOpen = false;

    private LinearLayout root;
    private LinearLayout content;
    private HorizontalScrollView tabScroll;

    private final int BG = Color.rgb(7, 8, 7);
    private final int PANEL = Color.rgb(17, 23, 19);
    private final int PANEL_2 = Color.rgb(23, 31, 25);
    private final int TEXT = Color.rgb(244, 247, 239);
    private final int MUTED = Color.rgb(164, 173, 157);
    private final int ACCENT = Color.rgb(163, 230, 53);
    private final int GOLD = Color.rgb(251, 191, 36);
    private final int DANGER = Color.rgb(239, 68, 68);

    static class GameEntry {
        String id;
        String name;
        String platform;
        String fileName;
        String uri;
        String status;
        String notes;
        long updatedAt;

        JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("name", name);
            obj.put("platform", platform);
            obj.put("fileName", fileName);
            obj.put("uri", uri);
            obj.put("status", status);
            obj.put("notes", notes);
            obj.put("updatedAt", updatedAt);
            return obj;
        }

        static GameEntry fromJson(JSONObject obj) {
            GameEntry g = new GameEntry();
            g.id = obj.optString("id", "game-" + System.currentTimeMillis());
            g.name = obj.optString("name", "Jogo");
            g.platform = obj.optString("platform", "manual");
            g.fileName = obj.optString("fileName", "Arquivo selecionado");
            g.uri = obj.optString("uri", "");
            g.status = obj.optString("status", "not-tested");
            g.notes = obj.optString("notes", "");
            g.updatedAt = obj.optLong("updatedAt", System.currentTimeMillis());
            return g;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        loadGames();
        renderMain();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable bg(int color, int radiusDp, int strokeColor, int strokeWidthDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeWidthDp > 0) d.setStroke(dp(strokeWidthDp), strokeColor);
        return d;
    }

    private TextView tv(String text, int sp, int color, int style) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT, style);
        v.setLineSpacing(dp(2), 1.0f);
        return v;
    }

    private Button btn(String text, boolean primary) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(primary ? BG : TEXT);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(48));
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackground(bg(primary ? ACCENT : PANEL_2, 18, primary ? ACCENT : Color.argb(28, 219, 255, 184), 1));
        return b;
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        layout.setBackground(bg(PANEL, 24, Color.argb(24, 219, 255, 184), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(14), dp(8), dp(14), dp(8));
        layout.setLayoutParams(lp);
        return layout;
    }

    private void renderMain() {
        inPlayer = false;
        sidebarOpen = false;

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, dp(10), 0, dp(18));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        page.addView(header());
        page.addView(quickActions());
        page.addView(tabs());

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        page.addView(content);

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        renderTab(currentTab);
    }

    private View header() {
        LinearLayout h = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        int iconId = getResources().getIdentifier("companiondeck_official_icon", "drawable", getPackageName());
        if (iconId != 0) icon.setImageResource(iconId);
        icon.setBackground(bg(Color.rgb(248, 250, 252), 16, Color.argb(70, 255, 255, 255), 1));
        icon.setPadding(dp(4), dp(4), dp(4), dp(4));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(54), dp(54));
        row.addView(icon, ilp);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(14), 0, 0, 0);
        titles.addView(tv(VERSION_LABEL.toUpperCase(Locale.ROOT), 13, ACCENT, Typeface.BOLD));
        titles.addView(tv("Companion Deck", 35, TEXT, Typeface.BOLD));
        row.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        h.addView(row);
        TextView copy = tv("Central gamer offline-first com interface nativa leve, runtime nativo preparado, Core Manager, Player em tela cheia, sidebar oculta e fallback externo.", 18, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.setMargins(0, dp(18), 0, 0);
        h.addView(copy, clp);

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.VERTICAL);
        modes.setPadding(0, dp(16), 0, 0);
        modes.addView(modeCard("Modo Lite", "Interface econômica", true));
        modes.addView(modeCard("Equilibrado", "Visual + desempenho", false));
        modes.addView(modeCard("Avançado", "Para aparelho forte", false));
        h.addView(modes);
        return h;
    }

    private View modeCard(String top, String bottom, boolean selected) {
        LinearLayout m = new LinearLayout(this);
        m.setOrientation(LinearLayout.VERTICAL);
        m.setPadding(dp(14), dp(12), dp(14), dp(12));
        m.setBackground(bg(selected ? Color.rgb(31, 49, 21) : PANEL_2, 18, selected ? ACCENT : Color.argb(24, 219, 255, 184), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, dp(5));
        m.setLayoutParams(lp);
        m.addView(tv(top, 14, MUTED, Typeface.NORMAL));
        m.addView(tv(bottom, 17, TEXT, Typeface.BOLD));
        return m;
    }

    private View quickActions() {
        LinearLayout q = new LinearLayout(this);
        q.setOrientation(LinearLayout.VERTICAL);
        q.setPadding(dp(14), dp(4), dp(14), dp(4));

        Button add = btn("Adicionar jogo\nSelecionar ROM/ISO do dispositivo", true);
        add.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        add.setOnClickListener(v -> pickRom());
        q.addView(add, new LinearLayout.LayoutParams(-1, dp(70)));

        Button legal = btn("Copiar aviso legal", false);
        legal.setOnClickListener(v -> copyLegal());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.setMargins(0, dp(10), 0, 0);
        q.addView(legal, lp);
        return q;
    }

    private View tabs() {
        tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(14), dp(8), dp(14), dp(8));

        addTab(row, "games", "Meus Jogos");
        addTab(row, "player", "Player");
        addTab(row, "cores", "Motores");
        addTab(row, "launcher", "Launcher");
        addTab(row, "rules", "Regras");

        tabScroll.addView(row);
        return tabScroll;
    }

    private void addTab(LinearLayout row, String id, String label) {
        Button b = btn(label, id.equals(currentTab));
        b.setTextSize(14);
        b.setOnClickListener(v -> {
            currentTab = id;
            renderMain();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(132), dp(48));
        lp.setMargins(0, 0, dp(8), 0);
        row.addView(b, lp);
    }

    private void renderTab(String id) {
        content.removeAllViews();
        if ("games".equals(id)) renderGamesTab();
        else if ("player".equals(id)) renderPlayerTab();
        else if ("cores".equals(id)) renderCoresTab();
        else if ("launcher".equals(id)) renderLauncherTab();
        else renderRulesTab();
    }

    private void renderSectionTitle(String label, String title) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(0));
        box.addView(tv(label.toUpperCase(Locale.ROOT), 12, ACCENT, Typeface.BOLD));
        box.addView(tv(title, 26, TEXT, Typeface.BOLD));
        content.addView(box);
    }

    private void renderGamesTab() {
        renderSectionTitle("Central local", "Meus Jogos");

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(dp(14), dp(8), dp(14), dp(8));
        stats.addView(statCard("Total", String.valueOf(games.size())), new LinearLayout.LayoutParams(0, dp(86), 1f));
        stats.addView(statCard("Jogáveis", String.valueOf(countStatus("playable") + countStatus("finished"))), new LinearLayout.LayoutParams(0, dp(86), 1f));
        stats.addView(statCard("Travando", String.valueOf(countStatus("lagging"))), new LinearLayout.LayoutParams(0, dp(86), 1f));
        content.addView(stats);

        if (games.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(tv("Nenhum jogo salvo ainda.", 20, TEXT, Typeface.BOLD));
            empty.addView(tv("Toque em Adicionar jogo, escolha o arquivo no dispositivo e salve o card. A ROM/ISO não é copiada para dentro do app.", 16, MUTED, Typeface.NORMAL));
            content.addView(empty);
            return;
        }

        for (GameEntry g : games) content.addView(gameCard(g));
    }

    private View statCard(String label, String value) {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setPadding(dp(12), dp(10), dp(12), dp(10));
        s.setBackground(bg(PANEL, 22, Color.argb(24, 219, 255, 184), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(84), 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        s.setLayoutParams(lp);
        s.addView(tv(label, 14, MUTED, Typeface.NORMAL));
        s.addView(tv(value, 28, TEXT, Typeface.BOLD));
        return s;
    }

    private int countStatus(String status) {
        int c = 0;
        for (GameEntry g : games) if (status.equals(g.status)) c++;
        return c;
    }

    private View gameCard(GameEntry g) {
        LinearLayout c = card();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);

        TextView cover = tv(initials(g.name), 24, ACCENT, Typeface.BOLD);
        cover.setGravity(Gravity.CENTER);
        cover.setBackground(bg(Color.rgb(43, 64, 31), 18, Color.TRANSPARENT, 0));
        top.addView(cover, new LinearLayout.LayoutParams(dp(92), dp(112)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(14), 0, 0, 0);
        info.addView(tv(platformLabel(g.platform), 13, MUTED, Typeface.BOLD));
        info.addView(tv(g.name, 20, TEXT, Typeface.BOLD));
        info.addView(tv(statusLabel(g.status), 13, GOLD, Typeface.BOLD));
        info.addView(tv(g.fileName, 13, MUTED, Typeface.NORMAL));
        top.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        c.addView(top);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(12), 0, 0);
        Button play = btn("Jogar", true);
        play.setOnClickListener(v -> openPlayer(g));
        Button profile = btn("Perfil", false);
        profile.setOnClickListener(v -> showProfile(g));
        Button ext = btn("Externo", false);
        ext.setOnClickListener(v -> openExternal(g));
        Button remove = btn("Remover", false);
        remove.setTextColor(Color.rgb(254, 202, 202));
        remove.setOnClickListener(v -> removeGame(g));

        actions.addView(play, new LinearLayout.LayoutParams(0, dp(46), 1f));
        actions.addView(profile, new LinearLayout.LayoutParams(0, dp(46), 1f));
        actions.addView(ext, new LinearLayout.LayoutParams(0, dp(46), 1f));
        actions.addView(remove, new LinearLayout.LayoutParams(0, dp(46), 1f));
        c.addView(actions);
        return c;
    }

    private void renderPlayerTab() {
        renderSectionTitle("Runtime nativo", "Player");
        LinearLayout info = card();
        info.addView(tv("Player nativo preparado.", 20, TEXT, Typeface.BOLD));
        info.addView(tv("Esta versão remove o caminho de emulação web/CDN. O Player agora usa uma base nativa com NativeCoreSurface para receber render real do core offline em fase futura.", 16, MUTED, Typeface.NORMAL));
        content.addView(info);

        if (!games.isEmpty()) {
            Button b = btn("Abrir último jogo salvo", true);
            b.setOnClickListener(v -> openPlayer(games.get(0)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
            lp.setMargins(dp(14), dp(8), dp(14), dp(8));
            content.addView(b, lp);
        }
    }

    private void renderCoresTab() {
        renderSectionTitle("Arquitetura interna", "Motores");
        LinearLayout intro = card();
        intro.addView(tv("Native Runtime Foundation", 20, TEXT, Typeface.BOLD));
        intro.addView(tv("Aqui ficam os slots reais dos motores. GB/GBC é o primeiro alvo nativo/offline. PSP, Cube/Wii e PS2 permanecem como fallback externo enquanto não houver integração adequada.", 16, MUTED, Typeface.NORMAL));
        content.addView(intro);

        for (NativeCoreBridge.CoreInfo core : NativeCoreBridge.allCores()) {
            if ("manual".equals(core.platformId)) continue;
            LinearLayout c = card();
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.addView(tv(core.platformLabel.toUpperCase(Locale.ROOT), 12, ACCENT, Typeface.BOLD));
            left.addView(tv(core.coreName, 20, TEXT, Typeface.BOLD));
            left.addView(tv(core.description, 15, MUTED, Typeface.NORMAL));
            row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView tier = tv(core.tier, 12, core.externalFirst ? GOLD : ACCENT, Typeface.BOLD);
            tier.setGravity(Gravity.CENTER);
            tier.setBackground(bg(Color.rgb(31, 49, 21), 999, Color.argb(45, 163, 230, 53), 1));
            row.addView(tier, new LinearLayout.LayoutParams(dp(86), dp(34)));
            c.addView(row);

            TextView progress = tv(core.status + " • " + core.progress + "% preparação", 14, TEXT, Typeface.BOLD);
            progress.setPadding(0, dp(10), 0, 0);
            c.addView(progress);
            content.addView(c);
        }
    }

    private void renderLauncherTab() {
        renderSectionTitle("Fallback externo", "Launcher");
        LinearLayout intro = card();
        intro.addView(tv("Opção secundária.", 20, TEXT, Typeface.BOLD));
        intro.addView(tv("O objetivo é rodar dentro do Companion Deck. Enquanto um console não tem motor interno, o fallback pode abrir o arquivo em app compatível instalado.", 16, MUTED, Typeface.NORMAL));
        content.addView(intro);

        launcherCard("RetroArch / Libretro", "Sistemas clássicos: GB/GBC, GBA, SNES e outros.", "com.retroarch", "https://www.retroarch.com/");
        launcherCard("PPSSPP", "Referência externa para PSP.", "org.ppsspp.ppsspp", "https://www.ppsspp.org/");
        launcherCard("Dolphin", "Referência externa para Cube/Wii.", "org.dolphinemu.dolphinemu", "https://dolphin-emu.org/");
    }

    private void launcherCard(String name, String desc, String pkg, String url) {
        LinearLayout c = card();
        boolean installed = isPackageInstalled(pkg);
        c.addView(tv(name, 20, TEXT, Typeface.BOLD));
        c.addView(tv((installed ? "Instalado detectado. " : "Não detectado / opcional. ") + desc, 15, MUTED, Typeface.NORMAL));
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(10), 0, 0);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button open = btn("Abrir app", installed);
        open.setEnabled(installed);
        open.setOnClickListener(v -> launchPackage(pkg));
        Button site = btn("Site oficial", false);
        site.setOnClickListener(v -> openUrl(url));
        row.addView(open, new LinearLayout.LayoutParams(0, dp(48), 1f));
        row.addView(site, new LinearLayout.LayoutParams(0, dp(48), 1f));
        c.addView(row);
        content.addView(c);
    }

    private void renderRulesTab() {
        renderSectionTitle("Uso responsável", "Regras");
        LinearLayout c = card();
        c.addView(tv("Sem ROMs, ISOs ou BIOS.", 20, TEXT, Typeface.BOLD));
        c.addView(tv("O Companion Deck não fornece jogos, ROMs, ISOs ou BIOS. O usuário deve adicionar apenas arquivos que possui legalmente. Esta versão remove qualquer direção de emulação web e prepara o runtime nativo/offline para motores internos reais.", 16, MUTED, Typeface.NORMAL));
        content.addView(c);
    }

    private void pickRom() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_ROM);
        } catch (Exception e) {
            Toast.makeText(this, "Seletor de arquivos indisponível.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_ROM || resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}

        String fileName = displayName(uri);
        GameEntry g = new GameEntry();
        g.id = "game-" + System.currentTimeMillis();
        g.name = cleanName(fileName);
        g.fileName = fileName;
        g.uri = uri.toString();
        g.platform = guessPlatform(fileName);
        g.status = "not-tested";
        g.notes = "";
        g.updatedAt = System.currentTimeMillis();

        games.add(0, g);
        saveGames();
        currentTab = "games";
        renderMain();
        Toast.makeText(this, "Jogo salvo: " + g.name, Toast.LENGTH_SHORT).show();
    }

    private String displayName(Uri uri) {
        String result = null;
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) result = cursor.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        if (result == null || result.trim().isEmpty()) result = uri.getLastPathSegment();
        return result == null ? "Jogo selecionado" : result;
    }

    private String cleanName(String fileName) {
        String name = fileName == null ? "Jogo" : fileName.replaceAll("\\.[^.]+$", "");
        name = name.replace('_', ' ').replace('-', ' ').trim();
        return name.isEmpty() ? "Jogo" : name;
    }

    private String guessPlatform(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".gb") || lower.endsWith(".gbc")) return "gbc";
        if (lower.endsWith(".gba")) return "gba";
        if (lower.endsWith(".sfc") || lower.endsWith(".smc")) return "snes";
        if (lower.endsWith(".cso")) return "psp";
        if (lower.endsWith(".z64") || lower.endsWith(".n64") || lower.endsWith(".v64")) return "n64";
        if (lower.endsWith(".gcm") || lower.endsWith(".wbfs") || lower.endsWith(".rvz")) return "cubewii";
        return "manual";
    }

    private void showProfile(GameEntry g) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);

        EditText name = new EditText(this);
        name.setText(g.name);
        name.setSingleLine(false);
        name.setTextColor(TEXT);
        name.setHintTextColor(MUTED);
        box.addView(label("Nome do jogo"));
        box.addView(name);

        Spinner platform = new Spinner(this);
        platform.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, PLATFORM_LABELS));
        platform.setSelection(indexOf(PLATFORM_IDS, g.platform));
        box.addView(label("Console / sistema"));
        box.addView(platform);

        Spinner status = new Spinner(this);
        status.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, STATUS_LABELS));
        status.setSelection(indexOf(STATUS_IDS, g.status));
        box.addView(label("Status do teste"));
        box.addView(status);

        EditText notes = new EditText(this);
        notes.setText(g.notes);
        notes.setMinLines(3);
        notes.setTextColor(TEXT);
        notes.setHint("Notas do jogador");
        notes.setHintTextColor(MUTED);
        box.addView(label("Notas"));
        box.addView(notes);

        NativeCoreBridge.CoreInfo info = NativeCoreBridge.infoForPlatform(g.platform);
        TextView coreInfo = tv("Motor: " + info.coreName + "\n" + info.description, 14, MUTED, Typeface.NORMAL);
        coreInfo.setPadding(0, dp(12), 0, 0);
        box.addView(coreInfo);

        new AlertDialog.Builder(this)
                .setTitle("Perfil do jogo")
                .setView(box)
                .setPositiveButton("Salvar", (d, which) -> {
                    g.name = name.getText().toString().trim().isEmpty() ? g.name : name.getText().toString().trim();
                    g.platform = PLATFORM_IDS[platform.getSelectedItemPosition()];
                    g.status = STATUS_IDS[status.getSelectedItemPosition()];
                    g.notes = notes.getText().toString();
                    g.updatedAt = System.currentTimeMillis();
                    saveGames();
                    renderMain();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private TextView label(String text) {
        TextView label = tv(text, 13, MUTED, Typeface.BOLD);
        label.setPadding(0, dp(10), 0, 0);
        return label;
    }

    private int indexOf(String[] array, String value) {
        for (int i = 0; i < array.length; i++) if (array[i].equals(value)) return i;
        return array.length - 1;
    }

    private void removeGame(GameEntry g) {
        new AlertDialog.Builder(this)
                .setTitle("Remover jogo")
                .setMessage("Remover \"" + g.name + "\" da biblioteca local?")
                .setPositiveButton("Remover", (d, w) -> {
                    games.remove(g);
                    saveGames();
                    renderMain();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void openPlayer(GameEntry g) {
        activePlayerGame = g;
        inPlayer = true;
        sidebarOpen = false;

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);

        NativeCoreSurface surface = new NativeCoreSurface(this);
        NativeCoreBridge.CoreInfo info = NativeCoreBridge.infoForPlatform(g.platform);
        surface.setSlotText(g.name, NativeCoreBridge.runtimeMessage(g.platform));
        frame.addView(surface, new FrameLayout.LayoutParams(-1, -1));

        TextView badge = tv(info.platformLabel + " • " + info.status, 12, ACCENT, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(bg(Color.argb(190, 17, 23, 19), 999, Color.argb(60, 163, 230, 53), 1));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, dp(34), Gravity.TOP | Gravity.LEFT);
        blp.setMargins(dp(16), dp(18), 0, 0);
        frame.addView(badge, blp);

        Button menu = btn("☰", false);
        menu.setTextSize(22);
        menu.setOnClickListener(v -> showSidebar(frame));
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.TOP | Gravity.RIGHT);
        mlp.setMargins(0, dp(16), dp(16), 0);
        frame.addView(menu, mlp);

        TextView bottom = tv("Runtime nativo preparado • sem core web/CDN", 12, MUTED, Typeface.BOLD);
        bottom.setGravity(Gravity.CENTER);
        bottom.setBackground(bg(Color.argb(130, 17, 23, 19), 999, Color.argb(48, 163, 230, 53), 1));
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(-2, dp(38), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bottomLp.setMargins(0, 0, 0, dp(24));
        frame.addView(bottom, bottomLp);

        setContentView(frame);
    }

    private void showSidebar(FrameLayout frame) {
        if (sidebarOpen) return;
        sidebarOpen = true;

        View dim = new View(this);
        dim.setBackgroundColor(Color.argb(120, 0, 0, 0));
        dim.setTag("sidebar");
        dim.setOnClickListener(v -> closeSidebar(frame));
        frame.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout side = new LinearLayout(this);
        side.setTag("sidebar");
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(dp(18), dp(26), dp(18), dp(18));
        side.setBackgroundColor(Color.rgb(10, 16, 12));

        side.addView(tv("MENU RÁPIDO", 12, ACCENT, Typeface.BOLD));
        side.addView(tv("Player", 28, TEXT, Typeface.BOLD));

        Button resume = btn("Continuar jogo", true);
        resume.setOnClickListener(v -> closeSidebar(frame));
        Button config = btn("Configurar layout de controle", false);
        config.setOnClickListener(v -> Toast.makeText(this, "Layout será ligado ao core nativo real.", Toast.LENGTH_SHORT).show());
        Button external = btn("Abrir em emulador externo", false);
        external.setOnClickListener(v -> openExternal(activePlayerGame));
        Button library = btn("Voltar para biblioteca", false);
        library.setOnClickListener(v -> {
            inPlayer = false;
            currentTab = "games";
            renderMain();
        });
        Button exit = btn("Sair do jogo", false);
        exit.setTextColor(Color.rgb(254, 202, 202));
        exit.setOnClickListener(v -> {
            inPlayer = false;
            currentTab = "games";
            renderMain();
        });

        addSideButton(side, resume);
        addSideButton(side, config);
        addSideButton(side, external);
        addSideButton(side, library);
        addSideButton(side, exit);

        TextView note = tv("Base correta: interface nativa fluida, Player nativo e slot de core offline. Nenhum motor web/CDN é usado nesta versão.", 14, MUTED, Typeface.NORMAL);
        note.setPadding(0, dp(16), 0, 0);
        side.addView(note);

        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(dp(330), -1, Gravity.RIGHT);
        frame.addView(side, slp);
    }

    private void addSideButton(LinearLayout side, Button b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56));
        lp.setMargins(0, dp(10), 0, 0);
        side.addView(b, lp);
    }

    private void closeSidebar(FrameLayout frame) {
        for (int i = frame.getChildCount() - 1; i >= 0; i--) {
            View child = frame.getChildAt(i);
            Object tag = child.getTag();
            if (tag != null && "sidebar".equals(tag.toString())) frame.removeViewAt(i);
        }
        sidebarOpen = false;
    }

    private void openExternal(GameEntry g) {
        if (g == null || g.uri == null || g.uri.isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(g.uri), "*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Abrir em emulador/app compatível"));
        } catch (Exception e) {
            Toast.makeText(this, "Nenhum app compatível encontrado.", Toast.LENGTH_LONG).show();
        }
    }

    private void copyLegal() {
        Toast.makeText(this, "Aviso legal: o app não fornece ROMs, ISOs ou BIOS.", Toast.LENGTH_SHORT).show();
    }

    private boolean isPackageInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void launchPackage(String pkg) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) startActivity(launch);
            else Toast.makeText(this, "App não encontrado.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir o app.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir o link.", Toast.LENGTH_SHORT).show();
        }
    }

    private String platformLabel(String id) {
        for (int i = 0; i < PLATFORM_IDS.length; i++) if (PLATFORM_IDS[i].equals(id)) return PLATFORM_LABELS[i];
        return "Escolher depois";
    }

    private String statusLabel(String id) {
        for (int i = 0; i < STATUS_IDS.length; i++) if (STATUS_IDS[i].equals(id)) return STATUS_LABELS[i];
        return "Não testado";
    }

    private String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "CD";
        String cleaned = name.replaceAll("[^A-Za-z0-9À-ÿ ]", " ").trim();
        if (cleaned.isEmpty()) return "CD";
        String[] parts = cleaned.split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    private void loadGames() {
        games.clear();
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String raw = prefs.getString(KEY_GAMES, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) games.add(GameEntry.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) {}
    }

    private void saveGames() {
        JSONArray arr = new JSONArray();
        try {
            for (GameEntry g : games) arr.put(g.toJson());
        } catch (Exception ignored) {}
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_GAMES, arr.toString()).apply();
    }

    @Override
    public void onBackPressed() {
        if (inPlayer) {
            inPlayer = false;
            currentTab = "games";
            renderMain();
            return;
        }
        super.onBackPressed();
    }
}
