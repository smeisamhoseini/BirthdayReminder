package com.school.birthday;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final int REQ_FILE = 1, REQ_SAVE = 3;
    private WebView web;
    private ValueCallback<Uri[]> chooser;
    private byte[] pendingBytes;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        // تماس و پیامک را به برنامه‌ی تلفن/پیام گوشی می‌دهد
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String sch = r.getUrl().getScheme();
                if ("tel".equals(sch) || "sms".equals(sch) || "mailto".equals(sch)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl())); } catch (Exception e) { }
                    return true;
                }
                return false;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "انتخاب فایل اکسل"), REQ_FILE);
                } catch (Exception e) { chooser = null; return false; }
                return true;
            }
        });
        web.loadUrl("file:///android_asset/birthday.html");
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
        scheduleDaily();
    }

    @Override protected void onActivityResult(int rq, int rs, Intent d) {
        if (rq == REQ_FILE && chooser != null) {
            chooser.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(rs, d));
            chooser = null;
        } else if (rq == REQ_SAVE && rs == RESULT_OK && d != null && d.getData() != null && pendingBytes != null) {
            try (OutputStream o = getContentResolver().openOutputStream(d.getData())) {
                o.write(pendingBytes);
                Toast.makeText(this, "ذخیره شد ✅", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "ذخیره ناموفق بود", Toast.LENGTH_SHORT).show();
            }
            pendingBytes = null;
        }
    }

    /** هر روز ساعت ۸ صبح یک‌بار بررسی می‌کند. */
    private void scheduleDaily() {
        Calendar now = Calendar.getInstance(), t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, 8); t.set(Calendar.MINUTE, 0); t.set(Calendar.SECOND, 0);
        if (!t.after(now)) t.add(Calendar.DAY_OF_MONTH, 1);
        PeriodicWorkRequest w = new PeriodicWorkRequest.Builder(BirthdayWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(t.getTimeInMillis() - now.getTimeInMillis(), TimeUnit.MILLISECONDS).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("daily", ExistingPeriodicWorkPolicy.KEEP, w);
    }

    class Bridge {
        @JavascriptInterface public void setEvents(String json) {
            getSharedPreferences("bd", MODE_PRIVATE).edit().putString("events", json).apply();
        }
        @JavascriptInterface public void saveFile(final String name, String base64) {
            pendingBytes = Base64.decode(base64, Base64.DEFAULT);
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                i.putExtra(Intent.EXTRA_TITLE, name);
                startActivityForResult(i, REQ_SAVE);
            });
        }
    }
}
