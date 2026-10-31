const base=(window.WETTER_API_BASE||"").replace(/\/$/,"");
const $=id=>document.getElementById(id);
const weatherLabel={clear:"☀️ Klar",rain:"🌧️ Regen",thunder:"⛈️ Gewitter"};
async function get(path){const r=await fetch(base+path,{cache:"no-store"});if(!r.ok)throw new Error("HTTP "+r.status);return r.json()}
function escapeHtml(v){return String(v).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]))}
function renderStatus(s){$("connection").textContent="● Verbunden";$("connection").className="pill ok";$("players").textContent=s.online+" / "+s.maxPlayers;$("motd").textContent=s.motd||"Minecraft-Server";$("version").textContent=s.serverVersion||"API verbunden";$("updated").textContent="Aktualisiert: "+new Date(s.sampledAt).toLocaleTimeString("de-DE");$("worlds").innerHTML=(s.worlds||[]).map(w=>'<article class="world"><strong>'+escapeHtml(w.name)+'</strong><div class="weather">'+(weatherLabel[w.weather]||"Unbekannt")+'</div><p class="muted">Weltzeit: '+Math.floor((w.timeTicks%24000)/1000)+' Uhr</p></article>').join("")||'<article class="world empty">Keine Welten gefunden.</article>'}
function attachVideo(video){
  const url=video.dataset.stream;if(!url)return;
  if(url.toLowerCase().includes(".m3u8")&&window.Hls&&window.Hls.isSupported()){
    const hls=new Hls({enableWorker:true});hls.loadSource(url);hls.attachMedia(video);
    hls.on(Hls.Events.ERROR,(_,data)=>{if(data.fatal)video.insertAdjacentHTML("afterend",'<p class="muted">Stream konnte nicht geladen werden.</p>')});
  }else if(video.canPlayType("application/vnd.apple.mpegurl"))video.src=url;
  else video.src=url;
}
function renderCameras(d){
  $("camera-state").textContent=d.integration==="securitycams"?"SecurityCams verbunden":d.integration==="not_installed"?"Nicht installiert":"Integration nicht verfügbar";
  const cards=(d.cameras||[]).map(c=>{
    const stream=c.streamUrl?'<video class="camera-video" controls autoplay muted playsinline data-stream="'+escapeHtml(c.streamUrl)+'"></video>':'<div class="camera-no-feed">Kein Stream konfiguriert</div>';
    return '<article class="world camera-card"><strong>📷 '+escapeHtml(c.name)+'</strong>'+stream+'<p class="muted">Besitzer: '+escapeHtml(c.owner)+'</p><p class="muted">Welt: '+escapeHtml(c.world)+'</p></article>';
  }).join("");
  $("cameras").innerHTML=(cards?'<div class="world-grid">'+cards+'</div>':'<p>'+escapeHtml(d.message||"Keine Kameradaten verfügbar.")+'</p>')+'<p class="muted camera-note">SecurityCams liefert die echte Kameraliste. Ein Bild erscheint nur mit einer konfigurierten HTTPS-Stream-URL. SecurityCams selbst erzeugt keinen Browser-Videostream.</p>';
  $("cameras").querySelectorAll("video[data-stream]").forEach(attachVideo);
}
async function refresh(){try{renderStatus(await get("/api/status"))}catch(e){$("connection").textContent="● Nicht erreichbar";$("connection").className="pill bad";$("motd").textContent="API nicht erreichbar: "+e.message}}
async function cameras(){try{renderCameras(await get("/api/cameras"))}catch(e){$("camera-state").textContent="API nicht erreichbar";$("cameras").textContent="Kamerastatus konnte nicht geladen werden."}}
refresh();cameras();setInterval(refresh,5000);setInterval(cameras,15000);
