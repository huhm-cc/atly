package cc.huhm.atly;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {
    private static final String HOST = "atly.huhm.cc";
    private static final String HOME = "https://" + HOST + "/inbox";
    private WebView web;
    private SwipeRefreshLayout swipe;
    private AtlyUpdater updater;
    private boolean manualLaunch, startupHandled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        swipe = new SwipeRefreshLayout(this);
        swipe.setColorSchemeColors(0xFFC0573A);
        swipe.addView(web);
        swipe.setOnRefreshListener(() -> web.reload());
        FrameLayout root = new FrameLayout(this);
        root.addView(swipe);
        setContentView(root);
        updater = new AtlyUpdater(this, web, root);
        manualLaunch = savedInstanceState == null && Intent.ACTION_MAIN.equals(getIntent().getAction());

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUserAgentString(s.getUserAgentString() + " AtlyAndroid/1");
        web.setBackgroundColor(0xFFF3EADB);
        web.addJavascriptInterface(new Bridge(), "AtlyAndroid");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                swipe.setRefreshing(false);
                if (startupHandled) return;
                startupHandled = true;
                if (updater.hasPendingRestore()) updater.restore(() -> web.reload());   // 安裝後還原資料
                else if (manualLaunch) updater.checkForUpdate();                         // 手動開啟才檢查更新
            }
        });
        // only allow pull-to-refresh when at the top
        web.getViewTreeObserver().addOnScrollChangedListener(() -> swipe.setEnabled(web.getScrollY() == 0));

        Uri data = getIntent().getData();
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(data != null && HOST.equals(data.getHost()) ? data.toString() : HOME);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri data = intent.getData();
        if (data != null && HOST.equals(data.getHost())) web.loadUrl(data.toString());
    }

    @Override
    protected void onResume() { super.onResume(); if (updater != null) updater.onResume(); }

    @Override
    protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override
    public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }

    private class Bridge {
        @JavascriptInterface
        public void copy(String text) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("atly", text));
            });
        }

        @JavascriptInterface
        public void shareStory(String base64Png, String link) {
            try {
                File dir = new File(getCacheDir(), "share");
                dir.mkdirs();
                File f = new File(dir, "atly-story.png");
                try (FileOutputStream o = new FileOutputStream(f)) { o.write(Base64.decode(base64Png, Base64.DEFAULT)); }
                Uri uri = FileProvider.getUriForFile(MainActivity.this, "cc.huhm.atly.files", f);
                runOnUiThread(() -> {
                    copy(link);
                    Intent ig = new Intent("com.instagram.share.ADD_TO_STORY");
                    ig.setDataAndType(uri, "image/png");
                    ig.setPackage("com.instagram.android");
                    ig.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    grantUriPermission("com.instagram.android", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        if (ig.resolveActivity(getPackageManager()) == null) throw new Exception("no ig");
                        startActivity(ig);
                        Toast.makeText(MainActivity.this, "連結已複製，到限動加上連結貼紙", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType("image/png");
                        send.putExtra(Intent.EXTRA_STREAM, uri);
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(send, "發布到限動"));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "發布失敗：" + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }
    }
}
