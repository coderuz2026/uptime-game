package uz.defactum.uptime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * АПТАЙМ: WebView-оболочка.
 * Игра лежит в assets/www (встроенная версия). Обновления игры скачиваются с GitHub Pages
 * в files/www и применяются при следующем запуске, без переустановки APK.
 * Если вышел новый APK (изменилась нативная часть), приложение предложит его скачать.
 */
public class MainActivity extends Activity {
    static final String PAGES = "https://coderuz2026.github.io/uptime-game/";
    static final String APK_URL = "https://github.com/coderuz2026/uptime-game/releases/latest/download/uptime.apk";
    static final String HOST = "https://appassets.androidplatform.net/game/";

    private WebView web;
    private File ota;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("uptime", MODE_PRIVATE);
        ota = new File(getFilesDir(), "www");
        applyPendingUpdate();
        // если встроенная версия новее скачанной (поставили свежий APK), удаляем старое обновление
        if (prefs.getInt("web", 0) < bundledWeb()) { deleteDir(ota); prefs.edit().putInt("web", bundledWeb()).apply(); }

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/game/", this::serve)
                .build();

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, u));
                return true;
            }
        });
        setContentView(web);
        hideBars();
        web.loadUrl(HOST + "index.html");
        new Thread(this::checkUpdates).start();
    }

    private WebResourceResponse serve(String path) {
        if (path.isEmpty()) path = "index.html";
        try {
            File f = new File(ota, path);
            InputStream in = (f.exists() && f.getCanonicalPath().startsWith(ota.getCanonicalPath()))
                    ? new FileInputStream(f) : getAssets().open("www/" + path);
            return new WebResourceResponse(mime(path), "utf-8", in);
        } catch (Exception e) {
            return null;
        }
    }

    private static String mime(String p) {
        p = p.toLowerCase();
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js") || p.endsWith(".mjs")) return "text/javascript";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".mp3")) return "audio/mpeg";
        return "application/octet-stream";
    }

    private int bundledWeb() {
        try (InputStream in = getAssets().open("www/version.json")) {
            return new JSONObject(new String(readAll(in), "UTF-8")).optInt("web", 0);
        } catch (Exception e) { return 0; }
    }

    /** Скачанное в прошлый раз обновление лежит в www_new — подменяем им www до загрузки страницы. */
    private void applyPendingUpdate() {
        File pend = new File(getFilesDir(), "www_new");
        File ready = new File(pend, ".ready");
        if (pend.isDirectory() && ready.exists()) {
            ready.delete();
            deleteDir(ota);
            if (pend.renameTo(ota)) prefs.edit().putInt("web", prefs.getInt("web_pending", 0)).apply();
        } else if (pend.exists()) deleteDir(pend);
    }

    private void checkUpdates() {
        try {
            JSONObject v = new JSONObject(new String(get(PAGES + "version.json?t=" + System.currentTimeMillis()), "UTF-8"));
            int webV = v.optInt("web", 0), apkV = v.optInt("apk", 0);
            if (apkV > BuildConfig.VERSION_CODE) {
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("Новая версия АПТАЙМ")
                        .setMessage("Вышло обновление приложения. Скачать и установить?")
                        .setPositiveButton("Скачать", (d, w) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL))))
                        .setNegativeButton("Позже", null).show());
            }
            int cur = Math.max(prefs.getInt("web", 0), bundledWeb());
            if (webV > cur) {
                File tmp = new File(getFilesDir(), "www_new");
                deleteDir(tmp);
                JSONArray files = v.getJSONArray("files");
                for (int i = 0; i < files.length(); i++) {
                    String rel = files.getString(i);
                    if (rel.contains("..")) continue;
                    File out = new File(tmp, rel);
                    out.getParentFile().mkdirs();
                    try (OutputStream os = new FileOutputStream(out)) { os.write(get(PAGES + rel + "?v=" + webV)); }
                }
                try (OutputStream os = new FileOutputStream(new File(tmp, "version.json"))) { os.write(v.toString().getBytes("UTF-8")); }
                new File(tmp, ".ready").createNewFile();
                prefs.edit().putInt("web_pending", webV).apply();
                runOnUiThread(() -> Toast.makeText(this, "Обновление игры скачано. Оно применится при следующем запуске.", Toast.LENGTH_LONG).show());
            }
        } catch (Exception ignored) {
            // нет интернета — играем во встроенную или ранее скачанную версию
        }
    }

    private static byte[] get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        try (InputStream in = c.getInputStream()) { return readAll(in); }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        return bo.toByteArray();
    }

    private static void deleteDir(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteDir(k);
        f.delete();
    }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideBars(); }
    @Override protected void onPause() { super.onPause(); if (web != null) web.onPause(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override public void onBackPressed() {
        if (web == null) { moveTaskToBack(true); return; }
        web.evaluateJavascript("(window.uptimeBack&&window.uptimeBack())?'1':'0'", v -> {
            if (v == null || !v.contains("1")) moveTaskToBack(true);
        });
    }
}
