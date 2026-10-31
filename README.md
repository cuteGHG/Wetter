# Wetter — Minecraft Server Dashboard

Read-only Paper plugin and static public dashboard. The plugin reads real camera metadata from SecurityCams v3 and can optionally embed an externally produced browser-playable video stream for each camera.

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

## Camera integration
`GET /api/cameras` reads SecurityCams' actual internal `CamStorage` list using a soft dependency and reflection. It returns each camera's ID, name, owner and world. It does not expose exact block coordinates or provide camera-control endpoints.

### Important: SecurityCams does not output browser video
SecurityCams' documented live mode moves a Minecraft player into a camera view inside the Minecraft client. It does not document an HTTP, RTSP, HLS, WebRTC or browser-video output. The plugin therefore cannot directly supply a website video.

For website live video, a separate capture pipeline is required:
1. Run a dedicated Minecraft client/account that can watch the relevant SecurityCams camera.
2. Capture that client window with OBS (or equivalent) and publish a browser-compatible stream (HLS or MP4/WebM; WebRTC needs a compatible player integration).
3. Add the public HTTPS stream URL to `plugins/Wetter/config.yml` under `camera-streams`, keyed by the SecurityCams camera ID, for example:
   ```yaml
   camera-streams:
     "1": "https://stream.example.net/camera-1/index.m3u8"
   ```
4. Restart Wetter and host the website files. The dashboard will display a player only for cameras with a configured HTTPS URL.

The URL is public because visitors to a public website can inspect it. Do not put private stream credentials or OBS control tokens in this setting. Protect the stream separately at your streaming server/CDN if needed. HLS playback uses hls.js from jsDelivr where the browser lacks native HLS support.

## API and hosting
Default API bind: `127.0.0.1:8765`.
- `GET /api/status`: player count, MOTD, server version, world weather and time.
- `GET /api/cameras`: SecurityCams camera metadata and optional configured HTTPS stream URLs.

Host `website/` as static files. Keep port 8765 private. Put the API behind an HTTPS reverse proxy, restrict it to GET/OPTIONS, and rate-limit it. Set `window.WETTER_API_BASE` in `website/config.js` if the API is on another origin.

## Status
The SecurityCams metadata adapter and optional external-stream display are implemented in source. This has not yet been compiled or tested against your live Paper/SecurityCams installation, and no video capture process is included: the external viewer + OBS stream must be set up separately.
