package __PKG__;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@CapacitorPlugin(name = "MusicNative")
public class MusicPlugin extends Plugin {
  private MainActivity act() {
    return (MainActivity) getActivity();
  }

  /* every song on the phone */
  @PluginMethod
  public void scan(PluginCall call) {
    final MainActivity a = act();
    JSObject res = new JSObject();
    if (!a.hasAudioAccess()) {
      a.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          a.askAudioAccess();
        }
      });
      res.put("ok", false);
      res.put("asked", true);
      call.resolve(res);
      return;
    }
    JSArray list = new JSArray();
    Cursor c = null;
    try {
      String[] proj = {
          MediaStore.Audio.Media._ID,
          MediaStore.Audio.Media.TITLE,
          MediaStore.Audio.Media.ARTIST,
          MediaStore.Audio.Media.DURATION,
          MediaStore.Audio.Media.DISPLAY_NAME,
          MediaStore.Audio.Media.SIZE,
          MediaStore.Audio.Media.DATE_ADDED,
          MediaStore.Audio.Media.DATA
      };
      c = getContext().getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
          "(" + MediaStore.Audio.Media.IS_MUSIC + " != 0 OR " + MediaStore.Audio.Media.DURATION + " >= 60000) AND " + MediaStore.Audio.Media.IS_RINGTONE + " = 0 AND " + MediaStore.Audio.Media.IS_ALARM + " = 0 AND " + MediaStore.Audio.Media.IS_NOTIFICATION + " = 0", null, null);
      if (c != null) {
        while (c.moveToNext()) {
          JSObject o = new JSObject();
          o.put("id", c.getLong(0));
          o.put("title", c.getString(1));
          o.put("artist", c.getString(2));
          o.put("dur", c.getLong(3));
          o.put("file", c.getString(4));
          o.put("size", c.getLong(5));
          o.put("added", c.getLong(6));
          o.put("path", c.getString(7));
          list.put(o);
        }
      }
    } catch (Throwable t) {
      call.reject("scan failed");
      return;
    } finally {
      if (c != null) {
        c.close();
      }
    }
    try {
      JSArray ok = new JSArray();
      for (int i = 0; i < list.length(); i++) {
        JSObject o = (JSObject) list.get(i);
        String pth = o.optString("path", "");
        if (pth.length() == 0 || new java.io.File(pth).exists()) {
          ok.put(o);
        }
      }
      if (ok.length() > 0) {
        list = ok;
      }
    } catch (Throwable t) {
    }
    res.put("ok", true);
    res.put("list", list);
    call.resolve(res);
  }

  /* fallback: hand one song to the page as a data address */
  @PluginMethod
  public void read(PluginCall call) {
    long id = -1;
    try {
      Object o = call.getData().opt("id");
      if (o instanceof Number) {
        id = ((Number) o).longValue();
      }
    } catch (Throwable t) {
    }
    if (id < 0) {
      call.reject("id");
      return;
    }
    Uri uri = android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
    try {
      InputStream in = getContext().getContentResolver().openInputStream(uri);
      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      byte[] buf = new byte[65536];
      int n;
      long total = 0;
      while ((n = in.read(buf)) > 0) {
        bo.write(buf, 0, n);
        total += n;
        if (total > 80L * 1024 * 1024) {
          in.close();
          call.reject("too big");
          return;
        }
      }
      in.close();
      String mime = getContext().getContentResolver().getType(uri);
      JSObject res = new JSObject();
      res.put("data", Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP));
      res.put("mime", mime == null ? "audio/mpeg" : mime);
      call.resolve(res);
    } catch (Throwable t) {
      call.reject("read failed: " + t);
    }
  }

  @PluginMethod
  public void settings(PluginCall call) {
    try {
      Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          Uri.parse("package:" + getContext().getPackageName()));
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      getActivity().startActivity(i);
    } catch (Throwable t) {
    }
    call.resolve();
  }
}
