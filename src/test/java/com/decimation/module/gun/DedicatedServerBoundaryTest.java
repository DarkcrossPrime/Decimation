package com.decimation.module.gun;

import com.decimation.Decimation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.Utf8Entry;

/** Checks compiled common code, including descriptors and bootstrap handles, for client-only links. */
public final class DedicatedServerBoundaryTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(Decimation.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!Files.isDirectory(root)) throw new AssertionError("Run against the compiled main source set");
        int classes = 0;
        try (var files = Files.walk(root.resolve("com/decimation"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (relative.startsWith("com/decimation/client/")) continue;
                // All type references/descriptors/bootstrap handles are backed by these UTF-8 entries.
                // Also reject reflective class-name literals; no extra test-only dependency needed.
                for (var entry : ClassFile.of().parse(Files.readAllBytes(file)).constantPool()) {
                    if (!(entry instanceof Utf8Entry text)) continue;
                    String value = text.stringValue().replace('.', '/');
                    for (String forbidden : new String[]{"net/minecraft/client/", "net/fabricmc/fabric/api/client/", "org/lwjgl/", "com/decimation/client/"})
                        if (value.contains(forbidden)) throw new AssertionError(relative + " references client-only type " + text.stringValue());
                }
                classes++;
            }
        }
        if (classes == 0) throw new AssertionError("No common classes inspected");
        System.out.println("Dedicated-server linkage boundary passed: " + classes + " common classes (not a server boot test).");
    }
}
