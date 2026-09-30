# Native Eagler build

The native client is built from a generated 26.2 Eagler project, not from the Fabric client JAR.

Expected project layout: game/src/main/java, build.gradle.kts, settings.gradle.kts, and the normal Eagler target/toolchain folders.

The integration is intentionally fail-closed if exact networking/rendering anchors cannot be verified.
