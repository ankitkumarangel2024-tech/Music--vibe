package __PKG__;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  static final int REQ_AUDIO = 7741;
  static MainActivity inst;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(MusicPlugin.class);
    super.onCreate(savedInstanceState);
    inst = this;
    try {
      startService(new Intent(this, KillService.class));
    } catch (Throwable t) {
    }
  }

  /* when the app is swiped away from recents: remove the media notification and stop everything */
  public static class KillService extends Service {
    @Override
    public IBinder onBind(Intent i) {
      return null;
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
      return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
      try {
        if (inst != null) {
          inst.runJs("window.__stop&&window.__stop()");
        }
      } catch (Throwable t) {
      }
      new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
        @Override
        public void run() {
          try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), android.content.pm.PackageManager.GET_SERVICES);
            if (pi.services != null) {
              for (android.content.pm.ServiceInfo si : pi.services) {
                if (si.name != null && !si.name.contains("KillService")) {
                  try {
                    stopService(new Intent().setClassName(getPackageName(), si.name));
                  } catch (Throwable t) {
                  }
                }
              }
            }
          } catch (Throwable t) {
          }
          try {
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancelAll();
          } catch (Throwable t) {
          }
          stopSelf();
        }
      }, 700);
    }
  }

  public String audioPermission() {
    return Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_AUDIO"
        : "android.permission.READ_EXTERNAL_STORAGE";
  }

  public boolean hasAudioAccess() {
    if (Build.VERSION.SDK_INT < 23) {
      return true;
    }
    return checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED;
  }

  public void askAudioAccess() {
    if (Build.VERSION.SDK_INT >= 23) {
      requestPermissions(new String[] {audioPermission()}, REQ_AUDIO);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQ_AUDIO) {
      runJs("window.__aperm&&window.__aperm(" + hasAudioAccess() + ")");
      askNotif();
    }
  }

  boolean notifAsked = false;

  public void askNotif() {
    if (notifAsked || Build.VERSION.SDK_INT < 33) {
      return;
    }
    if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) {
      return;
    }
    notifAsked = true;
    requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 7742);
  }

  @Override
  public void onResume() {
    super.onResume();
    if (hasAudioAccess()) {
      askNotif();
    }
  }

  public void runJs(final String js) {
    try {
      if (getBridge() != null && getBridge().getWebView() != null) {
        final WebView w = getBridge().getWebView();
        w.post(new Runnable() {
          @Override
          public void run() {
            w.evaluateJavascript(js, null);
          }
        });
      }
    } catch (Throwable t) {
    }
  }

  @Override
  public void onDestroy() {
    if (inst == this) {
      inst = null;
    }
    super.onDestroy();
  }
}
