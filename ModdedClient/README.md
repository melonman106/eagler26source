# ViaBackportVisuals Eagler 26.2 client integration

This folder contains the native Eagler 26.2 client-side integration for ViaBackportVisuals.

The Eagler build does not load the Fabric JAR. It applies the VBV client integration to a generated 26.2 source project before building the HTML client.

## Protocol

Channel: viabackportvisuals:marker

Payload:
1. Block position
2. Visual ID as VarInt
3. Remove flag as boolean

Visual IDs:
- 0-15 wool stairs
- 16-31 wool slabs
- 32-47 concrete stairs
- 48-63 concrete slabs
- 64 straw bed

The renderer must be position-aware. It must not globally replace vanilla stair, slab, bed, deepslate, copper, or other block models.

## Build input

This repository contains the Eagler patcher/source tooling, not a complete redistributable Minecraft/Eagler game source tree. The normal Eagler patcher still needs its documented authorized 26.2 inputs.

The Actions workflow expects an authorized generated project in project/ or generated-project/. It applies this integration and places generated HTML files in release/.

Do not commit restricted Minecraft client JARs, decompiled source, or restricted resource archives.
