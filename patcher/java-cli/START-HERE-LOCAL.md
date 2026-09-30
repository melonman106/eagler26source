# Start here: LOCAL-ONLY patcher kit

> LOCAL-ONLY: NOT CLEARED FOR REDISTRIBUTION.

This opt-in kit contains local media and six Minecraft resource files with
unresolved origin and redistribution rights. Use it only on this PC. Do not
upload, publish, or redistribute the kit or its outputs. The official Minecraft
client JAR is not included.

1. On Linux, verify the package before adding the client JAR:
   `./local-media-verify.sh .`. This checks package integrity only. It
   intentionally rejects a package containing the official client JAR.
2. Place the official 26.2 client JAR you are authorized to use at
   `inputs/minecraft-26.2-client.jar`. The GUI cannot start a standalone build
   until you provide it.
3. Launch the GUI with the platform launcher:
   - Linux x86_64: `./bootstrap/launch-gui-linux-x86_64.sh`
   - macOS: `./bootstrap/macos/launch-gui-macos.command`
   - Windows x64: `bootstrap/windows/eagler-patcher-windows-x86_64.cmd`
4. Select **Standalone**. The GUI finds the overlay, sounds/music EPKs and external
   resources under `inputs/`. Include the `--local-bundle` and
   `--local-vineflower` options described in `PORTABLE.md` when packaging if
   those additional inputs should be prefilled too.

Standalone uses the Normal profile and defaults to a smaller HTML with a music
resource-pack ZIP beside it. Import that ZIP in the game's resource-pack screen.
Choose embedded music for a larger single HTML file. Wispcraft is optional and
requires a local `dist/index.js` that you supply.

The Windows and macOS launchers have not been tested on native hosts. The
integrity checker verifies the local kit; it does not build or run the game.
