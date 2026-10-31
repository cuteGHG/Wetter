# Wetter — Minecraft Server Dashboard

Read-only Paper 26.3 plugin and static public dashboard.

## Requirements
- Paper 26.3
- Java 25 (required by Paper 26.x)
- Maven 3.9+

## Build the plugin
```bash
mvn clean package
```
The JAR is written to `target/wetter-0.1.0.jar`. Copy it into the Paper server's `plugins/` directory and restart.

## API
The plugin starts a local HTTP API at `127.0.0.1:8765`:
- `GET /api/status`: online/max player count, MOTD, server version, world weather and world time.
- `GET /api/cameras`: camera integration status.

The API is intentionally read-only and does not expose player names or remote administration. Keep port 8765 private. For public access, put the API behind an HTTPS reverse proxy, allow only GET/OPTIONS and apply rate limiting. Do not expose port 8765 directly.

## Website
Host the contents of `website/` as static files. If the site and API share the same HTTPS origin, leave `WETTER_API_BASE` empty. Otherwise set `window.WETTER_API_BASE` in `website/config.js` to the public HTTPS URL of the reverse proxy.

## SecurityCams v3
The camera endpoint currently returns `integration: not_connected`. A real browser live view still needs a confirmed stream/API from SecurityCams v3. The dashboard deliberately does not fabricate images or claim a non-existent stream is live.

## Status
This is the first source implementation committed to the repository, not a verified release JAR. It still needs a Maven build and a test on your exact Paper build. Do not deploy it publicly until that test and the HTTPS reverse-proxy setup are complete.
