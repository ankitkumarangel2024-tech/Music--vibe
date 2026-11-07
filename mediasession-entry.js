// Android WebView has no lock-screen / notification media session of its own.
// This hands the page's navigator.mediaSession calls to a native one.
import { Capacitor } from '@capacitor/core';

if (Capacitor.getPlatform() === 'android') {
  if (typeof window.MediaMetadata === 'undefined') {
    window.MediaMetadata = class MediaMetadata {
      constructor(o) { Object.assign(this, { title: '', artist: '', album: '', artwork: [] }, o || {}); }
    };
  }
  // Media notification is built natively by MusicPlugin (see index.html); the old plugin is no longer used.

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
