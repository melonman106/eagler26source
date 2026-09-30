package com.eaglercraft.patcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.eaglercraft.patcher.PatchEngine.PatchError;

/** Receipt and layout checks for reusing a patcher-created standalone workspace. */
final class ProjectReuse {
    private static final int MAX_RECEIPT_BYTES = 1024 * 1024;
    private static final String TOOL = "eaglercraft-26.2-java-cli";
    private static final String COMMAND = "create-dev";
    private static final String READY = "dev-workspace-ready";
    private static final Set<String> REQUIRED_FILES = Set.of(
            "receipt.json", "baseline-manifest.json", "skeleton-manifest.json",
            "settings.gradle.kts", "build.gradle.kts", "gradlew", "gradle/wrapper/gradle-wrapper.jar",
            "package.json", "package-lock.json", "source/version.json",
            "game/build.gradle.kts", "game/src/main/java",
            "wasm-toolchain/build-single-html.js", "wasm-toolchain/content-verified-brotli.js");

    private ProjectReuse() {
    }

    /** Cheap, non-recursive predicate suitable for an asynchronous CLI preflight. */
    static boolean looksLikeProject(Path directory) {
        try {
            Path root = directory.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return false;
            for (String relative : REQUIRED_FILES) {
                Path member = root;
                for (Path part : Path.of(relative)) {
                    member = member.resolve(part);
                    if (Files.isSymbolicLink(member)) return false;
                }
                if (relative.endsWith("/java")) {
                    if (!Files.isDirectory(member, LinkOption.NOFOLLOW_LINKS)) return false;
                } else if (!Files.isRegularFile(member, LinkOption.NOFOLLOW_LINKS)) {
                    return false;
                }
            }
            Map<String, Object> receipt = receipt(root);
            return TOOL.equals(string(receipt, "tool"))
                    && COMMAND.equals(string(receipt, "command"))
                    && READY.equals(string(receipt, "status"))
                    && "applied".equals(string(receipt, "patch_apply_status"));
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    static Result validate(Path directory, String expectedBundleSha256,
            String expectedSkeletonSha256, String expectedResourceOverlaySha256) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        if (!looksLikeProject(root)) {
            throw new PatchError("reuse project is not a recognized patcher-created workspace (receipt or required layout is missing/incompatible): " + root);
        }
        rejectSymlinkAncestors(root);
        Map<String, Object> receipt = receipt(root);
        requireEqual("official_client_jar_sha256", Main.expectedClientJarSha256(), receipt);
        requireEqual("patch_bundle_sha256", expectedBundleSha256, receipt);
        requireEqual("project_skeleton_sha256", expectedSkeletonSha256, receipt);
        requireEqual("project_skeleton_manifest_sha256", ProjectSkeleton.acceptedManifestSha256(), receipt);
        requireEqual("resource_overlay_sha256", expectedResourceOverlaySha256, receipt);

        String originalManifest = hash(receipt, "final_manifest_sha256");
        long originalFileCount = number(receipt, "final_java_file_count");
        if (originalFileCount < 1) {
            throw new PatchError("reuse project receipt has an invalid original patched-source file count");
        }
        var currentFiles = Main.scanJavaTree(root.resolve("game/src/main/java"));
        if (currentFiles.isEmpty()) {
            throw new PatchError("reuse project has no Java sources under game/src/main/java");
        }
        String currentManifest = sourceManifestDigest(currentFiles);
        Result result = new Result(root, currentFiles.size(), originalFileCount, currentManifest,
                originalManifest, currentManifest.equals(originalManifest));
        ensureGuide(root);
        return result;
    }

    /** Uses the same canonical manifest-object format as PatchEngine's recorded final_manifest_sha256. */
    static String sourceManifestDigest(List<Main.FileRecord> files) {
        List<Object> records = new ArrayList<>(files.size());
        for (Main.FileRecord file : files) {
            records.add(Map.of("path", file.path(), "sha256", file.sha256(), "size", file.size()));
        }
        Map<String, Object> manifest = Map.of(
                "format", "eaglercraft-26.2-java-source-manifest-v1",
                "root", "game/src/main/java",
                "file_count", (long) files.size(),
                "records", records);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Json.canonical(manifest));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError(ex);
        }
    }

    static void ensureGuide(Path root) throws IOException {
        Path guide = root.resolve("GUIDE.md");
        if (Files.exists(guide, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(guide) || !Files.isRegularFile(guide, LinkOption.NOFOLLOW_LINKS)) {
                throw new PatchError("GUIDE.md must be a regular file if present: " + guide);
            }
            return;
        }
        try {
            Files.writeString(guide, GUIDE, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (java.nio.file.FileAlreadyExistsException race) {
            if (Files.isSymbolicLink(guide) || !Files.isRegularFile(guide, LinkOption.NOFOLLOW_LINKS)) throw race;
        }
    }

    /** Makes the pinned decoder files available at the path the HTML packer reads, preserving existing dependencies. */
    static Path preparePinnedDecoder(Path project, Path installedNodeModules) throws IOException {
        Path sourcePackage = installedNodeModules.resolve("brotli-dec-wasm");
        Path sourceJs = sourcePackage.resolve("pkg/brotli_dec_wasm.js");
        Path sourceWasm = sourcePackage.resolve("pkg/brotli_dec_wasm_bg.wasm");
        requireRegular(sourceJs, "pinned Brotli decoder JavaScript");
        requireRegular(sourceWasm, "pinned Brotli decoder Wasm");
        Path nodeModules = project.resolve("node_modules");
        boolean createdNodeModules = false;
        if (Files.exists(nodeModules, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(nodeModules) || !Files.isDirectory(nodeModules, LinkOption.NOFOLLOW_LINKS)) {
                throw new PatchError("reused project node_modules must be a real directory if present: " + nodeModules);
            }
        } else {
            Files.createDirectory(nodeModules);
            createdNodeModules = true;
        }
        Path destinationPackage = nodeModules.resolve("brotli-dec-wasm");
        Path destinationJs = destinationPackage.resolve("pkg/brotli_dec_wasm.js");
        Path destinationWasm = destinationPackage.resolve("pkg/brotli_dec_wasm_bg.wasm");
        Path destinationPkg = destinationPackage.resolve("pkg");
        Path stagedPackage = nodeModules.resolve(".patcher-brotli-decoder-" + UUID.randomUUID());
        boolean installed = false;
        try {
            if (Files.exists(destinationPackage, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(destinationPackage)
                        || !Files.isDirectory(destinationPackage, LinkOption.NOFOLLOW_LINKS)) {
                    throw new PatchError("reused project Brotli package must be a real directory: " + destinationPackage);
                }
                if (Files.isSymbolicLink(destinationPkg)
                        || !Files.isDirectory(destinationPkg, LinkOption.NOFOLLOW_LINKS)) {
                    throw new PatchError("reused project Brotli pkg path must be a real directory: " + destinationPkg);
                }
                requireRegular(destinationJs, "reused project's Brotli decoder JavaScript");
                requireRegular(destinationWasm, "reused project's Brotli decoder Wasm");
                if (Files.mismatch(sourceJs, destinationJs) != -1
                        || Files.mismatch(sourceWasm, destinationWasm) != -1) {
                    throw new PatchError("reused project has a different brotli-dec-wasm decoder; preserving it and stopping");
                }
                return nodeModules;
            }
            Files.createDirectory(stagedPackage);
            copyPackageTree(sourcePackage, stagedPackage);
            Files.move(stagedPackage, destinationPackage, StandardCopyOption.ATOMIC_MOVE);
            installed = true;
            return nodeModules;
        } finally {
            if (!installed) deleteTree(stagedPackage);
            if (createdNodeModules && !installed) {
                try (var children = Files.newDirectoryStream(nodeModules)) {
                    if (!children.iterator().hasNext()) Files.deleteIfExists(nodeModules);
                }
            }
        }
    }

    private static void copyPackageTree(Path source, Path destination) throws IOException {
        rejectSymlinkAncestors(source);
        Files.walkFileTree(source, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path directory,
                    java.nio.file.attribute.BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(directory)) {
                    throw new PatchError("symlink in pinned Brotli package: " + directory);
                }
                Path target = destination.resolve(source.relativize(directory));
                if (!target.equals(destination)) Files.createDirectory(target);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(Path file,
                    java.nio.file.attribute.BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile() || Files.isSymbolicLink(file)) {
                    throw new PatchError("non-regular member in pinned Brotli package: " + file);
                }
                Files.copy(file, destination.resolve(source.relativize(file)));
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private static void requireRegular(Path path, String label) {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new PatchError(label + " is missing or not a regular file: " + path);
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(path, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file,
                    java.nio.file.attribute.BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path directory, IOException error)
                    throws IOException {
                if (error != null) throw error;
                Files.deleteIfExists(directory);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private static Map<String, Object> receipt(Path root) throws IOException {
        Path path = root.resolve("receipt.json");
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new PatchError("reuse project receipt.json is missing or is not a regular file: " + path);
        }
        long size = Files.size(path);
        if (size < 2 || size > MAX_RECEIPT_BYTES) {
            throw new PatchError("reuse project receipt.json has an invalid size: " + size);
        }
        String source = Files.readString(path, StandardCharsets.UTF_8);
        Object parsed = Json.parse(integerizeReceiptNumbers(source).getBytes(StandardCharsets.UTF_8),
                "reuse project receipt.json");
        if (!(parsed instanceof Map<?, ?> raw)) {
            throw new PatchError("reuse project receipt.json must contain a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) raw;
        return result;
    }

    /** Existing build receipts may contain valid decimal timing fields; the source-bundle JSON parser is integer-only. */
    private static String integerizeReceiptNumbers(String source) {
        StringBuilder result = new StringBuilder(source.length());
        int index = 0;
        while (index < source.length()) {
            char ch = source.charAt(index);
            if (ch == '"') {
                int start = index++;
                boolean escaped = false;
                while (index < source.length()) {
                    char current = source.charAt(index++);
                    if (escaped) {
                        escaped = false;
                    } else if (current == '\\') {
                        escaped = true;
                    } else if (current == '"') {
                        break;
                    }
                }
                result.append(source, start, index);
            } else if (ch == '-' || ch >= '0' && ch <= '9') {
                int start = index;
                if (ch == '-') index++;
                if (index >= source.length()) throw new Json.JsonError("reuse project receipt.json: invalid number");
                if (source.charAt(index) == '0') {
                    index++;
                    if (index < source.length() && isDigit(source.charAt(index))) {
                        throw new Json.JsonError("reuse project receipt.json: leading zero in number");
                    }
                } else if (source.charAt(index) >= '1' && source.charAt(index) <= '9') {
                    while (index < source.length() && isDigit(source.charAt(index))) index++;
                } else {
                    throw new Json.JsonError("reuse project receipt.json: invalid number");
                }
                boolean fractional = false;
                if (index < source.length() && source.charAt(index) == '.') {
                    fractional = true;
                    index++;
                    int digitsStart = index;
                    while (index < source.length() && isDigit(source.charAt(index))) index++;
                    if (digitsStart == index) throw new Json.JsonError("reuse project receipt.json: invalid decimal");
                }
                if (index < source.length() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                    fractional = true;
                    index++;
                    if (index < source.length() && (source.charAt(index) == '+' || source.charAt(index) == '-')) index++;
                    int digitsStart = index;
                    while (index < source.length() && isDigit(source.charAt(index))) index++;
                    if (digitsStart == index) throw new Json.JsonError("reuse project receipt.json: invalid exponent");
                }
                if (index < source.length()) {
                    char next = source.charAt(index);
                    if (!(next == ',' || next == ']' || next == '}' || Character.isWhitespace(next))) {
                        throw new Json.JsonError("reuse project receipt.json: invalid number terminator");
                    }
                }
                result.append(fractional ? "0" : source.substring(start, index));
            } else {
                result.append(ch);
                index++;
            }
        }
        return result.toString();
    }

    private static boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    private static String string(Map<String, Object> receipt, String field) {
        Object value = receipt.get(field);
        if (!(value instanceof String string)) {
            throw new PatchError("reuse project receipt is missing string field " + field);
        }
        return string;
    }

    private static String hash(Map<String, Object> receipt, String field) {
        String value = string(receipt, field);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new PatchError("reuse project receipt has an invalid SHA-256 field " + field);
        }
        return value;
    }

    private static long number(Map<String, Object> receipt, String field) {
        Object value = receipt.get(field);
        if (!(value instanceof Long number) || number < 0) {
            throw new PatchError("reuse project receipt is missing non-negative integer field " + field);
        }
        return number;
    }

    private static void requireEqual(String field, String expected, Map<String, Object> receipt) {
        String recorded = field.endsWith("sha256") ? hash(receipt, field) : string(receipt, field);
        if (!recorded.equals(expected)) {
            throw new PatchError("reuse project is incompatible: receipt " + field + " is " + recorded
                    + ", expected " + expected);
        }
    }

    private static void rejectSymlinkAncestors(Path root) {
        Path current = root.getRoot();
        for (Path part : root) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new PatchError("reuse project path contains a symlink: " + current);
            }
        }
    }

    static String guideText() {
        return GUIDE;
    }

    record Result(Path directory, int currentSourceFileCount, long originalSourceFileCount,
            String currentSourceManifestSha256, String originalSourceManifestSha256,
            boolean sourceMatchesOriginalReceipt) {
    }

    private static final String GUIDE = """
            # Eaglercraft 26.2 project guide

            This is an extracted, patched Gradle workspace. Java sources are in `game/src/main/java`.
            Gradle requires Java 25; set `JAVA_HOME` and `PATH` to a Java 25 JDK first, or select the
            patcher's bundled Java 25 runtime.

            Compile the game Java sources from this folder with:

            ```sh
            ./gradlew :game:compileJava --console=plain --no-daemon
            ```

            On Windows, use `gradlew.bat :game:compileJava --console=plain --no-daemon`.

            To export standalone HTML, open the local patcher and choose **Standalone HTML**. If this folder
            does not exist yet, the patcher creates the extracted project as part of the HTML build. To rebuild
            after editing this project, select this folder and enable **Reuse existing project**; choose a new,
            absent `.html` output path. Reuse checks this folder's receipt and pinned input identities, then
            preserves `receipt.json` and existing `GUIDE.md`. Source changes are reported in the build receipt.

            The equivalent CLI mode is `build-standalone ... --output <this-folder> --reuse-project` together
            with the patcher's pinned source, skeleton, resource, Java 25, Node.js, npm, sounds, music, and HTML
            output arguments. The optional music resource-pack ZIP is written beside the HTML. Run
            `java -jar eaglercraft-26.2-java-cli.jar --help` for the exact argument list. Before linking,
            the patcher checks available RAM, retains a system reserve, and stops if the configured build budget
            is below its minimum. Reuse keeps an existing `node_modules` tree; if the pinned Brotli decoder is
            absent, the HTML build adds that package, and stops rather than replacing it if its files differ.

            Open the exported HTML in your browser to test it before sharing it.
            """;
}
