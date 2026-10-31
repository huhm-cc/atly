package cc.huhm.atly;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

/**
 * atly 自動更新套件
 * - 只在使用者手動開啟 App 時檢查 GitHub Release
 * - 有新版：顯示「更新中」→ 建立 fetch-data.json 保存所有設定與資料 → 下載新版 APK → 開啟安裝
 * - 每次啟動先找 fetch-data.json：有的話顯示「載入資料中，請稍後」→ 還原 → 刪除檔案 → 顯示更新內容
 */
public class AtlyUpdater {
    public static final String REPO = "huhm-cc/atly";
    public static final String DATA_FILE = "fetch-data.json";
    private static final String PREFS = "atly";

    private final Activity act;
    private final WebView web;
    private final FrameLayout root;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private View overlay;
    private TextView overlayText;
    private ProgressBar overlayBar;

    public interface Done { void run(); }

    public AtlyUpdater(Activity act, WebView web, FrameLayout root) {
        this.act = act; this.web = web; this.root = root;
    }

    private File dataFile() { return new File(act.getFilesDir(), DATA_FILE); }

    // ---------- 1. 安裝後：尋找 fetch-data.json ----------
    public boolean hasPendingRestore() { return dataFile().exists(); }

    /** 在網頁載入完成後呼叫：還原 localStorage 與設定，刪除檔案並顯示更新內容 */
    public void restore(Done reload) {
        showOverlay("載入資料中，請稍後", true);
        new Thread(() -> {
            try {
                JSONObject d = new JSONObject(readFile(dataFile()));
                // SharedPreferences
                JSONObject prefs = d.optJSONObject("prefs");
                if (prefs != null) {
                    SharedPreferences.Editor e = act.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
                    for (Iterator<String> it = prefs.keys(); it.hasNext(); ) { String k = it.next(); e.putString(k, prefs.optString(k)); }
                    e.apply();
                }
                // localStorage
                JSONObject ls = d.optJSONObject("localStorage");
                String js = ls == null ? "" : "(function(d){for(var k in d){localStorage.setItem(k,d[k])}})(" + ls.toString() + ");";
                String from = d.optString("fromVersion"), to = d.optString("toVersion"), notes = d.optString("notes");
                ui.post(() -> web.evaluateJavascript(js + "true", r -> {
                    //  載入完成：立刻刪除 fetch-data.json
                    dataFile().delete();
                    hideOverlay();
                    reload.run();
                    showWhatsNew(from, to, notes);
                }));
            } catch (Exception e) {
                dataFile().delete();
                ui.post(() -> { hideOverlay(); toastDialog("資料還原失敗：" + e.getMessage()); });
            }
        }).start();
    }

    private void showWhatsNew(String from, String to, String notes) {
        String msg = (from.isEmpty() ? "" : "v" + from + " → ") + "v" + to + "\n\n" + (notes.isEmpty() ? "已更新到最新版本。" : notes);
        new AlertDialog.Builder(act).setTitle("更新完成 🎉").setMessage(msg).setPositiveButton("好", null).show();
    }

    // ---------- 2. 手動開啟時：檢查 Release ----------
    public void checkForUpdate() {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com/repos/" + REPO + "/releases/latest").openConnection();
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                if (c.getResponseCode() != 200) return;
                JSONObject rel = new JSONObject(readStream(c.getInputStream()));
                String tag = rel.optString("tag_name");               // v1.0.N
                int remote = parseCode(tag);
                if (remote <= BuildConfig.VERSION_CODE) return;
                String apkUrl = null;
                org.json.JSONArray assets = rel.optJSONArray("assets");
                for (int i = 0; assets != null && i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (a.optString("name").endsWith(".apk")) { apkUrl = a.optString("browser_download_url"); break; }
                }
                if (apkUrl == null) return;
                String url = apkUrl, notes = rel.optString("body", "").trim(), ver = tag.replaceFirst("^v", "");
                ui.post(() -> startUpdate(url, ver, notes));
            } catch (Exception ignored) { }
        }).start();
    }

    private static int parseCode(String tag) {
        try { String[] p = tag.replaceFirst("^v", "").split("\\."); return Integer.parseInt(p[p.length - 1]); }
        catch (Exception e) { return -1; }
    }

    private void startUpdate(String apkUrl, String newVersion, String notes) {
        showOverlay("更新中…\n正在保存你的資料", true);
        // 取出網頁 localStorage，連同設定寫入 fetch-data.json
        web.evaluateJavascript("(function(){var o={};for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);o[k]=localStorage.getItem(k)}return JSON.stringify(o)})()", raw -> {
            try {
                String lsJson = new JSONObject("{\"v\":" + raw + "}").optString("v", "{}");
                JSONObject d = new JSONObject();
                d.put("fromVersion", BuildConfig.VERSION_NAME);
                d.put("toVersion", newVersion);
                d.put("notes", notes);
                d.put("createdAt", System.currentTimeMillis());
                d.put("localStorage", new JSONObject(lsJson.isEmpty() ? "{}" : lsJson));
                JSONObject prefs = new JSONObject();
                for (Map.Entry<String, ?> e : act.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getAll().entrySet())
                    prefs.put(e.getKey(), String.valueOf(e.getValue()));
                d.put("prefs", prefs);
                writeFile(dataFile(), d.toString(2));
            } catch (Exception e) {
                hideOverlay(); toastDialog("無法保存資料，已取消更新：" + e.getMessage()); return;
            }
            download(apkUrl, newVersion);
        });
    }

    private void download(String apkUrl, String ver) {
        overlayText.setText("更新中…\n正在下載 v" + ver);
        overlayBar.setIndeterminate(false); overlayBar.setMax(100); overlayBar.setProgress(0);
        new Thread(() -> {
            try {
                File dir = new File(act.getCacheDir(), "update"); dir.mkdirs();
                File apk = new File(dir, "atly-update.apk");
                HttpURLConnection c = (HttpURLConnection) new URL(apkUrl).openConnection();
                c.setInstanceFollowRedirects(true);
                int total = c.getContentLength(); int done = 0; byte[] buf = new byte[16384]; int n;
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(apk)) {
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n); done += n;
                        if (total > 0) { int p = (int) (done * 100L / total); ui.post(() -> overlayBar.setProgress(p)); }
                    }
                }
                ui.post(() -> install(apk, apkUrl));
            } catch (Exception e) {
                // 下載失敗：直接開啟下載網址讓瀏覽器下載
                ui.post(() -> openInBrowser(apkUrl));
            }
        }).start();
    }

    private File pendingApk; private String pendingUrl;

    private void install(File apk, String apkUrl) {
        if (!act.getPackageManager().canRequestPackageInstalls()) {
            pendingApk = apk; pendingUrl = apkUrl;
            overlayText.setText("請允許 atly 安裝更新\n開啟後返回即可繼續");
            act.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + act.getPackageName())));
            return;
        }
        overlayText.setText("更新中…\n請在系統視窗按「更新」");
        Uri uri = FileProvider.getUriForFile(act, act.getPackageName() + ".files", apk);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try { act.startActivity(i); } catch (Exception e) { openInBrowser(apkUrl); }
    }

    /** 從「允許安裝」設定頁返回時呼叫 */
    public void onResume() {
        if (pendingApk != null && act.getPackageManager().canRequestPackageInstalls()) {
            File a = pendingApk; String u = pendingUrl; pendingApk = null; install(a, u);
        }
    }

    private void openInBrowser(String url) {
        overlayText.setText("更新中…\n正在開啟下載頁面");
        act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    // ---------- 畫面 ----------
    private void showOverlay(String text, boolean indeterminate) {
        if (overlay == null) {
            LinearLayout box = new LinearLayout(act);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setBackgroundColor(0xFFF3EADB);
            box.setClickable(true);
            int pad = dp(32); box.setPadding(pad, pad, pad, pad);
            TextView logo = new TextView(act);
            logo.setText("atly"); logo.setTextSize(56); logo.setTextColor(0xFFC0573A);
            logo.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD)); logo.setGravity(Gravity.CENTER);
            overlayText = new TextView(act);
            overlayText.setTextSize(18); overlayText.setTextColor(0xFF3B2A1E); overlayText.setGravity(Gravity.CENTER);
            overlayText.setPadding(0, dp(24), 0, dp(24)); overlayText.setLineSpacing(0, 1.3f);
            overlayBar = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
            overlayBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFC0573A));
            box.addView(logo); box.addView(overlayText);
            box.addView(overlayBar, new LinearLayout.LayoutParams(dp(220), ViewGroup.LayoutParams.WRAP_CONTENT));
            overlay = box;
            root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        overlayText.setText(text);
        overlayBar.setIndeterminate(indeterminate);
        overlay.setVisibility(View.VISIBLE);
    }

    private void hideOverlay() { if (overlay != null) overlay.setVisibility(View.GONE); }

    private void toastDialog(String m) { new AlertDialog.Builder(act).setMessage(m).setPositiveButton("好", null).show(); }

    private int dp(int v) { return Math.round(v * act.getResources().getDisplayMetrics().density); }

    // ---------- 檔案 ----------
    private static String readStream(InputStream in) throws Exception {
        try (InputStream i = in) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
            while ((n = i.read(buf)) > 0) b.write(buf, 0, n);
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    private static String readFile(File f) throws Exception { return readStream(new FileInputStream(f)); }
    private static void writeFile(File f, String s) throws Exception {
        try (OutputStream o = new FileOutputStream(f)) { o.write(s.getBytes(StandardCharsets.UTF_8)); }
    }
}
