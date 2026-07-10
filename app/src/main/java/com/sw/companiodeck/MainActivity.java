package com.sw.companiodeck;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
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
import android.view.MotionEvent;
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
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_ROM = 1201;
    private static final String INTERNAL_VERSION = "v1.2-r3";
    private static final String PREFS = "companion_deck_native_prefs";
    private static final String KEY_GAMES = "games_native_v1";
    private static final String[] PLATFORM_IDS = {"gbc", "gba", "snes", "psp", "ps1", "n64", "cubewii", "ps2", "manual"};
    private static final String[] PLATFORM_LABELS = {"Game Boy / Color", "Game Boy Advance", "SNES", "Portátil PSP", "PS1", "Nintendo 64", "Cube/Wii", "Console PS2", "Escolher depois"};
    private static final String[] STATUS_IDS = {"not-tested", "playable", "lagging", "finished"};
    private static final String[] STATUS_LABELS = {"Não testado", "Jogável", "Travando", "Finalizado"};

    private final ArrayList<GameEntry> games = new ArrayList<>();
    private String currentTab = "games";
    private GameEntry activePlayerGame;
    private CoffeeGbPlayerView activeCoffeeView;
    private boolean inPlayer = false;
    private boolean sidebarOpen = false;
    private boolean controlsVisible = true;
    private boolean playerLandscape = false;
    private FrameLayout activeControlsLayer;

    private LinearLayout content;

    private final int BG = Color.rgb(0, 0, 0);
    private final int CARD = Color.rgb(8, 11, 18);
    private final int CARD_2 = Color.rgb(13, 17, 27);
    private final int LINE = Color.argb(42, 82, 132, 255);
    private final int TEXT = Color.rgb(248, 250, 252);
    private final int MUTED = Color.rgb(172, 181, 197);
    private final int BLUE = Color.rgb(46, 123, 255);
    private final int VIOLET = Color.rgb(112, 77, 255);
    private final int RED = Color.rgb(239, 68, 68);

    static class GameEntry {
        String id, name, platform, fileName, uri, status, notes;
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

    private GradientDrawable accentBg() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{BLUE, VIOLET});
        d.setCornerRadius(dp(22));
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
        b.setTextColor(TEXT);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(48));
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackground(primary ? accentBg() : bg(CARD_2, 18, LINE, 1));
        return b;
    }

    private LinearLayout card(int horizontal, int vertical) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(horizontal), dp(vertical), dp(horizontal), dp(vertical));
        layout.setBackground(bg(CARD, 26, LINE, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(14), dp(7), dp(14), dp(7));
        layout.setLayoutParams(lp);
        return layout;
    }

    private void renderMain() {
        stopActiveCoreIfNeeded();
        inPlayer = false;
        sidebarOpen = false;
        playerLandscape = false;
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        playerLandscape = false;
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        playerLandscape = false;
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        LinearLayout root = new LinearLayout(this);
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
        LinearLayout h = card(20, 20);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        int iconId = getResources().getIdentifier("companiondeck_official_icon", "drawable", getPackageName());
        if (iconId != 0) icon.setImageResource(iconId);
        icon.setBackground(bg(Color.rgb(248, 250, 252), 18, Color.argb(60, 255, 255, 255), 1));
        icon.setPadding(dp(4), dp(4), dp(4), dp(4));
        row.addView(icon, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(14), 0, 0, 0);
        titles.addView(tv("Companion Deck", 34, TEXT, Typeface.BOLD));
        titles.addView(tv("Sua central de jogos locais", 16, MUTED, Typeface.NORMAL));
        row.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        h.addView(row);
        return h;
    }

    private View quickActions() {
        LinearLayout q = new LinearLayout(this);
        q.setOrientation(LinearLayout.VERTICAL);
        q.setPadding(dp(14), dp(5), dp(14), dp(5));

        Button add = btn("Adicionar jogo", true);
        add.setTextSize(17);
        add.setOnClickListener(v -> pickRom());
        q.addView(add, new LinearLayout.LayoutParams(-1, dp(62)));

        Button legal = btn("Aviso de uso", false);
        legal.setOnClickListener(v -> showLegal());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.setMargins(0, dp(9), 0, 0);
        q.addView(legal, lp);
        return q;
    }

    private View tabs() {
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(14), dp(8), dp(14), dp(8));

        addTab(row, "games", "Jogos");
        addTab(row, "player", "Jogar");
        addTab(row, "apps", "Apps");
        addTab(row, "more", "Mais");

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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(128), dp(48));
        lp.setMargins(0, 0, dp(8), 0);
        row.addView(b, lp);
    }

    private void renderTab(String id) {
        content.removeAllViews();
        if ("games".equals(id)) renderGamesTab();
        else if ("player".equals(id)) renderPlayerTab();
        else if ("apps".equals(id)) renderAppsTab();
        else renderMoreTab();
    }

    private void title(String text) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(2));
        box.addView(tv(text, 27, TEXT, Typeface.BOLD));
        content.addView(box);
    }

    private void renderGamesTab() {
        title("Meus jogos");

        if (games.isEmpty()) {
            LinearLayout empty = card(18, 18);
            empty.addView(tv("Nenhum jogo salvo", 21, TEXT, Typeface.BOLD));
            empty.addView(tv("Adicione um arquivo local para começar.", 16, MUTED, Typeface.NORMAL));
            content.addView(empty);
            return;
        }

        TextView count = tv(games.size() == 1 ? "1 jogo salvo" : games.size() + " jogos salvos", 15, MUTED, Typeface.BOLD);
        count.setPadding(dp(18), 0, 0, dp(5));
        content.addView(count);

        for (GameEntry g : games) content.addView(gameCard(g));
    }

    private View gameCard(GameEntry g) {
        LinearLayout c = card(14, 14);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView cover = tv(initials(g.name), 22, TEXT, Typeface.BOLD);
        cover.setGravity(Gravity.CENTER);
        cover.setBackground(bg(Color.rgb(17, 27, 46), 22, Color.argb(52, 46, 123, 255), 1));
        top.addView(cover, new LinearLayout.LayoutParams(dp(86), dp(104)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(14), 0, 0, 0);
        info.addView(tv(platformLabel(g.platform), 13, BLUE, Typeface.BOLD));
        info.addView(tv(g.name, 19, TEXT, Typeface.BOLD));
        info.addView(tv(statusLabel(g.status), 13, MUTED, Typeface.BOLD));
        top.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        c.addView(top);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(12), 0, 0);

        Button play = btn(isCoffeeGbSupported(g) ? "Jogar" : "Abrir", true);
        play.setOnClickListener(v -> {
            if (isCoffeeGbSupported(g)) openPlayer(g);
            else showNoInternalCore(g);
        });
        Button profile = btn("Perfil", false);
        profile.setOnClickListener(v -> showProfile(g));
        Button ext = btn("Externo", false);
        ext.setOnClickListener(v -> openExternal(g));

        actions.addView(play, new LinearLayout.LayoutParams(0, dp(46), 1.15f));
        actions.addView(profile, new LinearLayout.LayoutParams(0, dp(46), 1f));
        actions.addView(ext, new LinearLayout.LayoutParams(0, dp(46), 1f));
        c.addView(actions);
        return c;
    }

    private void renderPlayerTab() {
        title("Jogar");
        LinearLayout info = card(18, 18);
        info.addView(tv("Player interno", 22, TEXT, Typeface.BOLD));
        info.addView(tv("Abra um jogo salvo para entrar em tela cheia.", 16, MUTED, Typeface.NORMAL));
        content.addView(info);

        if (!games.isEmpty()) {
            Button b = btn("Abrir último jogo", true);
            b.setOnClickListener(v -> {
                GameEntry latest = games.get(0);
                if (isCoffeeGbSupported(latest)) openPlayer(latest);
                else showNoInternalCore(latest);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56));
            lp.setMargins(dp(14), dp(8), dp(14), dp(8));
            content.addView(b, lp);
        }
    }

    private void renderAppsTab() {
        title("Apps compatíveis");
        LinearLayout intro = card(18, 18);
        intro.addView(tv("Fallback externo", 21, TEXT, Typeface.BOLD));
        intro.addView(tv("Use quando um sistema ainda não abrir dentro do Companion Deck.", 16, MUTED, Typeface.NORMAL));
        content.addView(intro);

        launcherCard("RetroArch / Libretro", "Sistemas clássicos", "com.retroarch", "https://www.retroarch.com/");
        launcherCard("PPSSPP", "Portátil PSP", "org.ppsspp.ppsspp", "https://www.ppsspp.org/");
        launcherCard("Dolphin", "Cube/Wii", "org.dolphinemu.dolphinemu", "https://dolphin-emu.org/");
    }

    private void launcherCard(String name, String desc, String pkg, String url) {
        LinearLayout c = card(16, 16);
        boolean installed = isPackageInstalled(pkg);
        c.addView(tv(name, 20, TEXT, Typeface.BOLD));
        c.addView(tv((installed ? "Instalado" : "Opcional") + " • " + desc, 15, MUTED, Typeface.NORMAL));

        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(10), 0, 0);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button open = btn("Abrir", installed);
        open.setEnabled(installed);
        open.setOnClickListener(v -> launchPackage(pkg));
        Button site = btn("Site", false);
        site.setOnClickListener(v -> openUrl(url));
        row.addView(open, new LinearLayout.LayoutParams(0, dp(46), 1f));
        row.addView(site, new LinearLayout.LayoutParams(0, dp(46), 1f));
        c.addView(row);
        content.addView(c);
    }

    private void renderMoreTab() {
        title("Mais");

        Button settings = btn("Preferências de interface", false);
        settings.setOnClickListener(v -> Toast.makeText(this, "Preferências completas entram na próxima fase.", Toast.LENGTH_SHORT).show());
        addFullButton(settings);

        Button compatibility = btn("Compatibilidade dos sistemas", false);
        compatibility.setOnClickListener(v -> showCompatibility());
        addFullButton(compatibility);

        Button legal = btn("Uso responsável", false);
        legal.setOnClickListener(v -> showLegal());
        addFullButton(legal);

        Button about = btn("Informações técnicas", false);
        about.setOnClickListener(v -> showTechnicalInfo());
        addFullButton(about);
    }

    private void addFullButton(Button b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.setMargins(dp(14), dp(7), dp(14), dp(7));
        content.addView(b, lp);
    }


    private void stopActiveCoreIfNeeded() {
        if (activeCoffeeView != null) {
            activeCoffeeView.stopEmulation();
            activeCoffeeView = null;
        }
        activeControlsLayer = null;
    }
    private boolean isCoffeeGbSupported(GameEntry g) {
        if (g == null) return false;
        String file = g.fileName == null ? "" : g.fileName.toLowerCase(Locale.ROOT);
        boolean validGbFile = file.endsWith(".gb") || file.endsWith(".gbc") || file.endsWith(".zip");
        return "gbc".equals(g.platform) && validGbFile;
    }
    private void showNoInternalCore(GameEntry g) {
        String system = g == null ? "este sistema" : platformLabel(g.platform);
        String file = g == null ? "" : g.fileName;

        new AlertDialog.Builder(this)
                .setTitle("Motor interno indisponível")
                .setMessage("O Companion Deck só abre internamente sistemas que já têm motor real ligado. Agora o motor real disponível é Game Boy / Color para arquivos .gb, .gbc ou .zip contendo uma ROM GB/GBC.\n\nSistema atual: " + system + "\nArquivo: " + file)
                .setPositiveButton("Abrir externo", (d, w) -> openExternal(g))
                .setNeutralButton("Perfil", (d, w) -> {
                    if (g != null) showProfile(g);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }





    private void addGameControls(FrameLayout frame, CoffeeGbPlayerView view) {
        if (frame.findViewWithTag("realControls") != null) return;
        FrameLayout layer = new FrameLayout(this);
        layer.setTag("realControls");
        activeControlsLayer = layer;
        layer.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
        frame.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout dpad = new LinearLayout(this);
        dpad.setOrientation(LinearLayout.VERTICAL);
        dpad.setGravity(Gravity.CENTER);

        Button up = controlButton("▲", view, eu.rekawek.coffeegb.core.joypad.Button.UP);
        Button down = controlButton("▼", view, eu.rekawek.coffeegb.core.joypad.Button.DOWN);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.HORIZONTAL);
        Button left = controlButton("◀", view, eu.rekawek.coffeegb.core.joypad.Button.LEFT);
        Button center = btn("●", false);
        center.setEnabled(false);
        Button right = controlButton("▶", view, eu.rekawek.coffeegb.core.joypad.Button.RIGHT);
        mid.addView(left, new LinearLayout.LayoutParams(dp(52), dp(52)));
        mid.addView(center, new LinearLayout.LayoutParams(dp(52), dp(52)));
        mid.addView(right, new LinearLayout.LayoutParams(dp(52), dp(52)));

        dpad.addView(up, new LinearLayout.LayoutParams(dp(52), dp(52)));
        dpad.addView(mid);
        dpad.addView(down, new LinearLayout.LayoutParams(dp(52), dp(52)));

        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(170), dp(170), Gravity.BOTTOM | Gravity.LEFT);
        dlp.setMargins(dp(18), 0, 0, dp(42));
        layer.addView(dpad, dlp);

        LinearLayout ab = new LinearLayout(this);
        ab.setOrientation(LinearLayout.HORIZONTAL);
        ab.setGravity(Gravity.CENTER);
        ab.addView(controlButton("B", view, eu.rekawek.coffeegb.core.joypad.Button.B), new LinearLayout.LayoutParams(dp(64), dp(64)));
        ab.addView(controlButton("A", view, eu.rekawek.coffeegb.core.joypad.Button.A), new LinearLayout.LayoutParams(dp(64), dp(64)));
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(dp(150), dp(80), Gravity.BOTTOM | Gravity.RIGHT);
        alp.setMargins(0, 0, dp(22), dp(78));
        layer.addView(ab, alp);

        LinearLayout startSelect = new LinearLayout(this);
        startSelect.setOrientation(LinearLayout.HORIZONTAL);
        startSelect.setGravity(Gravity.CENTER);
        startSelect.addView(controlButton("SELECT", view, eu.rekawek.coffeegb.core.joypad.Button.SELECT), new LinearLayout.LayoutParams(dp(94), dp(42)));
        startSelect.addView(controlButton("START", view, eu.rekawek.coffeegb.core.joypad.Button.START), new LinearLayout.LayoutParams(dp(94), dp(42)));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(dp(210), dp(52), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        slp.setMargins(0, 0, 0, dp(24));
        layer.addView(startSelect, slp);
    }

    private Button controlButton(String label, CoffeeGbPlayerView view, eu.rekawek.coffeegb.core.joypad.Button gbButton) {
        Button b = btn(label, false);
        b.setTextSize(label.length() > 2 ? 11 : 18);
        b.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                view.setButtonPressed(gbButton, true);
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_UP) {
                view.setButtonPressed(gbButton, false);
                return true;
            }
            return true;
        });
        return b;
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
        persistRomUriPermission(uri);
        g.platform = guessPlatform(fileName, uri);
        g.status = "not-tested";
        g.notes = "";
        g.updatedAt = System.currentTimeMillis();

        games.add(0, g);
        saveGames();
        currentTab = "games";
        renderMain();
        Toast.makeText(this, "Jogo salvo", Toast.LENGTH_SHORT).show();
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
        name = name.replace('_', ' ').trim();
        return name.isEmpty() ? "Jogo" : name;
    }
    private void persistRomUriPermission(Uri uri) {
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {
            // Alguns gerenciadores de arquivo não entregam permissão persistível.
            // Nesse caso o app continua funcionando na sessão atual e mostra erro real se o Android negar depois.
        }
    }
    private String guessPlatform(String fileName, Uri uri) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".gb") || lower.endsWith(".gbc")) return "gbc";
        if (lower.endsWith(".zip") && zipContainsGbRom(uri)) return "gbc";
        if (lower.endsWith(".gba")) return "gba";
        if (lower.endsWith(".sfc") || lower.endsWith(".smc")) return "snes";
        if (lower.endsWith(".cso")) return "psp";
        if (lower.endsWith(".z64") || lower.endsWith(".n64") || lower.endsWith(".v64")) return "n64";
        if (lower.endsWith(".gcm") || lower.endsWith(".wbfs") || lower.endsWith(".rvz")) return "cubewii";
        if (lower.endsWith(".iso")) return "manual";
        return "manual";
    }
    private boolean zipContainsGbRom(Uri uri) {
        if (uri == null) return false;
        try (InputStream raw = getContentResolver().openInputStream(uri);
             ZipInputStream zip = raw == null ? null : new ZipInputStream(raw)) {
            if (zip == null) return false;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName() == null ? "" : entry.getName().toLowerCase(Locale.ROOT);
                if (!entry.isDirectory() && (name.endsWith(".gb") || name.endsWith(".gbc"))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }





    private void showProfile(GameEntry g) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);

        EditText name = new EditText(this);
        name.setText(g.name);
        name.setTextColor(TEXT);
        name.setHintTextColor(MUTED);
        box.addView(label("Nome"));
        box.addView(name);

        Spinner platform = new Spinner(this);
        platform.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, PLATFORM_LABELS));
        platform.setSelection(indexOf(PLATFORM_IDS, g.platform));
        box.addView(label("Sistema"));
        box.addView(platform);

        Spinner status = new Spinner(this);
        status.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, STATUS_LABELS));
        status.setSelection(indexOf(STATUS_IDS, g.status));
        box.addView(label("Status"));
        box.addView(status);

        EditText notes = new EditText(this);
        notes.setText(g.notes);
        notes.setMinLines(3);
        notes.setTextColor(TEXT);
        notes.setHint("Notas");
        notes.setHintTextColor(MUTED);
        box.addView(label("Notas"));
        box.addView(notes);

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
                .setNeutralButton("Remover", (d, which) -> removeGame(g))
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
        games.remove(g);
        saveGames();
        renderMain();
        Toast.makeText(this, "Jogo removido", Toast.LENGTH_SHORT).show();
    }

    private void openPlayer(GameEntry g) {
        activePlayerGame = g;
        controlsVisible = true;
        playerLandscape = false;
        activeControlsLayer = null;
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        if (!isCoffeeGbSupported(g)) {
            showNoInternalCore(g);
            return;
        }
        inPlayer = true;
        sidebarOpen = false;

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);

        CoffeeGbPlayerView coffee = new CoffeeGbPlayerView(this);
        activeCoffeeView = coffee;
        frame.addView(coffee, new FrameLayout.LayoutParams(-1, -1));
        addGameControls(frame, coffee);
        coffee.loadGame(Uri.parse(g.uri), g.name);

        Button menu = btn("☰", false);
        menu.setTextSize(22);
        menu.setOnClickListener(v -> showSidebar(frame));
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.TOP | Gravity.RIGHT);
        mlp.setMargins(0, dp(16), dp(16), 0);
        frame.addView(menu, mlp);

        setContentView(frame);
    }


    private void showControlPanel(FrameLayout frame) {
        if (activeCoffeeView == null) {
            Toast.makeText(this, "Abra um jogo GB/GBC primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (activeControlsLayer == null) {
            addGameControls(frame, activeCoffeeView);
        }

        String controlsLabel = controlsVisible ? "Ocultar controles" : "Mostrar controles";
        String scaleLabel = "Tela: " + activeCoffeeView.getScaleModeLabel();
        String orientationLabel = playerLandscape ? "Orientação: Vertical" : "Orientação: Horizontal";
        String[] options = new String[] { controlsLabel, scaleLabel, orientationLabel, "Continuar" };

        new AlertDialog.Builder(this)
                .setTitle("Controles GB/GBC")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        controlsVisible = !controlsVisible;
                        if (activeControlsLayer != null) {
                            activeControlsLayer.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
                        }
                        Toast.makeText(this, controlsVisible ? "Controles visíveis" : "Controles ocultos", Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        activeCoffeeView.cycleScaleMode();
                        Toast.makeText(this, "Tela: " + activeCoffeeView.getScaleModeLabel(), Toast.LENGTH_SHORT).show();
                    } else if (which == 2) {
                        playerLandscape = !playerLandscape;
                        if (playerLandscape) {
                            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                            Toast.makeText(this, "Modo horizontal ativado", Toast.LENGTH_SHORT).show();
                        } else {
                            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                            Toast.makeText(this, "Modo vertical/automático", Toast.LENGTH_SHORT).show();
                        }
                        closeSidebar(frame);
                    } else {
                        closeSidebar(frame);
                    }
                })
                .show();
    }

    private void showSidebar(FrameLayout frame) {
        if (sidebarOpen) return;
        sidebarOpen = true;

        View dim = new View(this);
        dim.setBackgroundColor(Color.argb(138, 0, 0, 0));
        dim.setTag("sidebar");
        dim.setOnClickListener(v -> closeSidebar(frame));
        frame.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout side = new LinearLayout(this);
        side.setTag("sidebar");
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(dp(18), dp(26), dp(18), dp(18));
        side.setBackgroundColor(Color.rgb(5, 7, 12));

        side.addView(tv("Player", 28, TEXT, Typeface.BOLD));

        Button resume = btn("Continuar", true);
        resume.setOnClickListener(v -> closeSidebar(frame));
        Button config = btn("Controles", false);
        config.setOnClickListener(v -> showControlPanel(frame));
        Button external = btn("Abrir externo", false);
        external.setOnClickListener(v -> openExternal(activePlayerGame));
        Button library = btn("Biblioteca", false);
        library.setOnClickListener(v -> {
            inPlayer = false;
            currentTab = "games";
            renderMain();
        });
        Button exit = btn("Sair", false);
        exit.setTextColor(Color.rgb(248, 113, 113));
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

        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(dp(300), -1, Gravity.RIGHT);
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
            startActivity(Intent.createChooser(intent, "Abrir em app compatível"));
        } catch (Exception e) {
            Toast.makeText(this, "Nenhum app compatível encontrado.", Toast.LENGTH_LONG).show();
        }
    }

    private void showCompatibility() {
        StringBuilder sb = new StringBuilder();
        for (NativeCoreBridge.CoreInfo info : NativeCoreBridge.allCores()) {
            if ("manual".equals(info.platformId)) continue;
            sb.append(info.platformLabel).append("\n")
              .append(info.userStatus).append("\n")
              .append(info.detailStatus).append("\n\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("Compatibilidade")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private void showLegal() {
        new AlertDialog.Builder(this)
                .setTitle("Uso responsável")
                .setMessage("O Companion Deck não fornece jogos, ROMs, ISOs ou BIOS. Adicione apenas arquivos que você possui legalmente. O app mantém a proposta offline-first e usa apps externos apenas como fallback quando necessário.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showTechnicalInfo() {
        new AlertDialog.Builder(this)
                .setTitle("Informações técnicas")
                .setMessage("Companion Deck " + INTERNAL_VERSION + "\nInterface nativa AMOLED\nRuntime nativo preparado\nSem WebView como UI principal\nSem core web/CDN\nGB/GBC com core interno real experimental")
                .setPositiveButton("OK", null)
                .show();
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
