// Android WebView has no lock-screen / notification media session of its own.
// This hands the page's navigator.mediaSession calls to a native one.
import { Capacitor } from '@capacitor/core';
import { MediaSession } from '@jofr/capacitor-media-session';
import { LocalNotifications } from '@capacitor/local-notifications';

if (Capacitor.getPlatform() === 'android') {
  if (typeof window.MediaMetadata === 'undefined') {
    window.MediaMetadata = class MediaMetadata {
      constructor(o) { Object.assign(this, { title: '', artist: '', album: '', artwork: [] }, o || {}); }
    };
  }
  let md = null, state = 'none';
  const quiet = p => { try { p && p.catch && p.catch(() => {}); } catch (e) {} };
  // The native plugin crashes the whole app when artwork is a web address it cannot reach,
  // so only small embedded (data:) images are passed on.
  const embedded = list => Array.from(list || [])
    .filter(a => a && typeof a.src === 'string' && a.src.indexOf('data:') === 0)
    .map(a => ({ src: a.src, sizes: a.sizes, type: a.type }));
  const session = {
    get metadata() { return md; },
    set metadata(m) {
      md = m;
      if (!m) return;
      quiet(MediaSession.setMetadata({
        title: m.title || '', artist: m.artist || '', album: m.album || '',
        artwork: embedded(m.artwork)
      }));
    },
    get playbackState() { return state; },
    set playbackState(s) { state = s; quiet(MediaSession.setPlaybackState({ playbackState: s })); },
    setActionHandler(action, handler) { if (handler) quiet(MediaSession.setActionHandler({ action }, handler)); },
    setPositionState(s) { if (s) quiet(MediaSession.setPositionState(s)); }
  };
  try { Object.defineProperty(navigator, 'mediaSession', { value: session, configurable: true }); } catch (e) {}

  // ---- smoothness (native app only) ----
  // Many blurred glass layers over moving colour make older GPUs stutter, so list rows lose their
  // live blur (they keep the glass look) and the paint-heavy shape/sheen loops are switched off.
  const css = `
    .rw.glass,.qi.glass,#chips .glass,.search.glass{-webkit-backdrop-filter:none!important;backdrop-filter:none!important;
      background-color:rgba(24,30,52,.5)!important}
    .art,.sheen::after{animation:none!important}
    .glass{transition:none!important}
    @keyframes vinR{from{opacity:0;transform:translate3d(42px,0,0) scale(.985)}to{opacity:1;transform:none}}
    @keyframes vinL{from{opacity:0;transform:translate3d(-42px,0,0) scale(.985)}to{opacity:1;transform:none}}
    .vinR{animation:vinR .36s cubic-bezier(.22,.9,.3,1) both!important}
    .vinL{animation:vinL .36s cubic-bezier(.22,.9,.3,1) both!important}
    .stage{will-change:scroll-position}
  `;
  const st = document.createElement('style'); st.textContent = css; document.head.appendChild(st);
  // slide the page in whenever Now Playing / Library swap
  const watch = () => {
    const play = document.getElementById('v-play'), lib = document.getElementById('v-lib');
    if (!play || !lib) return;
    const go = (el, cls) => {
      el.classList.remove('vinR', 'vinL'); void el.offsetWidth; el.classList.add(cls);
      el.addEventListener('animationend', () => el.classList.remove(cls), { once: true });
    };
    new MutationObserver(ms => {
      for (const m of ms) {
        const el = m.target;
        if ((el !== play && el !== lib) || el.hidden || m.oldValue === null) continue;
        go(el, el === lib ? 'vinR' : 'vinL');
      }
    }).observe(document, { subtree: true, attributes: true, attributeFilter: ['hidden'], attributeOldValue: true });
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', watch); else watch();
}
