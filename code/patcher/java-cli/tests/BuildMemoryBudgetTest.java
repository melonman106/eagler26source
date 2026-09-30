package com.eaglercraft.patcher;

import java.util.HashMap;

public final class BuildMemoryBudgetTest {
    public static void main(String[] args) {
        long gib = 1024L * BuildMemoryBudget.MIB;
        if (BuildMemoryBudget.fromBytes(8 * gib, 7 * gib).canBuild()) throw new AssertionError("8 GiB");
        if (BuildMemoryBudget.fromBytes(16 * gib, 8 * gib).canBuild()) throw new AssertionError("busy host");
        if (BuildMemoryBudget.fromBytes(0, 0).canBuild()) throw new AssertionError("unknown");
        var env = new HashMap<String, String>();
        BuildMemoryBudget.fromBytes(16 * gib, 14 * gib).applyTo(env);
        if (!"12288".equals(env.get("EAGLER_BUILD_CAP_MIB"))) throw new AssertionError(env);
        if (!"10240".equals(env.get("EAGLER_LINK_HEAP_MIB"))) throw new AssertionError(env);
        if (!env.get("EAGLER_LINK_HEAP_MIB").equals(env.get("EAGLER_SERVER_LINK_HEAP_MIB"))) throw new AssertionError(env);
        BuildMemoryBudget.fromBytes(64 * gib, 60 * gib).applyTo(env);
        if (!"13312".equals(env.get("EAGLER_BUILD_CAP_MIB"))) throw new AssertionError(env);
        try {
            BuildMemoryBudget.fromBytes(8 * gib, 7 * gib).applyTo(env);
            throw new AssertionError("low memory accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("BuildMemoryBudgetTest PASS");
    }
}
