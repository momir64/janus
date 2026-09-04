import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Rewrites AndroidTestResultListener.getDeviceId so its return value passes through
 * URLDecoder.decode(value, "UTF-8").
 *
 * That method extracts the device id from a JUnit Platform unique id, whose segment values are
 * percent-encoded, so a device connected as "adb connect host:port" is reported to Android Studio
 * as 192.168.0.7%3A5555 and never matches the raw serial the IDE knows it by. Studio then fails to
 * attribute the results, dumps the raw UTP_TEST_RESULT_ON_TEST_RESULT_EVENT blocks to the build
 * console, and marks the run failed. Decoding here makes the emitted events carry the real serial.
 *
 * The class is patched rather than replaced because it is large and builds result protos; only the
 * one method needs changing.
 *
 * Usage: java -cp <asm.jar>;<this dir> PatchListener <path to jar> <output dir>
 */
public final class PatchListener {
    private static final String CLASS_ENTRY =
            "com/android/tools/androidtest/listener/AndroidTestResultListener.class";

    public static void main(String[] args) throws Exception {
        Path jar = Paths.get(args[0]);
        Path outDir = Paths.get(args[1]);

        byte[] original;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(CLASS_ENTRY);
            if (entry == null) {
                throw new IllegalStateException("entry not found: " + CLASS_ENTRY);
            }
            try (InputStream in = zip.getInputStream(entry)) {
                original = in.readAllBytes();
            }
        }

        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        final boolean[] patched = {false};

        reader.accept(
                new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override
                    public MethodVisitor visitMethod(
                            int access, String name, String descriptor,
                            String signature, String[] exceptions) {
                        MethodVisitor mv =
                                super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (!"getDeviceId".equals(name)
                                || !descriptor.endsWith(")Ljava/lang/String;")) {
                            return mv;
                        }
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.ARETURN) {
                                    visitLdcInsn("UTF-8");
                                    visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "java/net/URLDecoder",
                                            "decode",
                                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                            false);
                                    patched[0] = true;
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                },
                0);

        if (!patched[0]) {
            throw new IllegalStateException("getDeviceId not found - class layout changed");
        }

        Path out = outDir.resolve(CLASS_ENTRY);
        Files.createDirectories(out.getParent());
        Files.write(out, writer.toByteArray());
        System.out.println("wrote " + out);
    }
}
