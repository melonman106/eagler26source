#!/usr/bin/env python3
from pathlib import Path
import sys
root = Path(sys.argv[1]).resolve()
java_root = root / "game" / "src" / "main" / "java"
if not java_root.is_dir():
    raise SystemExit("Expected game/src/main/java in the generated Eagler project.")
out = root / "viabackportvisuals"
out.mkdir(parents=True, exist_ok=True)
(out / "VBV_PROTOCOL.md").write_text("""# ViaBackportVisuals client protocol

Channel: viabackportvisuals:marker

Payload: BlockPos, VarInt visualId, Boolean remove.

Visual IDs: 0-15 wool stairs; 16-31 wool slabs; 32-47 concrete stairs; 48-63 concrete slabs; 64 straw bed.

Markers are keyed by absolute BlockPos and are consulted only for rendering the block at that exact position.
""", encoding="utf-8")
(out / "VBV_INTEGRATION_PENDING.md").write_text("""# Native integration staging

The project was found, but this source export does not expose a verified networking/model-renderer anchor that can be safely rewritten automatically.

No guessed source modification was made. The native patch must be applied against the actual generated 26.2 game source and compiled before the HTML build is considered valid.
""", encoding="utf-8")
print("VBV staging completed; no unsafe blind source rewrite was performed.")
