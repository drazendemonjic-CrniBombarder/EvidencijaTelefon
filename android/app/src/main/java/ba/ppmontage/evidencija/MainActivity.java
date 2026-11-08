package ba.ppmontage.evidencija;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Build;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Evidencija radnog vremena – aplikacija za telefon.
 * Cijeli program je HTML u assets/index.html; ova klasa ga otvara u WebView-u i daje mu:
 *  - izbor datoteke i kameru (input type=file),
 *  - slanje mailom (EvrAndroid.share) s prilozima iz keša aplikacije,
 *  - izlaz (EvrAndroid.exit) i dugme Nazad (window.evrBack),
 *  - prijem paketa .evp podijeljenog iz WhatsAppa ili maila (window.evrIncomingFile).
 */
public class MainActivity extends Activity {
    private static final int REQ_FILE = 1;
    private static final int REQ_SHARE = 2;

    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private Uri camUri;
    private boolean pageReady = false;
    private String pendingJs = null;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setTextZoom(100);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                if (pendingJs != null) { view.evaluateJavascript(pendingJs, null); pendingJs = null; }
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { /* nema programa za vezu */ }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams p) {
                return chooseFile(cb, p);
            }
        });
        web.addJavascriptInterface(new Bridge(), "EvrAndroid");
        web.loadUrl("file:///android_asset/index.html");
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    // paket iz ureda otvoren / podijeljen u aplikaciju
    private void handleIntent(Intent i) {
        if (i == null) return;
        Uri u = null;
        if (Intent.ACTION_SEND.equals(i.getAction())) u = i.getParcelableExtra(Intent.EXTRA_STREAM);
        else if (Intent.ACTION_VIEW.equals(i.getAction())) u = i.getData();
        if (u == null) return;
        try {
            byte[] data = readAll(u);
            String js = "window.evrIncomingFile && window.evrIncomingFile(" + JSONObject.quote(displayName(u)) + "," + JSONObject.quote(Base64.encodeToString(data, Base64.NO_WRAP)) + ")";
            if (pageReady) web.evaluateJavascript(js, null); else pendingJs = js;
        } catch (Exception e) {
            Toast.makeText(this, "Datoteka se ne može pročitati.", Toast.LENGTH_LONG).show();
        }
    }

    private byte[] readAll(Uri u) throws Exception {
        InputStream in = getContentResolver().openInputStream(u);
        if (in == null) throw new Exception("nema podataka");
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > 20 * 1024 * 1024) throw new Exception("datoteka je prevelika");
            }
            return bo.toByteArray();
        } finally { in.close(); }
    }

    private String displayName(Uri u) {
        String name = null;
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception e) { /* naziv nije poznat */ }
        if (name == null) name = u.getLastPathSegment();
        return name == null ? "paket.evp" : name;
    }

    private boolean pickImage = false;

    // slika iz galerije → JPEG (najviše 2400 px, ispravno okrenut) u cache/cam, pa je WebView sigurno može pročitati
    private Uri toJpeg(Uri src, int idx) throws Exception {
        Bitmap bmp;
        final int MAX = 2400;
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source s = ImageDecoder.createSource(getContentResolver(), src);
            bmp = ImageDecoder.decodeBitmap(s, (dec, info, so) -> {
                int w = info.getSize().getWidth(), h = info.getSize().getHeight(), m = Math.max(w, h);
                if (m > MAX) { float k = (float) MAX / m; dec.setTargetSize(Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k))); }
                dec.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        } else {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(src)) { BitmapFactory.decodeStream(in, null, o); }
            int sample = 1, m = Math.max(o.outWidth, o.outHeight);
            while (m / sample > MAX * 2) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            try (InputStream in = getContentResolver().openInputStream(src)) { bmp = BitmapFactory.decodeStream(in, null, o2); }
            if (bmp == null) throw new Exception("slika se ne može pročitati");
            int rot = 0;
            try (InputStream in = getContentResolver().openInputStream(src)) {
                int or = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                rot = or == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : or == ExifInterface.ORIENTATION_ROTATE_180 ? 180 : or == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            } catch (Exception e) { /* bez EXIF-a */ }
            float k = Math.min(1f, (float) MAX / Math.max(bmp.getWidth(), bmp.getHeight()));
            if (rot != 0 || k < 1f) {
                Matrix mx = new Matrix();
                mx.postScale(k, k);
                mx.postRotate(rot);
                Bitmap b2 = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mx, true);
                if (b2 != bmp) bmp.recycle();
                bmp = b2;
            }
        }
        File dir = new File(getCacheDir(), "cam");
        dir.mkdirs();
        // stare kopije iz galerije (starije od 2 dana) se brišu
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) if (f.getName().startsWith("gal_") && f.lastModified() < System.currentTimeMillis() - 2 * 864e5) f.delete();
        String name = "gal_" + System.currentTimeMillis() + "_" + idx + ".jpg";
        try (FileOutputStream fo = new FileOutputStream(new File(dir, name))) {
            if (!bmp.compress(Bitmap.CompressFormat.JPEG, 88, fo)) throw new Exception("JPEG nije snimljen");
        } finally { bmp.recycle(); }
        return FileShareProvider.uriFor("cam", name);
    }

    // <input type="file">: kamera (capture) ili izbor datoteke / slike
    private boolean chooseFile(ValueCallback<Uri[]> cb, WebChromeClient.FileChooserParams p) {
        if (fileCb != null) fileCb.onReceiveValue(null);
        fileCb = cb;
        camUri = null;
        String[] acc = p.getAcceptTypes();
        boolean image = acc != null && acc.length > 0 && acc[0] != null && acc[0].startsWith("image");
        pickImage = image;
        try {
            if (image && p.isCaptureEnabled()) {
                camUri = FileShareProvider.uriFor("cam", "slika_" + System.currentTimeMillis() + ".jpg");
                Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cam.putExtra(MediaStore.EXTRA_OUTPUT, camUri);
                cam.setClipData(ClipData.newRawUri("slika", camUri));
                cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(cam, REQ_FILE);
            } else {
                Intent g = new Intent(Intent.ACTION_GET_CONTENT);
                g.addCategory(Intent.CATEGORY_OPENABLE);
                g.setType(image ? "image/*" : "*/*");
                if (p.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) g.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(Intent.createChooser(g, image ? "Odaberi sliku" : "Odaberi datoteku"), REQ_FILE);
            }
        } catch (ActivityNotFoundException e) {
            fileCb.onReceiveValue(null);
            fileCb = null;
            Toast.makeText(this, "Nema programa za ovu radnju.", Toast.LENGTH_LONG).show();
        }
        return true;
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE) {
            Uri[] out = null;
            if (res == RESULT_OK) {
                if (camUri != null) out = new Uri[]{camUri};
                else if (data != null && data.getClipData() != null) {
                    ClipData cd = data.getClipData();
                    out = new Uri[cd.getItemCount()];
                    for (int i = 0; i < cd.getItemCount(); i++) out[i] = cd.getItemAt(i).getUri();
                } else if (data != null && data.getData() != null) out = new Uri[]{data.getData()};
            }
            // slike iz galerije (1.3): pretvori u JPEG u kešu aplikacije (HEIC, Google Fotografije, privremene dozvole)
            if (out != null && camUri == null && pickImage) {
                final Uri[] src = out;
                final ValueCallback<Uri[]> cb = fileCb;
                fileCb = null;
                camUri = null;
                new Thread(() -> {
                    Uri[] conv = new Uri[src.length];
                    for (int i = 0; i < src.length; i++) {
                        try { conv[i] = toJpeg(src[i], i); } catch (Throwable e) { conv[i] = src[i]; }
                    }
                    runOnUiThread(() -> { if (cb != null) cb.onReceiveValue(conv); });
                }).start();
                return;
            }
            if (fileCb != null) fileCb.onReceiveValue(out);
            fileCb = null;
            camUri = null;
        } else if (req == REQ_SHARE) {
            long ms = System.currentTimeMillis() - shareStart;
            web.evaluateJavascript("window.evrShareDone && window.evrShareDone(" + ms + ")", null);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("(window.evrBack ? window.evrBack() : false)", v -> {
            if (!"true".equals(v)) finish();
        });
    }

    /** Veza prema HTML programu (window.EvrAndroid). */
    private class Bridge {
        @JavascriptInterface
        public void share(final String json) {
            runOnUiThread(() -> doShare(json));
        }

        @JavascriptInterface
        public void exit() {
            runOnUiThread(() -> finishAndRemoveTask());
        }
    }

    private long shareStart = 0;

    // mail s prilozima: datoteke idu u keš (cache/share), a mail program ih čita preko FileShareProvider-a.
    // mode "mail": Gmail direktno, pa drugi mail programi; "any": izbor bilo kojeg programa (WhatsApp, Viber, Drive …)
    private void doShare(String json) {
        try {
            JSONObject o = new JSONObject(json);
            File dir = new File(getCacheDir(), "share");
            if (dir.exists()) { File[] old = dir.listFiles(); if (old != null) for (File f : old) f.delete(); }
            dir.mkdirs();
            JSONArray files = o.optJSONArray("files");
            ArrayList<Uri> uris = new ArrayList<>();
            ClipData clip = null;
            if (files != null) for (int i = 0; i < files.length(); i++) {
                JSONObject f = files.getJSONObject(i);
                String name = new File(f.getString("name")).getName();
                try (FileOutputStream fo = new FileOutputStream(new File(dir, name))) {
                    fo.write(Base64.decode(f.getString("b64"), Base64.DEFAULT));
                }
                Uri u = FileShareProvider.uriFor("share", name);
                uris.add(u);
                if (clip == null) clip = ClipData.newRawUri(name, u); else clip.addItem(new ClipData.Item(u));
            }
            Intent send = new Intent(uris.size() > 1 ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND);
            send.setType(uris.size() == 1 && name0(files).endsWith(".jpg") ? "image/jpeg" : "application/octet-stream");
            String to = o.optString("to", "");
            if (to.length() > 0) send.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
            // kopije (CC) iz paketa (1.2)
            JSONArray ccA = o.optJSONArray("cc");
            if (ccA != null && ccA.length() > 0) {
                ArrayList<String> cc = new ArrayList<>();
                for (int i = 0; i < ccA.length(); i++) { String a = ccA.optString(i, "").trim(); if (a.length() > 0) cc.add(a); }
                if (!cc.isEmpty()) send.putExtra(Intent.EXTRA_CC, cc.toArray(new String[0]));
            }
            send.putExtra(Intent.EXTRA_SUBJECT, o.optString("subject", "Evidencija"));
            send.putExtra(Intent.EXTRA_TEXT, o.optString("body", ""));
            if (uris.size() > 1) send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            else if (uris.size() == 1) send.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            if (clip != null) send.setClipData(clip);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareStart = System.currentTimeMillis();
            if ("mail".equals(o.optString("mode", "mail"))) {
                PackageManager pm = getPackageManager();
                // 1) Gmail direktno (bez izbora)
                Intent gm = new Intent(send);
                gm.setPackage("com.google.android.gm");
                if (gm.resolveActivity(pm) != null) { startActivityForResult(gm, REQ_SHARE); return; }
                // 2) drugi mail programi: oni koji otvaraju mailto: i primaju priloge
                ArrayList<Intent> targets = new ArrayList<>();
                List<ResolveInfo> mail = pm.queryIntentActivities(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), 0);
                for (ResolveInfo ri : mail) {
                    Intent t = new Intent(send);
                    t.setPackage(ri.activityInfo.packageName);
                    if (t.resolveActivity(pm) != null) targets.add(t);
                }
                if (targets.size() == 1) { startActivityForResult(targets.get(0), REQ_SHARE); return; }
                if (targets.size() > 1) {
                    Intent ch = Intent.createChooser(targets.remove(0), "Pošalji mailom");
                    ch.putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.toArray(new Parcelable[0]));
                    startActivityForResult(ch, REQ_SHARE);
                    return;
                }
                Toast.makeText(this, "Nema mail programa – izaberi drugi način.", Toast.LENGTH_LONG).show();
            }
            startActivityForResult(Intent.createChooser(send, "Pošalji"), REQ_SHARE);
        } catch (Exception e) {
            Toast.makeText(this, "Slanje nije uspjelo: " + e.getMessage(), Toast.LENGTH_LONG).show();
            web.evaluateJavascript("window.evrShareDone && window.evrShareDone(0)", null);
        }
    }

    private static String name0(JSONArray files) {
        try { return files.getJSONObject(0).getString("name").toLowerCase(); } catch (Exception e) { return ""; }
    }
}
