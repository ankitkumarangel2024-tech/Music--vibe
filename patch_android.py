"""Run after `npx cap add android`: permissions, crash reporter, notification icon, launcher icon."""
import os
from PIL import Image

man = 'android/app/src/main/AndroidManifest.xml'
s = open(man, encoding='utf-8').read()
perms = ['FOREGROUND_SERVICE', 'FOREGROUND_SERVICE_MEDIA_PLAYBACK', 'POST_NOTIFICATIONS', 'WAKE_LOCK', 'READ_MEDIA_AUDIO']
add = ''.join('    <uses-permission android:name="android.permission.%s" />\n' % p
              for p in perms if 'android.permission.%s"' % p not in s)
add += '    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />\n'
s = s.replace('<application', add + '    <application', 1)
if 'android:name=".CrashApp"' not in s:
    s = s.replace('<application', '<application android:name=".CrashApp"', 1)
if 'KillService' not in s:
    s = s.replace('</application>', '        <service android:name=".MainActivity$KillService" android:exported="false" android:stopWithTask="false" />\n    </application>', 1)
open(man, 'w', encoding='utf-8').write(s)
import xml.dom.minidom
try:
    xml.dom.minidom.parse(man)
except Exception as e:
    print('MANIFEST BROKEN:', e)
    print(s)
    raise SystemExit(1)
print(s)
print('manifest ok')

res = 'android/app/src/main/res'
os.makedirs(res + '/drawable', exist_ok=True)
open(res + '/drawable/ic_baseline_volume_up_24.xml', 'w').write(
    '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" '
    'android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#FFFFFF" '
    'android:pathData="M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z"/></vector>\n')

icon = Image.open('icon-512.png').convert('RGBA')
fore = Image.open('icon-maskable-512.png').convert('RGBA')
sizes = {'mdpi': (48, 108), 'hdpi': (72, 162), 'xhdpi': (96, 216), 'xxhdpi': (144, 324), 'xxxhdpi': (192, 432)}
for d, (legacy, adaptive) in sizes.items():
    folder = os.path.join(res, 'mipmap-' + d)
    if not os.path.isdir(folder):
        continue
    icon.resize((legacy, legacy), Image.LANCZOS).save(os.path.join(folder, 'ic_launcher.png'))
    icon.resize((legacy, legacy), Image.LANCZOS).save(os.path.join(folder, 'ic_launcher_round.png'))
    fore.resize((adaptive, adaptive), Image.LANCZOS).save(os.path.join(folder, 'ic_launcher_foreground.png'))
bg = os.path.join(res, 'values', 'ic_launcher_background.xml')
if os.path.exists(bg):
    open(bg, 'w').write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                        '    <color name="ic_launcher_background">#04060C</color>\n</resources>\n')
print('icons ok')
