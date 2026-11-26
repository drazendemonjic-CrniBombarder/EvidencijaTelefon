package ba.ppmontage.evidencija;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Podsjetnik (1.7): svaki dan u 7:30 provjerava prethodni radni dan
 * (utorak–subota: jučer; ponedjeljak: subota; nedjelja: ništa).
 * Ako za taj dan nema poslane evidencije, pokaže notifikaciju.
 * HTML program preko EvrAndroid.setDays javlja samo datume upisanih i poslanih dana (bez imena i sati).
 * Isti receiver prima i alarm i restart telefona / ažuriranje aplikacije / promjenu vremena (ponovno zakazivanje).
 */
public class Podsjetnik extends BroadcastReceiver {
    static final String ACTION = "ba.ppmontage.evidencija.PODSJETNIK";
    static final String PREFS = "podsjetnik";
    static final String CHANNEL = "podsjetnik";
    static final int HOUR = 7, MINUTE = 30, NOTIF_ID = 730;
    static final String[] DANI = { "nedjelju", "ponedjeljak", "utorak", "srijedu", "četvrtak", "petak", "subotu" };

    @Override
    public void onReceive(Context c, Intent i) {
        if (i != null && ACTION.equals(i.getAction())) check(c, Calendar.getInstance());
        schedule(c);
    }

    /** Dan koji se provjerava kad je sada `now` (null = nedjelja, nema provjere). */
    static Calendar dayToCheck(Calendar now) {
        int dow = now.get(Calendar.DAY_OF_WEEK);
        if (dow == Calendar.SUNDAY) return null;
        Calendar d = (Calendar) now.clone();
        d.add(Calendar.DAY_OF_MONTH, dow == Calendar.MONDAY ? -2 : -1);
        return d;
    }

    static String iso(Calendar d) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d", d.get(Calendar.YEAR), d.get(Calendar.MONTH) + 1, d.get(Calendar.DAY_OF_MONTH));
    }

    /** Sljedeće 7:30 koje nije nedjelja. */
    static long nextTime(Calendar now) {
        Calendar t = (Calendar) now.clone();
        t.set(Calendar.HOUR_OF_DAY, HOUR); t.set(Calendar.MINUTE, MINUTE); t.set(Calendar.SECOND, 0); t.set(Calendar.MILLISECOND, 0);
        if (!t.after(now)) t.add(Calendar.DAY_OF_MONTH, 1);
        while (t.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) t.add(Calendar.DAY_OF_MONTH, 1);
        return t.getTimeInMillis();
    }

    static void schedule(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(c, Podsjetnik.class).setAction(ACTION);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (!p.getBoolean("aktivan", false)) { am.cancel(pi); return; }
        long at = nextTime(Calendar.getInstance());
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    /** Poziva HTML program: {"sent":[datumi], "made":[datumi]}. */
    static void setDays(Context c, String json) {
        try {
            JSONObject o = new JSONObject(json);
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            SharedPreferences.Editor e = p.edit();
            e.putString("sent", o.optJSONArray("sent") != null ? o.getJSONArray("sent").toString() : "[]");
            e.putString("made", o.optJSONArray("made") != null ? o.getJSONArray("made").toString() : "[]");
            e.putBoolean("aktivan", true);
            // dani prije prvog javljanja (aktivacija telefona / nova verzija) se ne provjeravaju
            if (!p.contains("od")) e.putString("od", iso(Calendar.getInstance()));
            e.apply();
        } catch (Exception ex) { /* neispravan JSON: ostaje staro stanje */ }
        schedule(c);
    }

    static void stop(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        schedule(c);
    }

    static Set<String> set(String json) {
        Set<String> s = new HashSet<>();
        try { JSONArray a = new JSONArray(json == null ? "[]" : json); for (int i = 0; i < a.length(); i++) s.add(a.getString(i)); } catch (Exception e) { /* prazno */ }
        return s;
    }

    static void check(Context c, Calendar now) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("aktivan", false)) return;
        Calendar d = dayToCheck(now);
        if (d == null) return;
        String day = iso(d);
        if (day.compareTo(p.getString("od", day)) < 0) return;
        if (set(p.getString("sent", "[]")).contains(day)) return;
        boolean made = set(p.getString("made", "[]")).contains(day);
        String dan = DANI[d.get(Calendar.DAY_OF_WEEK) - 1] + " " + String.format(Locale.ROOT, "%02d.%02d.%04d.", d.get(Calendar.DAY_OF_MONTH), d.get(Calendar.MONTH) + 1, d.get(Calendar.YEAR));
        String title = made ? "Evidencija za " + dan + " nije poslana" : "Nema evidencije za " + dan;
        String text = made ? "Upisana je, ali nije poslana. Otvori aplikaciju i pošalji je." : "Za taj dan nije napravljena i poslana evidencija. Otvori aplikaciju, upiši i pošalji.";
        notify(c, title, text);
    }

    static void notify(Context c, String title, String text) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Podsjetnik za evidenciju", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("U 7:30, ako za prethodni radni dan nije poslana evidencija");
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(c, CHANNEL);
        } else {
            b = new Notification.Builder(c);
            b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        }
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        b.setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(pi).setAutoCancel(true);
        nm.notify(NOTIF_ID, b.build());
    }
}
