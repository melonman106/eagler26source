# Eaglercraft 26.2 patcher sources

This folder contains the Java patcher, its Swing interface, and the source
patch and project-skeleton exporters. It does not include a game build or all
the files needed to create one.

See [GUIDE.md](GUIDE.md) for instructions to build the Normal client and host
the output, including the local Isolated Web App workflow.

## Build and open the window

Build with JDK 17:

```sh
cd patcher/java-cli
./build.sh
```

The GUI JAR is written to
`patcher/java-cli/build/eaglercraft-26.2-u1-patcher-gui.jar`. Open it with
Java, or double-click it if your desktop associates JAR files with Java. From
a terminal, run:

```sh
java -jar build/eaglercraft-26.2-u1-patcher-gui.jar
```

Run `patcher/java-cli/eagler-patcher --help` to see the command-line options.
After building, `./test-gui.sh` checks the GUI wiring without opening a window.

## Source project or HTML?

Choose **Standalone HTML** for a playable file. With a new project folder, this
creates the patched source and then compiles it; you do not need to run
**Source project** first. The source stays in the project folder.

Choose **Source project** when you only want the editable project. Generated
projects include `GUIDE.md` with build and output instructions. For an existing
compatible project, use the HTML mode's reuse option: it compiles that project's
source without extracting or patching it again. Unrelated folders and
incompatible projects are rejected. Keep backups of your edits.

HTML builds need substantially more memory than source extraction. The patcher
measures available RAM and leaves a system reserve before choosing Java limits.
The current linker needs a build budget of at least 10 GiB, in addition to that
reserve; 16 GiB total RAM or more is recommended. Other running apps may leave
too little available memory even on a 16 GiB machine. A smaller allowed heap
may build more slowly or run out of heap; the budget is not a completion guarantee.

## Build time

Expect a full HTML build to take about **30 minutes on a reasonably capable PC**,
based on the maintainer's reported experience. Slower
hardware, downloads, power-saving mode, or memory pressure can take longer.
This is a rough estimate, not a benchmark or deadline for every PC.

For reference, a completed local Normal build on 29 September 2026 recorded
41 minutes 46 seconds from start to HTML output. Allow extra time rather than
treating the estimate as a fixed countdown.

Source extraction alone is much shorter. Reusing a project skips extraction
and patching, but the WebAssembly compilation can still take most of the time.
The patcher shows elapsed time rather than an invented countdown. Leave it
running while the compiler is working; do not start another build in the same
project folder.

## App-local tool setup

The Linux launcher can install Java 17, Java 25, Node.js 24, and npm inside the
patcher folder. Build the GUI and copy its JAR beside the launcher:

```sh
cd patcher/java-cli
cp build/eaglercraft-26.2-u1-patcher-gui.jar .
./bootstrap/launch-gui-linux-x86_64.sh
```

It supports glibc Linux x86_64 and requires an internet connection. The
launcher checks vendor SHA-256 values and stores the tools in `.toolchain`
inside the application folder. Run it as a normal user, without `sudo`.

The Windows launcher is in `patcher/java-cli/bootstrap/windows/`. Build the
GUI JAR with Bash (for example, Git Bash or WSL), copy it to
`patcher/java-cli/eaglercraft-26.2-u1-patcher-gui.jar`, and run
`patcher/java-cli/bootstrap/windows/eagler-patcher-windows-x86_64.cmd`.
The script looks for the JAR at the package root, not beside the `.cmd` file.
It supports Windows 10 22H2 or Windows 11 x64, 64-bit PowerShell 5.1 or newer,
a non-administrator account, and a writable local folder. It downloads the
vendor tool archives and stores them beside the patcher. Native Windows
execution has not been verified. This source checkout has no prebuilt JAR; the
separate local package does.

## Making and applying a source patch

`patcher/source-patches/export_source_patches.py` compares two Java source
trees and writes a deterministic bundle of add, modify, and delete operations.
File hashes identify each operation. The Java CLI checks those hashes before
applying a bundle.

For example, with two source trees you are authorized to use:

```sh
python3 patcher/source-patches/export_source_patches.py export \
  --base /path/to/base-java \
  --final /path/to/changed-java \
  --output /tmp/source-patch \
  --archive /tmp/source-patch.zip
```

The exporter can also verify and apply a bundle. See
`patcher/source-patches/README.md` for the commands and required source trees.
The Java CLI accepts only its pinned source bundle. To use another bundle,
change that pin in the source first.

`patcher/project-skeleton/export_skeleton.py` shows how the non-game project
files are collected into a pinned skeleton ZIP. The portable-package script
is also included for reference, but needs the omitted local inputs and full
workspace layout to run. The two `wasm-toolchain` scripts show the standalone
HTML and music-pack output paths; this source-only repo does not contain the
rest of the game build tree.

## Inputs and limits

`create-dev` requires an official 26.2 client JAR, Vineflower 1.12.0, Java 17,
and the pinned source patch bundle. The client JAR and bundle must match fixed
hashes. `create-dev` reconstructs Java source; it does not compile the game.
`build-standalone` also requires the pinned project skeleton, Java 25, Node/npm,
and authorized sounds and music EPKs. The default makes a smaller HTML and a
music resource-pack ZIP beside it; import the ZIP in-game. Embedded music makes
a larger single HTML. Wispcraft injection accepts a local script. Only the
Normal client is supported. Resource reconstruction requires the local
resource overlay and its six external files.

These inputs are not included. The source patch archive contains
Mojang-derived Java changes and is omitted from this export. The resource
overlay is marked `local-test-only`; its provenance includes files whose
redistribution rights are unresolved. This export also omits decompiled game
source, game assets, project skeleton archives, sound and media files,
generated JARs and classes, caches, and third-party mod binaries.

Use only input files you are entitled to use. This source folder has no license
file and does not grant permission to reuse the code or any external inputs.
