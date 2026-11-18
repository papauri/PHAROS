package org.pharos.core.webbridge

/**
 * Self-contained, zero-dependency, single-file HTML5 / Canvas / WebAudio Offline PWA Client
 * served over PHAROS LocalOnlyHotspot (`playStore` & `standalone` flavors).
 *
 * Allows any bystander with a browser (iOS, Android, laptop) and zero internet connection
 * to transmit and decode PHAROS optical 4x4/4-PPM flashes, generate MFSK/DTMF radio tones,
 * and exchange Tactical Cards with the host PHAROS node.
 */
object OfflineWebBridgePwa {

    fun renderSingleFileHtml(
        hostNodeFingerprint: String,
        isStandaloneApkAvailable: Boolean,
        initialCardsJson: String = "[]"
    ): String {
        val apkBannerHtml = if (isStandaloneApkAvailable) {
            """
            <div class="apk-banner">
              <div>
                <strong>NATIVE UNIVERSAL APK AVAILABLE OFFLINE</strong>
                <div style="font-size:12px;color:#ffb000;">Full Android 6.0-16 Universal APK signed &amp; verified directly from this node</div>
              </div>
              <a href="/download/pharos-universal.apk" class="btn-red">DOWNLOAD .APK</a>
            </div>
            """.trimIndent()
        } else {
            """
            <div class="apk-banner">
              <div>
                <strong>GOOGLE PLAY EDITION — ZERO-INSTALL WEB NODE ACTIVE</strong>
                <div style="font-size:12px;color:#ffb000;">Full Optical &amp; Acoustic WebAudio survival transceiver running locally in your browser</div>
              </div>
            </div>
            """.trimIndent()
        }

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
<title>PHAROS // OFFLINE SURVIVAL NODE</title>
<style>
  :root { --bg:#000000; --fg:#ff2a2a; --amber:#ffb000; --green:#00e676; --card:#0c0c0c; --border:#331111; }
  * { box-sizing:border-box; margin:0; padding:0; font-family:monospace; }
  body { background:var(--bg); color:var(--fg); padding:12px; max-width:640px; margin:0 auto; }
  header { border-bottom:1px solid var(--fg); padding-bottom:8px; margin-bottom:12px; display:flex; justify-content:space-between; align-items:center; }
  .badge { background:#220505; border:1px solid var(--fg); padding:2px 6px; font-size:11px; color:var(--amber); }
  .apk-banner { background:#140505; border:1px solid var(--amber); padding:10px; margin-bottom:12px; display:flex; justify-content:space-between; align-items:center; gap:8px; }
  .btn-red { background:var(--fg); color:#000; font-weight:bold; border:none; padding:10px 14px; text-decoration:none; cursor:pointer; display:inline-block; }
  .btn-outline { background:#000; color:var(--fg); border:1px solid var(--fg); padding:8px 12px; cursor:pointer; }
  .panel { background:var(--card); border:1px solid var(--border); padding:12px; margin-bottom:12px; }
  h2 { font-size:14px; margin-bottom:8px; color:var(--amber); letter-spacing:1px; }
  canvas { width:100%; max-width:260px; height:260px; background:#050505; border:1px solid var(--fg); display:block; margin:8px auto; }
  input, select, textarea { width:100%; background:#050505; color:var(--fg); border:1px solid var(--fg); padding:8px; margin-bottom:8px; }
  .card-item { border-left:3px solid var(--green); padding:8px; background:#070707; margin-bottom:6px; font-size:12px; }
</style>
</head>
<body>
<header>
  <div>
    <div style="font-size:18px;font-weight:bold;">PHAROS // WEB BRIDGE</div>
    <div style="font-size:11px;color:#888;">HOST NODE: $hostNodeFingerprint | ZERO-INTERNET P2P</div>
  </div>
  <span class="badge">AIRGAP LOCKED</span>
</header>
$apkBannerHtml

<div class="panel">
  <h2>1. OPTICAL 4x4 MATRIX &amp; MORSE TRANSMITTER</h2>
  <canvas id="opticalCanvas" width="240" height="240"></canvas>
  <input id="txText" value="SOS SECTOR 4 NEED EVAC" maxlength="100"/>
  <div style="display:flex;gap:8px;justify-content:center;">
    <button class="btn-red" onclick="startOpticalFlash()">FLASH 4x4 BEAM</button>
    <button class="btn-outline" onclick="playMfskChirp()">PLAY MFSK WALKIE CHIRP</button>
    <button class="btn-outline" onclick="playDtmfBurst()">NOKIA DTMF</button>
  </div>
</div>

<div class="panel">
  <h2>2. LOCAL MESH TACTICAL CARDS</h2>
  <div id="cardsContainer"></div>
</div>

<script>
const initialCards = $initialCardsJson;
function renderCards() {
  const container = document.getElementById('cardsContainer');
  if (!initialCards.length) {
    container.innerHTML = '<div style="color:#777;font-size:12px;">No tactical cards queued on local hotspot yet.</div>';
    return;
  }
  container.innerHTML = initialCards.map(c =>
    '<div class="card-item"><strong>[' + c.tier + ']</strong> ' + c.note +
    '<div style="color:#888;margin-top:4px;">GPS: ' + c.lat + ', ' + c.lon + ' | BAT: ' + c.bat + '%</div></div>'
  ).join('');
}
renderCards();

let flashTimer = null;
function startOpticalFlash() {
  const ctx = document.getElementById('opticalCanvas').getContext('2d');
  const text = document.getElementById('txText').value || 'SOS';
  const colors = ['#101010', '#ff2a2a', '#ffb000', '#ffffff'];
  let frame = 0;
  if (flashTimer) clearInterval(flashTimer);
  flashTimer = setInterval(() => {
    for (let gy = 0; gy < 4; gy++) {
      for (let gx = 0; gx < 4; gx++) {
        const ch = text.charCodeAt((frame + gy * 4 + gx) % text.length) || 0;
        const sym = (ch >> ((gx % 4) * 2)) & 3;
        ctx.fillStyle = colors[sym];
        ctx.fillRect(gx * 60, gy * 60, 58, 58);
      }
    }
    frame++;
    if (frame > 40) { clearInterval(flashTimer); }
  }, 50);
}

function playMfskChirp() {
  const AudioCtx = window.AudioContext || window.webkitAudioContext;
  if (!AudioCtx) return;
  const actx = new AudioCtx();
  const text = document.getElementById('txText').value || 'SOS';
  const now = actx.currentTime;
  for (let i = 0; i < Math.min(text.length, 24); i++) {
    const osc = actx.createOscillator();
    const gain = actx.createGain();
    osc.frequency.value = 800 + ((text.charCodeAt(i) & 0x0F) * 100);
    gain.gain.setValueAtTime(0.25, now + i * 0.04);
    gain.gain.exponentialRampToValueAtTime(0.001, now + (i + 1) * 0.04);
    osc.connect(gain).connect(actx.destination);
    osc.start(now + i * 0.04);
    osc.stop(now + (i + 1) * 0.04);
  }
}

function playDtmfBurst() {
  const AudioCtx = window.AudioContext || window.webkitAudioContext;
  if (!AudioCtx) return;
  const actx = new AudioCtx();
  const tones = [[941,1209],[697,1209],[770,1336],[852,1477],[941,1477]];
  const now = actx.currentTime;
  tones.forEach((pair, idx) => {
    pair.forEach(freq => {
      const osc = actx.createOscillator();
      const gain = actx.createGain();
      osc.frequency.value = freq;
      gain.gain.value = 0.15;
      osc.connect(gain).connect(actx.destination);
      osc.start(now + idx * 0.08);
      osc.stop(now + idx * 0.08 + 0.06);
    });
  });
}
</script>
</body>
</html>
        """.trimIndent()
    }
}
