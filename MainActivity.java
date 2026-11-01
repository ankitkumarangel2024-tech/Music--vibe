package __PKG__;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  static final int REQ_AUDIO = 7741;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(MusicPlugin.class);
    super.onCreate(savedInstanceState);
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
}
