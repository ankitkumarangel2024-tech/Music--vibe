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
}
