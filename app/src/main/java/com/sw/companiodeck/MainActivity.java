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
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.Window;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_ROM = 701;
    private static final int REQUEST_PICK_COVER = 702;

    private WebView webView;
    private String pendingCoverGameId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(0xFF070807);
        window.setNavigationBarColor(0xFF070807);

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
                    return handleExternalUrl(request.getUrl());
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
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) || "market".equalsIgnoreCase(scheme)) {
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
            return "v0.6";
        }

        @JavascriptInterface
        public void pickRom() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                            "application/octet-stream",
                            "application/zip",
                            "application/x-zip-compressed",
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
        public void pickCover(String gameId) {
            pendingCoverGameId = gameId == null ? "" : gameId;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    try {
                        startActivityForResult(intent, REQUEST_PICK_COVER);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Seletor de imagem indisponível.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public boolean isPackageInstalled(String packageName) {
            if (packageName == null || packageName.trim().isEmpty()) return false;
            try {
                PackageManager pm = getPackageManager();
                pm.getPackageInfo(packageName.trim(), 0);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void launchPackage(String packageName) {
            if (packageName == null || packageName.trim().isEmpty()) return;
            final String safePackage = packageName.trim();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent launch = getPackageManager().getLaunchIntentForPackage(safePackage);
                        if (launch != null) {
                            startActivity(launch);
                        } else {
                            Toast.makeText(MainActivity.this, "App não encontrado no aparelho.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Não foi possível abrir o app.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void openContentUri(String uriText) {
            if (uriText == null || uriText.trim().isEmpty()) return;
            final String safeUri = uriText.trim();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Uri uri = Uri.parse(safeUri);
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, "*/*");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        Intent chooser = Intent.createChooser(intent, "Abrir com emulador/app compatível");
                        startActivity(chooser);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Nenhum app compatível encontrado para abrir este arquivo.", Toast.LENGTH_LONG).show();
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
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)));
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
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        persistReadPermission(uri);

        if (requestCode == REQUEST_PICK_ROM) {
            String displayName = getDisplayName(uri);
            String uriText = uri.toString();
            String platformGuess = guessPlatform(displayName);

            String js = "window.CompanionDeckUI && window.CompanionDeckUI.onRomPicked("
                    + jsString(displayName) + ","
                    + jsString(uriText) + ","
                    + jsString(platformGuess) + ");";
            runJs(js);
            return;
        }

        if (requestCode == REQUEST_PICK_COVER) {
            String js = "window.CompanionDeckUI && window.CompanionDeckUI.onCoverPicked("
                    + jsString(pendingCoverGameId) + ","
                    + jsString(uri.toString()) + ");";
            pendingCoverGameId = "";
            runJs(js);
        }
    }

    private void persistReadPermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    private void runJs(String js) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(js, null);
        } else {
            webView.loadUrl("javascript:" + js);
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
        if (result == null || result.trim().isEmpty()) result = uri.getLastPathSegment();
        if (result == null || result.trim().isEmpty()) result = "Jogo selecionado";
        return result;
    }

    private String guessPlatform(String name) {
        if (name == null) return "manual";
        String lower = name.toLowerCase();
        if (lower.endsWith(".gba")) return "gba";
        if (lower.endsWith(".gb")) return "gbc";
        if (lower.endsWith(".gbc")) return "gbc";
        if (lower.endsWith(".nes")) return "nes";
        if (lower.endsWith(".sfc") || lower.endsWith(".smc")) return "snes";
        if (lower.endsWith(".md") || lower.endsWith(".gen") || lower.endsWith(".smd")) return "megadrive";
        if (lower.endsWith(".z64") || lower.endsWith(".n64") || lower.endsWith(".v64")) return "n64";
        if (lower.endsWith(".cso")) return "psp";
        if (lower.endsWith(".iso")) return "manual";
        if (lower.endsWith(".bin") || lower.endsWith(".cue")) return "manual";
        if (lower.endsWith(".zip")) return "manual";
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
        String js = "window.CompanionDeckUI && window.CompanionDeckUI.closeProfileIfOpen && window.CompanionDeckUI.closeProfileIfOpen();";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(js, null);
        }

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }
}
