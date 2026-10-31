# Wetter — Ingame-Dashboard

Wetter is a Paper plugin with an inventory GUI. No website or HTTP API is required.

## Requirements
- Paper 26.3
- Java 25
- SecurityCams 3.x for camera integration

## Commands
- `/wetter` — open the GUI (online players, current world weather and world clock, public cameras)
- `/wetter add <Kameraname>` — admin: publish an existing SecurityCams camera by its exact display name (case-insensitive)
- `/wetter remove <Kameraname>` — admin: remove it from the public list
- `/wetter list` — admin: list public cameras
- `/wetter reload` — admin: reload config
- `/wetter help` — show commands

Admin commands require `wetter.admin` (default: op). Public cameras are saved in `plugins/Wetter/config.yml`.

## Camera viewing
When a player clicks a published camera, Wetter calls SecurityCams 3.x's own `PlayerCamsManager.connectToCam` integration. It temporarily grants the SecurityCams permission needed to view that published camera, then removes the temporary permission. SecurityCams remains responsible for the actual in-game camera session and its normal cleanup.

## Build
`mvn clean package` creates `target/wetter-0.3.0.jar`. Install the JAR in `plugins/` beside SecurityCams and restart the server.

The integration uses reflection against SecurityCams 3.x internals and therefore must be verified on the target server build before production use.
