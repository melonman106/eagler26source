# Eaglercraft 26.2 u1 patcher launcher

Open `eaglercraft-26.2-u1-patcher-gui.jar` with Java 17 or newer to use the
desktop window. Run `eagler-patcher` on Linux/macOS or `eagler-patcher.cmd` on Windows for the CLI. Set
`EAGLER_PATCHER_JAVA` when Java 17 or newer is not named `java` on `PATH`.

Platform launchers can download verified Java 17, Java 25, and Node/npm into
`.toolchain/` before opening the GUI. Use
`bootstrap/launch-gui-linux-x86_64.sh` on glibc Linux x86_64,
`bootstrap/macos/launch-gui-macos.command` on macOS 13+ Intel/Apple Silicon,
or `bootstrap/windows/eagler-patcher-windows-x86_64.cmd` on supported Windows
10/11 x64. They do not change system packages. See each platform README for
network, disk, OS, and architecture requirements. The macOS and Windows
launchers have not been run on native hosts.

The default distribution contains no Minecraft JAR, decompiled
Minecraft source or Minecraft assets. The explicit
`--local-media` opt-in below adds local-test-only media and six unresolved
Minecraft resource files; that package is for this PC only and is not cleared
for redistribution. No option packages the official Minecraft client JAR.
Supply the exact official 26.2 client JAR and the source-patch,
verified project-skeleton, and resource-overlay inputs. Vineflower can be included
using `--local-vineflower` below, with its Apache 2.0 license and exact JAR hash
checked first. The accepted project-skeleton archive SHA-256 is
`e76f606630ce6596061e7ac5a76d01a541846cac7d8d1424ec38a942ab00c071`.

`create-dev` reconstructs the authenticated Gradle workspace. `build-standalone`
accepts the same inputs plus `--java25`, `--node`, `--npm`, `--sounds-epk`,
`--expected-sounds-epk-sha256`, `--music-epk`, `--expected-music-epk-sha256`,
and `--standalone-output`. It runs the selected npm CLI through the selected Node
executable using `npm ci --ignore-scripts --no-audit --no-fund`, with a bounded
timeout and receipt hashes. It refuses a compile-only skeleton before
decompilation; a standalone-capable skeleton must contain the authenticated
Node/linker/deployment substrate. The separately authorized sounds and music EPKs are
accepted only with caller-supplied exact SHA-256 values and staged into the generated
workspace; no hidden checkout copy is used. Output is staged under a random
partial filename and promoted only after the builder succeeds. Progress lines
on stderr report elapsed seconds. Receipts record a local candidate; they do
not establish browser acceptance or release readiness.

`build-standalone` creates patched source before producing HTML; running
`create-dev` first is optional. Add `--reuse-project` when `--output` names an
earlier CLI-created project. The CLI validates its receipt, pins, and required
layout before reuse, and preserves its source and receipt. Other non-empty
directories are rejected. Current Wasm builds need at least 10 GiB of detected
build-memory budget plus OS reserve (typically a 16 GiB machine). The CLI
rechecks memory before compiling and refuses an insufficient budget. Lower
memory can slow the build and does not guarantee completion.

The default portable directory contains the launcher JAR, launchers, and
documentation. To opt into a local bundle, pass both caller-provided archives:

```sh
./make-portable.sh /path/to/eaglercraft-26.2-u1-patcher \
  --local-bundle /path/to/source-patch-bundle.zip \
  /path/to/project-skeleton-iwa.zip \
  --local-vineflower /path/to/vineflower-1.12.0.jar
```

The option accepts only the fixed reviewed source-patch bundle SHA-256
`df3af583c06aa22748d21f039980cdd3923dbc7ab0bc21accbbb28b1cd1e7389` and
verified skeleton SHA-256
`e76f606630ce6596061e7ac5a76d01a541846cac7d8d1424ec38a942ab00c071`. A
mismatch stops packaging before output creation. When accepted, the files
are copied under stable names `source-patch-bundle.zip` and
`project-skeleton-v5-teavm-runtime-verified.zip`, and both are covered by the
distribution's `SHA256SUMS`. The optional Vineflower input must match SHA-256
`1dfcfe974395734fa467ce620661c7623d05ba83670de0529b1fbd63ff548b9d`; its
license and attribution are copied beside the JAR. The default invocation adds
neither sidecar nor Vineflower.

To add the local-only overlay, sounds and music EPKs, and six external resource inputs,
also pass `--local-media`:

```sh
./make-portable.sh /path/to/eaglercraft-26.2-u1-patcher \
  --local-bundle /path/to/source-patch-bundle.zip \
  /path/to/project-skeleton-iwa.zip \
  --local-vineflower /path/to/vineflower-1.12.0.jar \
  --local-media
```

`--local-media` reads the fixed local files from this checkout. Before staging,
it requires the overlay SHA-256
`2ba7e3376891c64f8bf57f3687e05b8dbe1971a75475b6825449e5e5f96d71f3`, sounds
EPK SHA-256
`94bc8bfcf4132c52c6d5f61f3f92e50532a6fad1f5bc901ee25a462db8ba4fc6`, music
EPK SHA-256 `f01cdaf62a9686438998b11ffed5407a1b890e14960c63f71b57401d02216a4e`, and the
six exact resource hashes allowlisted by the resource-overlay tool. It verifies
the inputs before copy, checks both hashes and bytes after copy, and promotes
the package from a same-directory staging folder only after checks pass. The
GUI finds these files at `inputs/resource-overlay-normal.zip`,
`inputs/sounds.epk`, `inputs/sounds.epk.sha256`, `inputs/music.epk`,
`inputs/music.epk.sha256`, and
`inputs/resources/`. `START-HERE-LOCAL.md` explains where to place the official
client JAR and is included only in a `--local-media` package; the client JAR
itself is intentionally not included. The package includes
`local-media-verify.sh`, and `SHA256SUMS` covers every packaged file except
itself. The verifier checks kit integrity only; it does not build or run the
game. Do not share or redistribute a `--local-media` package or its outputs.

All three modes use the Normal profile. The local-media package contains no
mod source archives and remains local-only; do not share or redistribute it.
