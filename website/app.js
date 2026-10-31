const base=(window.WETTER_API_BASE||"").replace(/\/$/,"");
const $=id=>document.getElementById(id);
const weatherLabel={clear:"☀️ Klar",rain:"🌧️ Regen",thunder:"⛈️ Gewitter"};
async function get(path){const r=await fetch(base+path,{cache:"no-store"});if(!r.ok)throw new Error("HTTP "+r.status);return r.json()}
function escapeHtml(v){return String(v).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]))}
function renderStatus(s){$("connection").textContent="● Verbunden";$("connection").className="pill ok";$("players").textContent=s.online+" / "+s.maxPlayers;$("motd").textContent=s.motd||"Minecraft-Server";$("version").textContent=s.serverVersion||"API verbunden";$("updated").textContent="Aktualisiert: "+new Date(s.sampledAt).toLocaleTimeString("de-DE");$("worlds").innerHTML=(s.worlds||[]).map(w=>'<article class="world"><strong>'+escapeHtml(w.name)+'</strong><div class="weather">'+(weatherLabel[w.weather]||"Unbekannt")+'</div><p class="muted">Weltzeit: '+Math.floor((w.timeTicks%24000)/1000)+' Uhr</p></article>').join("")||'<article class="world empty">Keine Welten gefunden.</article>'}
function renderCameras(d){
  $("camera-state").textContent=d.integration==="securitycams"?"SecurityCams verbunden":d.integration==="not_installed"?"Nicht installiert":"Integration nicht verfügbar";
  const cards=(d.cameras||[]).map(c=>'<article class="world camera-card"><strong>📷 '+escapeHtml(c.name)+'</strong><p class="muted">Besitzer: '+escapeHtml(c.owner)+'</p><p class="muted">Welt: '+escapeHtml(c.world)+'</p><span class="camera-tag">Metadaten verfügbar · kein Browser-Livestream</span></article>').join("");
  const note=d.message||"Keine Kameradaten verfügbar.";
  $("cameras").innerHTML=(cards?'<div class="world-grid">'+cards+'</div>':'<p>'+escapeHtml(note)+'</p>')+(d.liveViewAvailable?'<p class="muted">Livebild verfügbar.</p>':'<p class="muted camera-note">Die Kameraliste kommt direkt aus SecurityCams. Für Livebilder fehlt dem Plugin ein Video-Stream-Ausgang; ein Browserbild wird daher nicht vorgetäuscht.</p>');
}
async function refresh(){try{renderStatus(await get("/api/status"))}catch(e){$("connection").textContent="● Nicht erreichbar";$("connection").className="pill bad";$("motd").textContent="API nicht erreichbar: "+e.message}}
async function cameras(){try{renderCameras(await get("/api/cameras"))}catch(e){$("camera-state").textContent="API nicht erreichbar";$("cameras").textContent="Kamerastatus konnte nicht geladen werden."}}
refresh();cameras();setInterval(refresh,5000);setInterval(cameras,15000);
