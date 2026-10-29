package databack.test.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/// Applies mods' access transformers (`META-INF/<name>`) to Minecraft's classes, as FML does at launch. Tests run on
/// MCP names and the transformers use SRG ones, so members are matched by the MCP name each entry notes in its
/// comment (`field_149782_v #blockHardness`). An uncommented field entry widens all of its class's fields; uncommented
/// method entries are ignored.
final class TestAccessTransformer {

    /// One entry: the access to set, whether to drop `final`, and the member (null name: every field).
    private static final class Rule {

        final int access;
        final boolean definal;
        final boolean field;
        @Nullable
        final String name;
        @Nullable
        final String desc;

        Rule(int access, boolean definal, boolean field, @Nullable String name, @Nullable String desc) {
            this.access = access;
            this.definal = definal;
            this.field = field;
            this.name = name;
            this.desc = desc;
        }

        boolean matches(boolean field, String name, String desc) {
            if (this.field != field) return false;
            if (this.name == null) return true;
            return this.name.equals(name) && (this.desc == null || this.desc.equals(desc));
        }

        int apply(int access) {
            int widened = access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
            widened |= wider(access, this.access);
            return definal ? widened & ~Opcodes.ACC_FINAL : widened;
        }
    }

    /// By internal class name.
    private final Map<String, List<Rule>> rules = new HashMap<>();

    /// Reads every `META-INF/<file>` on the classpath.
    TestAccessTransformer(@NotNull String file) {
        try {
            Enumeration<URL> sources = TestAccessTransformer.class.getClassLoader().getResources("META-INF/" + file);
            for (URL source : Collections.list(sources)) read(source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void read(URL source) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(source.openStream(), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null;) parse(line);
        }
    }

    private void parse(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return;

        int hash = trimmed.indexOf('#');
        String comment = hash < 0 ? null : trimmed.substring(hash + 1).trim();
        String[] parts = (hash < 0 ? trimmed : trimmed.substring(0, hash)).trim().split("\\s+");
        if (parts.length < 3) return;

        int access = parts[0].startsWith("public") ? Opcodes.ACC_PUBLIC
            : parts[0].startsWith("protected") ? Opcodes.ACC_PROTECTED : 0;
        boolean definal = parts[0].endsWith("-f");
        String owner = parts[1].replace('.', '/');

        int paren = parts[2].indexOf('(');
        boolean field = paren < 0;
        String desc = field ? null : parts[2].substring(paren);
        if (comment == null && !field) return;

        rules.computeIfAbsent(owner, k -> new ArrayList<>())
            .add(new Rule(access, definal, field, comment == null ? null : comment.split("\\s+")[0], desc));
    }

    private static int wider(int current, int wanted) {
        if ((current & Opcodes.ACC_PUBLIC) != 0 || wanted == Opcodes.ACC_PUBLIC) return Opcodes.ACC_PUBLIC;
        if ((current & Opcodes.ACC_PROTECTED) != 0 || wanted == Opcodes.ACC_PROTECTED) return Opcodes.ACC_PROTECTED;
        return current & Opcodes.ACC_PRIVATE;
    }

    /// The class with its members widened, or null if no entry names it.
    @Nullable
    byte[] transform(@NotNull String internalName, byte @NotNull [] classFile) {
        List<Rule> classRules = rules.get(internalName);
        if (classRules == null) return null;

        ClassReader reader = new ClassReader(classFile);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {

            @Override
            public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                return super.visitField(widen(access, true, name, desc), name, desc, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(
                int access, String name, String desc, String signature, String[] exceptions
            ) {
                return super.visitMethod(widen(access, false, name, desc), name, desc, signature, exceptions);
            }

            private int widen(int access, boolean field, String name, String desc) {
                for (Rule rule : classRules) {
                    if (rule.matches(field, name, desc)) access = rule.apply(access);
                }
                return access;
            }
        }, 0);
        return writer.toByteArray();
    }
}
