package com.eaglercraft.patcher;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** A launch-time budget, not a guarantee that a particular linker run will fit. */
final class BuildMemoryBudget {
    static final long MIB = 1024L * 1024L;
    static final int MIN_BUILD_MIB = 10240;
    static final int MAX_BUILD_MIB = 13312;
    private final long availableMiB;
    private final int capMiB;

    private BuildMemoryBudget(long availableMiB, int capMiB) {
        this.availableMiB = availableMiB;
        this.capMiB = capMiB;
    }

    static BuildMemoryBudget fromBytes(long total, long available) {
        if (total <= 0 || available <= 0) return new BuildMemoryBudget(0, 0);
        available = Math.min(total, available);
        long reserve = Math.max(2048L * MIB, total / 8);
        long budget = Math.max(0, (available - reserve) / MIB);
        int cap = (int) Math.min(MAX_BUILD_MIB, budget);
        cap = cap / 256 * 256;
        return new BuildMemoryBudget(available / MIB, cap);
    }

    static BuildMemoryBudget detect() {
        long total = 0, available = 0;
        var bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean memory) {
            total = memory.getTotalMemorySize();
            available = memory.getFreeMemorySize();
        }
        try {
            for (String line : Files.readAllLines(Path.of("/proc/meminfo"))) {
                if (line.startsWith("MemTotal:")) total = total > 0 ? Math.min(total, kib(line)) : kib(line);
                if (line.startsWith("MemAvailable:")) available = available > 0 ? Math.min(available, kib(line)) : kib(line);
            }
        } catch (IOException | RuntimeException ignored) {
            // Other operating systems use the management bean above.
        }
        // The JVM accounts for many container limits; also respect a tighter cgroup v2 budget.
        try {
            Path group = Path.of("/sys/fs/cgroup");
            for (String line : Files.readAllLines(Path.of("/proc/self/cgroup"))) {
                if (line.startsWith("0::")) {
                    String relative = line.substring(3);
                    if (relative.startsWith("/")) relative = relative.substring(1);
                    Path candidate = group.resolve(relative).normalize();
                    if (candidate.startsWith(group) && Files.isRegularFile(candidate.resolve("memory.max"))) {
                        group = candidate;
                    }
                    break;
                }
            }
            Path root = Path.of("/sys/fs/cgroup");
            while (group != null && group.startsWith(root)) {
                String limitText = Files.readString(group.resolve("memory.max")).trim();
                if (!"max".equals(limitText)) {
                    long limit = Long.parseLong(limitText);
                    long used = Long.parseLong(Files.readString(group.resolve("memory.current")).trim());
                    if (limit > 0 && used >= 0) {
                        total = total > 0 ? Math.min(total, limit) : limit;
                        available = Math.min(available, Math.max(0, limit - used));
                    }
                }
                group = group.getParent();
            }
        } catch (IOException | RuntimeException ignored) {
            // No readable cgroup v2 limit; retain the OS/JVM measurement.
        }
        return fromBytes(total, available);
    }

    private static long kib(String line) {
        return Math.multiplyExact(Long.parseLong(line.trim().split("\\s+")[1]), 1024L);
    }

    boolean canBuild() { return capMiB >= MIN_BUILD_MIB; }

    int decompilerHeapMiB() {
        if (capMiB < 3072) throw new IllegalArgumentException(
                "Not enough available RAM to extract source safely. Close other apps;"
                + " source extraction needs at least 3 GiB for the process plus the system reserve.");
        return Math.min(4096, capMiB - 1024);
    }

    String sourceSummary() {
        if (capMiB < 3072) return "RAM: source extraction needs 3 GiB available beyond the system reserve.";
        return String.format(Locale.ROOT, "Source extraction: Java heap limit %.1f GiB; system reserve retained.",
                decompilerHeapMiB() / 1024.0);
    }

    void requireBuildCapacity() {
        if (!canBuild()) throw new IllegalArgumentException(summary()
                + " Close other apps and try again. The current linker needs at least 10 GiB"
                + " for the build, plus reserved memory for the system; 16 GiB RAM or more is recommended.");
    }

    String summary() {
        if (availableMiB == 0) return "Available RAM could not be measured; build not started.";
        return String.format(Locale.ROOT, "RAM: %.1f GiB available; build budget %.1f GiB%s.",
                availableMiB / 1024.0, capMiB / 1024.0,
                canBuild() ? " (system reserve excluded)" : " (below the 10 GiB minimum)");
    }

    void applyTo(Map<String, String> env) {
        requireBuildCapacity();
        env.put("EAGLER_BUILD_CAP_MIB", Integer.toString(capMiB));
        env.put("EAGLER_LINK_HEAP_MIB", Integer.toString(capMiB - 2048));
        env.put("EAGLER_SERVER_LINK_HEAP_MIB", Integer.toString(capMiB - 2048));
        env.put("NODE_OPTIONS", "--max-old-space-size=1024");
    }
}
