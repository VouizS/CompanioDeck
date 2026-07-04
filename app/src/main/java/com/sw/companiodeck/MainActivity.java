package com.sw.companiodeck;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.JavascriptInterface;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.Window;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_ROM = 701;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(0xFF070A12);
        window.setNavigationBarColor(0xFF070A12);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(false);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null) {
                    Uri uri = request.getUrl();
                    return handleExternalUrl(uri);
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url == null) return false;
                return handleExternalUrl(Uri.parse(url));
            }
        });

        webView.addJavascriptInterface(new NativeBridge(), "CompanionDeckNative");
        webView.loadUrl("file:///android_asset/web/index.html");
    }

    private boolean handleExternalUrl(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception e) {
                Toast.makeText(this, "Não foi possível abrir o link.", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return false;
    }

    public class NativeBridge {
        @JavascriptInterface
        public String getVersionName() {
            return "v0.1";
        }

        @JavascriptInterface
        public void pickRom() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                            "application/octet-stream",
                            "application/zip",
                            "application/x-iso9660-image",
                            "application/x-cd-image"
                    });
                    try {
                        startActivityForResult(intent, REQUEST_PICK_ROM);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Seletor de arquivos indisponível.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void copyToClipboard(String label, String text) {
            final String safeLabel = label == null ? "Companion Deck" : label;
            final String safeText = text == null ? "" : text;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText(safeLabel, safeText));
                        Toast.makeText(MainActivity.this, "Copiado.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void openOfficialUrl(String url) {
            if (url == null || url.trim().isEmpty()) return;
            final String safeUrl = url.trim();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Não foi possível abrir o link.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void toast(String message) {
            final String msg = message == null ? "" : message;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_ROM && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;

            final int flags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }

            String displayName = getDisplayName(uri);
            String uriText = uri.toString();
            String platformGuess = guessPlatform(displayName);

            String js = "window.CompanionDeckUI && window.CompanionDeckUI.onRomPicked("
                    + jsString(displayName) + ","
                    + jsString(uriText) + ","
                    + jsString(platformGuess) + ");";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                webView.evaluateJavascript(js, null);
            } else {
                webView.loadUrl("javascript:" + js);
            }
        }
    }

    private String getDisplayName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
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
        }
        if (result == null || result.trim().isEmpty()) {
            result = uri.getLastPathSegment();
        }
        if (result == null || result.trim().isEmpty()) {
            result = "Jogo selecionado";
        }
        return result;
    }

    private String guessPlatform(String name) {
        if (name == null) return "manual";
        String lower = name.toLowerCase();
        if (lower.endsWith(".gba")) return "gba";
        if (lower.endsWith(".gb")) return "gb";
        if (lower.endsWith(".gbc")) return "gbc";
        if (lower.endsWith(".nes")) return "nes";
        if (lower.endsWith(".sfc") || lower.endsWith(".smc")) return "snes";
        if (lower.endsWith(".md") || lower.endsWith(".gen") || lower.endsWith(".smd")) return "megadrive";
        if (lower.endsWith(".z64") || lower.endsWith(".n64") || lower.endsWith(".v64")) return "n64";
        if (lower.endsWith(".cso")) return "psp";
        if (lower.endsWith(".iso")) return "iso-manual";
        if (lower.endsWith(".bin") || lower.endsWith(".cue")) return "ps1-manual";
        if (lower.endsWith(".zip")) return "archive-manual";
        return "manual";
    }

    private String jsString(String value) {
        if (value == null) return "''";
        String escaped = value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
        return "'" + escaped + "'";
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }
}
