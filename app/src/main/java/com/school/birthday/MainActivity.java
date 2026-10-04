package com.school.birthday;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final int REQ_FILE = 1;
    private WebView web;
    private ValueCallback<Uri[]> chooser;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.addJavascriptInterface(new Bridge(), "Android");
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
    }
}
