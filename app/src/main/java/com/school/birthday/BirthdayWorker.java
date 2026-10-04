package com.school.birthday;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;

public class BirthdayWorker extends Worker {
    public BirthdayWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    @NonNull @Override public Result doWork() {
        Context c = getApplicationContext();
        String json = c.getSharedPreferences("bd", Context.MODE_PRIVATE).getString("events", "[]");
        String today = LocalDate.now().toString(), tomorrow = LocalDate.now().plusDays(1).toString();
        StringBuilder tdy = new StringBuilder(), tmr = new StringBuilder();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String d = o.getString("d");
                if (d.equals(today)) add(tdy, o.getString("t"));
                else if (d.equals(tomorrow)) add(tmr, o.getString("t"));
            }
        } catch (Exception e) { return Result.success(); }
        if (tdy.length() == 0 && tmr.length() == 0) return Result.success();

        String text = (tdy.length() > 0 ? "🎂 امروز: " + tdy : "")
                + (tdy.length() > 0 && tmr.length() > 0 ? "\n" : "")
                + (tmr.length() > 0 ? "⏰ فردا: " + tmr : "");
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("bd", "یادآور تولد", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent pi = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, "bd")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("یادآور تولد")
                .setContentText(text.replace("\n", " | "))
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi).setAutoCancel(true).build();
        nm.notify(1, n);
        return Result.success();
    }

    private static void add(StringBuilder sb, String s) { if (sb.length() > 0) sb.append("، "); sb.append(s); }
}
