package uz.uptimestudio.uptime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
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
 * при запуске (экран загрузки с процентами) в files/www, без переустановки APK. Качаются только изменившиеся файлы.
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
        if (Build.VERSION.SDK_INT >= 28) { // рисуем и под вырезом камеры: игра на весь экран
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
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
        web.loadDataWithBaseURL(HOST + "loader.html", LOADER, "text/html", "utf-8", null);
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

    static final String LOADER = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1,viewport-fit=cover'><style>"
            + "html,body{margin:0;height:100%;background:#0b0f17;color:#e6edf3;font-family:sans-serif;overflow:hidden}"
            + ".w{height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:22px}"
            + "h1{margin:0;font-size:34px;letter-spacing:6px;font-weight:800}"
            + ".b{display:flex;gap:7px;align-items:flex-end;height:64px}"
            + ".b i{width:10px;height:14px;border-radius:5px;background:#2ea043;box-shadow:0 0 12px #2ea04399;animation:j .8s ease-in-out infinite}"
            + ".b i:nth-child(2){animation-delay:.1s}.b i:nth-child(3){animation-delay:.2s}.b i:nth-child(4){animation-delay:.3s}.b i:nth-child(5){animation-delay:.4s}.b i:nth-child(6){animation-delay:.5s}.b i:nth-child(7){animation-delay:.6s}"
            + "@keyframes j{0%,100%{height:14px;opacity:.55}50%{height:60px;opacity:1}}"
            + ".p{width:70%;max-width:320px;height:10px;border-radius:5px;background:#1c2330;overflow:hidden}"
            + "#f{height:100%;width:0;background:linear-gradient(90deg,#2ea043,#56d364);border-radius:5px;transition:width .2s}"
            + "#n{font-size:26px;font-weight:800;color:#56d364;min-height:32px}#t{font-size:14px;color:#8b949e}"
            + ".ok .b i{animation:none;height:60px}.ok #t{color:#56d364;font-weight:700;font-size:18px}"
            + "</style></head><body><div class='w' id='w'><h1>АПТАЙМ</h1><div class='b'><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div>"
            + "<div id='n'></div><div class='p'><div id='f'></div></div><div id='t'>Проверка обновлений…</div></div>"
            + "<script>function st(t){document.getElementById('t').textContent=t}"
            + "function pr(p){document.getElementById('f').style.width=p+'%';document.getElementById('n').textContent=p+'%'}"
            + "function ok(){pr(100);document.getElementById('w').className='w ok';st('✓ Успешно обновлено')}</script></body></html>";

    private void js(String code) { runOnUiThread(() -> { if (web != null) web.evaluateJavascript(code, null); }); }

    private void startGame(long delayMs) {
        runOnUiThread(() -> web.postDelayed(() -> { web.loadUrl(HOST + "index.html"); web.postDelayed(() -> web.clearHistory(), 1500); }, delayMs));
    }

    private JSONObject currentHashes() {
        try {
            File f = new File(ota, "version.json");
            try (InputStream in = f.exists() ? new FileInputStream(f) : getAssets().open("www/version.json")) {
                JSONObject h = new JSONObject(new String(readAll(in), "UTF-8")).optJSONObject("hash");
                return h != null ? h : new JSONObject();
            }
        } catch (Exception e) { return new JSONObject(); }
    }

    /** При запуске: проверяем версию, качаем изменившиеся файлы с прогрессом, подменяем www и запускаем игру. */
    private void checkUpdates() {
        long wait = 0;
        try {
            JSONObject v = new JSONObject(new String(get(PAGES + "version.json?t=" + System.currentTimeMillis(), 4000, null), "UTF-8"));
            int webV = v.optInt("web", 0), apkV = v.optInt("apk", 0);
            if (apkV > BuildConfig.VERSION_CODE && !BuildConfig.PLAY) { // в сборке для Google Play приложение обновляет только сам магазин
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("Новая версия АПТАЙМ")
                        .setMessage("Вышло обновление приложения. Скачать и установить?")
                        .setPositiveButton("Скачать", (d, w) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL))))
                        .setNegativeButton("Позже", null).show());
            }
            int cur = Math.max(prefs.getInt("web", 0), bundledWeb());
            if (webV > cur) {
                js("st('Загрузка обновления…');pr(0)");
                File tmp = new File(getFilesDir(), "www_new");
                deleteDir(tmp);
                JSONArray files = v.getJSONArray("files");
                JSONObject nh = v.optJSONObject("hash"), oh = currentHashes();
                final int n = files.length();
                final int[] last = {-1};
                for (int i = 0; i < n; i++) {
                    String rel = files.getString(i);
                    if (rel.contains("..")) continue;
                    File out = new File(tmp, rel);
                    out.getParentFile().mkdirs();
                    String h = nh != null ? nh.optString(rel, "") : "";
                    final int idx = i;
                    Progress pg = frac -> { int p = Math.min(99, (int) ((idx + frac) * 100 / n)); if (p != last[0]) { last[0] = p; js("pr(" + p + ")"); } };
                    boolean copied = false;
                    if (!h.isEmpty() && h.equals(oh.optString(rel, ""))) { // файл не менялся — берём локальный
                        try {
                            File f = new File(ota, rel);
                            try (InputStream in = f.exists() ? new FileInputStream(f) : getAssets().open("www/" + rel);
                                 OutputStream os = new FileOutputStream(out)) { os.write(readAll(in)); }
                            copied = true;
                        } catch (Exception ignored) { }
                    }
                    if (!copied) try (OutputStream os = new FileOutputStream(out)) { os.write(get(PAGES + rel + "?v=" + webV, 8000, pg)); }
                    pg.at(1);
                }
                try (OutputStream os = new FileOutputStream(new File(tmp, "version.json"))) { os.write(v.toString().getBytes("UTF-8")); }
                deleteDir(ota); // игра ещё не загружена, можно подменить сразу
                if (!tmp.renameTo(ota)) throw new Exception("rename");
                prefs.edit().putInt("web", webV).apply();
                js("ok()");
                wait = 900;
            }
        } catch (Exception e) {
            deleteDir(new File(getFilesDir(), "www_new"));
            // нет интернета или обрыв — играем во встроенную или ранее скачанную версию
        }
        startGame(wait);
    }

    interface Progress { void at(double frac); }

    private static byte[] get(String url, int connectMs, Progress pg) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(connectMs);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        long total = c.getContentLength();
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) { bo.write(b, 0, n); if (pg != null && total > 0) pg.at(Math.min(1, bo.size() / (double) total)); }
            return bo.toByteArray();
        }
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
    @Override protected void onPause() {
        super.onPause();
        if (web != null) { web.evaluateJavascript("window.uptimePause&&uptimePause()", null); web.onPause(); web.pauseTimers(); }
    }
    @Override protected void onResume() {
        super.onResume();
        if (web != null) { web.resumeTimers(); web.onResume(); web.evaluateJavascript("window.uptimeResume&&uptimeResume()", null); }
    }
    @Override public void onBackPressed() {
        if (web == null) { moveTaskToBack(true); return; }
        web.evaluateJavascript("(window.uptimeBack&&window.uptimeBack())?'1':'0'", v -> {
            if (v == null || !v.contains("1")) moveTaskToBack(true);
        });
    }
}
