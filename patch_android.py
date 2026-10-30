"""Run after `npx cap add android`: permissions, launcher icon, dark launch colour."""
import os, re
from PIL import Image

man = 'android/app/src/main/AndroidManifest.xml'
s = open(man, encoding='utf-8').read()
perms = ['FOREGROUND_SERVICE', 'FOREGROUND_SERVICE_MEDIA_PLAYBACK', 'POST_NOTIFICATIONS', 'WAKE_LOCK']
add = ''.join('    <uses-permission android:name="android.permission.%s" />\n' % p
              for p in perms if 'android.permission.%s"' % p not in s)
if add:
    s = s.replace('<application', add + '    <application', 1)
open(man, 'w', encoding='utf-8').write(s)
print('manifest ok')

res = 'android/app/src/main/res'
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
print('icons ok')

bg = os.path.join(res, 'values', 'ic_launcher_background.xml')
if os.path.exists(bg):
    open(bg, 'w').write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                        '    <color name="ic_launcher_background">#04060C</color>\n</resources>\n')
    print('background ok')
