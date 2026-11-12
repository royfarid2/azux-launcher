# Azux Launcher
Landscape Android Minecraft launcher.
- Asks for a player name on first launch (offline account, UUID generated like Minecraft offline mode).
- Loads every Minecraft version from Mojang, downloads game jar + libraries + assets inside the app with real progress, speed, ETA and SHA-1 verification.
- Select / delete installed versions. Cancel and re-press Download to resume.
Status: the Java runtime + renderer (what actually runs the game) is not connected yet, so PLAY explains that instead of faking a launch.
Build: push to GitHub -> Actions -> "Build Azux Launcher" -> download the APK artifact.
