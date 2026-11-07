package ba.ppmontage.evidencija;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Daje mail programu (prilozi) i kameri (nova slika) pristup datotekama u kešu aplikacije:
 * content://ba.ppmontage.evidencija.files/share/<naziv> i .../cam/<naziv>. Ništa izvan tih foldera.
 */
public class FileShareProvider extends ContentProvider {
    static final String AUTH = "ba.ppmontage.evidencija.files";

    static Uri uriFor(String folder, String name) {
        return new Uri.Builder().scheme("content").authority(AUTH).appendPath(folder).appendPath(name).build();
    }

    private File fileOf(Uri uri) throws FileNotFoundException {
        if (uri.getPathSegments().size() != 2) throw new FileNotFoundException("putanja");
        String folder = uri.getPathSegments().get(0), name = new File(uri.getPathSegments().get(1)).getName();
        if (!"share".equals(folder) && !"cam".equals(folder)) throw new FileNotFoundException("folder");
        File dir = new File(getContext().getCacheDir(), folder);
        dir.mkdirs();
        File f = new File(dir, name);
        try {
            if (!f.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) throw new FileNotFoundException("putanja");
        } catch (IOException e) { throw new FileNotFoundException("putanja"); }
        return f;
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileOf(uri);
        if (mode != null && mode.contains("w")) {
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] proj, String sel, String[] args, String sort) {
        File f;
        try { f = fileOf(uri); } catch (FileNotFoundException e) { return null; }
        String[] cols = proj != null ? proj : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        String p = uri.getLastPathSegment();
        if (p == null) return "application/octet-stream";
        p = p.toLowerCase();
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".csv")) return "text/csv";
        return "application/octet-stream";
    }

    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
