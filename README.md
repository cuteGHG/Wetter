# Wetter — Minecraft Server Dashboard

Read-only Paper plugin and static public dashboard. The plugin exposes server status and reads camera metadata from SecurityCams v3 when that plugin is installed.

## Requirements
- Paper 26.3
- Java 25
- Maven 3.9+
- SecurityCams v3 (optional, for camera metadata)

## Build
```bash
mvn clean package
```
The JAR is written to `target/wetter-0.2.0.jar`. Copy it into Paper's `plugins/` directory and restart. Install SecurityCams separately as usual.

## API
Default bind: `127.0.0.1:8765`.
- `GET /api/status`: player count, MOTD, server version, world weather and time.
- `GET /api/cameras`: reads the actual SecurityCams camera list (ID, camera name, owner and world) through its internal `CamStorage` API.

The integration uses reflection and `softdepend: [SecurityCams]`, so Wetter can start if SecurityCams is absent. Camera metadata is read on the server thread. No player viewing controls or camera movement endpoints are exposed.

## Important: browser live video
SecurityCams v3's documented live mode is a Minecraft player-viewing session: a player connects to a camera and sees the view in their Minecraft client. Its documented feature set includes in-game live viewing and replaying saved recordings, but does not document an HTTP, RTSP, HLS, WebRTC or browser-video output. Therefore this adapter can list real cameras but **cannot display their live pictures in a website**.

For an actual public live picture, a separate capture pipeline is needed: a dedicated Minecraft account/client joins the camera, its client window is captured (for example with OBS), and OBS publishes a secured stream (preferably WebRTC or HLS) that the website embeds. This requires a running Minecraft client and a streaming service/process; it cannot be achieved by installing only a Paper plugin. Do not expose a Minecraft account or stream-control token to the public website.

## Website and public deployment
Host `website/` as static files. Keep port 8765 private on localhost. Put the API behind an HTTPS reverse proxy, restrict it to GET/OPTIONS, and rate-limit it. Set `window.WETTER_API_BASE` in `website/config.js` if the API is on another origin.

## Status
Initial camera metadata adapter source is committed; it has not yet been compiled or tested against your exact server/plugin runtime. It is not a live-video implementation.
