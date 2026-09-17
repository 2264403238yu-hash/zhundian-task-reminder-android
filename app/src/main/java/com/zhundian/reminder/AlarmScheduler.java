package com.zhundian.reminder;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.media.RingtoneManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class AlarmScheduler {
    public static final String CHANNEL_ID = "zhundian-reminders";
    public static final String ACTION_REMINDER = "com.zhundian.reminder.ACTION_REMINDER";
    private static final String PREFS_NAME = "zhundian_alarms";
    private static final String KEY_ALARMS = "alarms";

    private AlarmScheduler() {
    }

    public static void schedule(Context context, String taskId, long remindAt, long dueAt, String title, String body) {
        if (remindAt <= System.currentTimeMillis()) {
            remindAt = System.currentTimeMillis() + 1200L;
        }

        int requestCode = notificationId(taskId);
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        intent.putExtra("taskId", taskId);
        intent.putExtra("title", title);
        intent.putExtra("body", body);
        intent.putExtra("dueAt", dueAt);
        intent.putExtra("notificationId", requestCode);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pendingIntent);
        } catch (SecurityException error) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pendingIntent);
        }

        saveAlarm(context, taskId, remindAt, dueAt, title, body);
    }

    public static void cancel(Context context, String taskId) {
        int requestCode = notificationId(taskId);
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(requestCode);
        removeAlarm(context, taskId);
    }

    public static void notifyNow(Context context, String title, String body) {
        String taskId = "test-" + System.currentTimeMillis();
        int id = notificationId(taskId);
        Notification notification = buildNotification(context, title, body, System.currentTimeMillis(), id);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ensureChannel(context);
        notificationManager.notify(id, notification);
    }

    public static void restoreAll(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = preferences.getString(KEY_ALARMS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index += 1) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) continue;
                schedule(
                    context,
                    item.optString("taskId"),
                    item.optLong("remindAt"),
                    item.optLong("dueAt"),
                    item.optString("title", "任务提醒"),
                    item.optString("body", "")
                );
            }
        } catch (Exception ignored) {
        }
    }

    public static Notification buildNotification(Context context, String title, String body, long dueAt, int notificationId) {
        ensureChannel(context);
        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(context, CHANNEL_ID)
            : new Notification.Builder(context);

        builder
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle("准点提醒：" + title)
            .setContentText(body)
            .setStyle(new Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(Notification.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setColor(0xFFF26F4F);
        }
        return builder.build();
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.notification_channel_description));
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 260, 120, 260, 120, 480});
        channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(channel);
    }

    private static int notificationId(String taskId) {
        return Math.abs(taskId.hashCode() % 100000);
    }

    private static void saveAlarm(Context context, String taskId, long remindAt, long dueAt, String title, String body) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = preferences.getString(KEY_ALARMS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            JSONArray next = new JSONArray();
            for (int index = 0; index < array.length(); index += 1) {
                JSONObject item = array.optJSONObject(index);
                if (item != null && !taskId.equals(item.optString("taskId"))) {
                    next.put(item);
                }
            }
            JSONObject item = new JSONObject();
            item.put("taskId", taskId);
            item.put("remindAt", remindAt);
            item.put("dueAt", dueAt);
            item.put("title", title);
            item.put("body", body);
            next.put(item);
            preferences.edit().putString(KEY_ALARMS, next.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static void removeAlarm(Context context, String taskId) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = preferences.getString(KEY_ALARMS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            JSONArray next = new JSONArray();
            for (int index = 0; index < array.length(); index += 1) {
                JSONObject item = array.optJSONObject(index);
                if (item != null && !taskId.equals(item.optString("taskId"))) {
                    next.put(item);
                }
            }
            preferences.edit().putString(KEY_ALARMS, next.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
