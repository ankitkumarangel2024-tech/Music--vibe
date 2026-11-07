package __PKG__;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
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

  /* embedded cover picture of one song (small jpeg as base64) */
  @PluginMethod
  public void art(PluginCall call) {
    long id = -1;
    try {
      Object o = call.getData().opt("id");
      if (o instanceof Number) {
        id = ((Number) o).longValue();
      }
    } catch (Throwable t) {
    }
    JSObject res = new JSObject();
    if (id < 0) {
      call.resolve(res);
      return;
    }
    Uri uri = android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
    byte[] pic = null;
    android.media.MediaMetadataRetriever mr = new android.media.MediaMetadataRetriever();
    try {
      mr.setDataSource(getContext(), uri);
      pic = mr.getEmbeddedPicture();
    } catch (Throwable t) {
    } finally {
      try {
        mr.release();
      } catch (Throwable t) {
      }
    }
    try {
      android.graphics.Bitmap bm = null;
      if (pic != null && pic.length > 0) {
        android.graphics.BitmapFactory.Options op = new android.graphics.BitmapFactory.Options();
        op.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeByteArray(pic, 0, pic.length, op);
        int s = 1;
        while (op.outWidth / (s * 2) >= 600) {
          s *= 2;
        }
        op = new android.graphics.BitmapFactory.Options();
        op.inSampleSize = s;
        bm = android.graphics.BitmapFactory.decodeByteArray(pic, 0, pic.length, op);
      }
      if (bm == null && android.os.Build.VERSION.SDK_INT >= 29) {
        bm = getContext().getContentResolver().loadThumbnail(uri, new android.util.Size(512, 512), null);
      }
      if (bm != null) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bm.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, bo);
        res.put("data", Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP));
      }
    } catch (Throwable t) {
    }
    call.resolve(res);
  }

  /* tells the page which notification code is really installed */
  @PluginMethod
  public void ver(PluginCall call) {
    JSObject r = new JSObject();
    r.put("v", "svc2");
    call.resolve(r);
  }

  /* media notification: title, artist and cover always travel together in one call */
  @PluginMethod
  public void np(final PluginCall call) {
    final org.json.JSONObject d = call.getData();
    final Context ctx = getContext();
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        doNp(ctx, d);
      }
    });
    call.resolve();
  }

  private void doNp(Context ctx, org.json.JSONObject d) {
    try {
      if (d.optBoolean("stop", false)) {
        if (Svc.inst != null) {
          Svc.inst.stopSelf();
        }
        return;
      }
      String art = d.optString("art", "");
      Bitmap bm = null;
      if (art.length() > 0) {
        try {
          byte[] b = Base64.decode(art, Base64.DEFAULT);
          bm = BitmapFactory.decodeByteArray(b, 0, b.length);
        } catch (Throwable t) {
          bm = null;
        }
      }
      boolean playing = d.optBoolean("playing", false);
      Svc.mid = d.optString("key", "");
      Svc.set(d.optString("title", ""), d.optString("artist", ""), bm, art.length() + ":" + art.hashCode(),
          playing, (long) d.optDouble("pos", 0), (long) d.optDouble("dur", 0));
      if (Svc.inst != null) {
        Svc.inst.refresh();
      } else if (playing) {
        Intent i = new Intent(ctx, Svc.class);
        if (Build.VERSION.SDK_INT >= 26) {
          ctx.startForegroundService(i);
        } else {
          ctx.startService(i);
        }
      }
    } catch (Throwable t) {
    }
  }

  /* what the notification service holds right now (for the in-app check) */
  @PluginMethod
  public void npInfo(PluginCall call) {
    JSObject r = new JSObject();
    r.put("ver", "n2");
    r.put("running", Svc.inst != null);
    r.put("title", Svc.title);
    r.put("sig", Svc.sig);
    r.put("hasArt", Svc.art != null);
    call.resolve(r);
  }

  /* foreground media service: owns the MediaSession and the notification */
  public static class Svc extends Service {
    static Svc inst;
    static String title = "", artist = "", sig = "", mid = "";
    static Bitmap art;
    static boolean playing;
    static long pos, dur;
    static final String CH = "mv_play";
    static final int ID = 4411;
    MediaSession ms;
    NotificationManager nm;
    boolean fg = false;
    String lastKey = "", lastFull = "";

    static void set(String t, String a, Bitmap b, String s, boolean p, long ps, long d) {
      title = t;
      artist = a;
      art = b;
      sig = s;
      playing = p;
      pos = ps;
      dur = d;
    }

    @Override
    public IBinder onBind(Intent i) {
      return null;
    }

    @Override
    public void onCreate() {
      super.onCreate();
      inst = this;
      nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      if (Build.VERSION.SDK_INT >= 26) {
        NotificationChannel c = new NotificationChannel(CH, "Music playback", NotificationManager.IMPORTANCE_LOW);
        c.setShowBadge(false);
        nm.createNotificationChannel(c);
      }
      ms = new MediaSession(this, "MusicVibe");
      ms.setCallback(new MediaSession.Callback() {
        @Override
        public void onPlay() {
          js("play", "");
        }

        @Override
        public void onPause() {
          js("pause", "");
        }

        @Override
        public void onSkipToNext() {
          js("nexttrack", "");
        }

        @Override
        public void onSkipToPrevious() {
          js("previoustrack", "");
        }

        @Override
        public void onStop() {
          js("pause", "");
        }

        @Override
        public void onSeekTo(long p) {
          js("seekto", "{seekTime:" + (p / 1000.0) + "}");
        }
      });
      ms.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
      ms.setActive(true);
    }

    static void js(String action, String arg) {
      try {
        if (MainActivity.inst != null) {
          MainActivity.inst.runJs("window.__mc&&window.__mc('" + action + "'," + (arg.length() > 0 ? arg : "null") + ")");
        }
      } catch (Throwable t) {
      }
    }

    PendingIntent act(String a, int code) {
      Intent i = new Intent(this, Svc.class);
      i.setAction(a);
      int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
      return PendingIntent.getService(this, code, i, fl);
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
      String a = i == null ? null : i.getAction();
      if ("prev".equals(a)) {
        js("previoustrack", "");
        return START_NOT_STICKY;
      }
      if ("next".equals(a)) {
        js("nexttrack", "");
        return START_NOT_STICKY;
      }
      if ("toggle".equals(a)) {
        js(playing ? "pause" : "play", "");
        return START_NOT_STICKY;
      }
      if ("close".equals(a)) {
        stopSelf();
        return START_NOT_STICKY;
      }
      try {
        Notification n0 = build();
        if (Build.VERSION.SDK_INT >= 29) {
          startForeground(ID, n0, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
          startForeground(ID, n0);
        }
        fg = true;
      } catch (Throwable t) {
      }
      lastFull = "";
      refresh();
      return START_NOT_STICKY;
    }

    void refresh() {
      try {
        MediaMetadata.Builder mb = new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Music Vibe")
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, mid)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, artist)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, dur);
        if (art != null) {
          mb.putBitmap(MediaMetadata.METADATA_KEY_ART, art);
          mb.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
          mb.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, art);
        }
        String key = mid + "|" + title + "|" + artist + "|" + sig;
        boolean metaChanged = !key.equals(lastKey);
        if (metaChanged) {
          ms.setMetadata(null);
          ms.setMetadata(mb.build());
        }
        long acts = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
            | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO
            | PlaybackState.ACTION_STOP;
        ms.setPlaybackState(new PlaybackState.Builder().setActions(acts)
            .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, pos, playing ? 1f : 0f,
                SystemClock.elapsedRealtime())
            .build());
        String full = key + (playing ? "1" : "0");
        if (full.equals(lastFull)) {
          return;
        }
        lastFull = full;
        Notification n = build();
        if (playing) {
          if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
          } else {
            startForeground(ID, n);
          }
          fg = true;
        } else {
          if (fg) {
            stopForeground(false);
            fg = false;
          }
          nm.notify(ID, n);
        }
      } catch (Throwable t) {
      }
    }

    Notification build() {
      Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
      int ic = getResources().getIdentifier("ic_baseline_volume_up_24", "drawable", getPackageName());
      b.setSmallIcon(ic != 0 ? ic : android.R.drawable.ic_media_play);
      b.setContentTitle(title).setContentText(artist).setOnlyAlertOnce(true).setShowWhen(false);
      if (art != null) {
        b.setLargeIcon(art);
      }
      Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
      if (open != null) {
        b.setContentIntent(PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)));
      }
      b.setDeleteIntent(act("close", 4));
      b.setVisibility(Notification.VISIBILITY_PUBLIC);
      b.addAction(android.R.drawable.ic_media_previous, "Previous", act("prev", 1));
      b.addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
          playing ? "Pause" : "Play", act("toggle", 2));
      b.addAction(android.R.drawable.ic_media_next, "Next", act("next", 3));
      b.setStyle(new Notification.MediaStyle().setMediaSession(ms.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
      return b.build();
    }

    @Override
    public void onDestroy() {
      try {
        stopForeground(true);
        nm.cancel(ID);
        ms.setActive(false);
        ms.release();
      } catch (Throwable t) {
      }
      inst = null;
      super.onDestroy();
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
