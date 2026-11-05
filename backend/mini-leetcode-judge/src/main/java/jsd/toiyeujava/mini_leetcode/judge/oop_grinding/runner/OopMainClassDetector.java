package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner;

import lombok.extern.slf4j.Slf4j;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Phát hiện lớp có phương thức {@code public static void main(String[])}
 * bằng cách ĐỌC BYTECODE, không nạp hay thực thi bytecode của học viên
 * trong JVM của judge worker.
 *
 * <p>Việc nạp lớp học viên vào JVM của worker (như {@code URLClassLoader} trong
 * {@code OopGrindingInspector}) sẽ cho phép mã độc chạy ngay trên host. Vì vậy
 * ở đây ta chỉ parse header của file {@code .class}: magic, constant pool,
 * access flags và bảng method — đủ để biết lớp nào là entry point.
 */
@Slf4j
public final class OopMainClassDetector {

    /** Chuỗi mô tả của {@code public static void main(String[] args)}. */
    private static final String MAIN_DESCRIPTOR = "([Ljava/lang/String;)V";

    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_STATIC = 0x0008;

    private static final int CONSTANT_UTF8 = 1;
    private static final int CONSTANT_INTEGER = 3;
    private static final int CONSTANT_FLOAT = 4;
    private static final int CONSTANT_LONG = 5;
    private static final int CONSTANT_DOUBLE = 6;
    private static final int CONSTANT_CLASS = 7;
    private static final int CONSTANT_STRING = 8;
    private static final int CONSTANT_FIELDREF = 9;
    private static final int CONSTANT_METHODREF = 10;
    private static final int CONSTANT_INTERFACE_METHODREF = 11;
    private static final int CONSTANT_NAME_AND_TYPE = 12;
    private static final int CONSTANT_METHOD_HANDLE = 15;
    private static final int CONSTANT_METHOD_TYPE = 16;
    private static final int CONSTANT_DYNAMIC = 17;
    private static final int CONSTANT_INVOKE_DYNAMIC = 18;

    /**
     * Tên lớp harness do hệ thống sinh ra — KHÔNG phải main của học viên.
     *
     * <p>Probe REPL của console ({@code __ConsoleProbe}) và probe đo số liệu
     * ({@code __OopMetricsProbe}) đều có {@code public static void main(String[])}.
     * Nếu không loại trừ chúng, detector có thể chọn nhầm probe làm entry point
     * của học viên (đặc biệt khi tên lớp học viên bắt đầu bằng chữ thường, vì
     * {@code '_'} (0x5F) sort trước chữ thường trong ASCII).
     */
    private static final Set<String> GENERATED_HARNESS_CLASSES = Set.of(
            "__ConsoleProbe",
            "__OopMetricsProbe",
            "__ExprEval");

    private OopMainClassDetector() {
    }

    /**
     * Tìm lớp đầu tiên (theo thứ tự ổn định) có {@code public static void main(String[])}.
     *
     * @param classesDir thư mục chứa file {@code .class}
     * @return tên lớp đầy đủ (dấu chấm) hoặc {@link Optional#empty()} nếu không có
     */
    public static Optional<String> detectMainClass(Path classesDir) {
        return detectMainClasses(classesDir).stream().findFirst();
    }

    /**
     * Mọi lớp học viên có {@code public static void main(String[])}, theo thứ tự ổn định.
     * Console dùng để báo lỗi khi có nhiều hơn một entry point.
     */
    public static List<String> detectMainClasses(Path classesDir) {
        List<String> mains = new ArrayList<>();
        if (classesDir == null || !Files.isDirectory(classesDir)) {
            return mains;
        }
        for (Path classFile : listClassFiles(classesDir)) {
            detectMainClassInFile(classFile)
                    .filter(name -> !GENERATED_HARNESS_CLASSES.contains(name))
                    .ifPresent(mains::add);
        }
        return mains;
    }

    /** Danh sách file {@code .class}, sắp xếp ổn định để kết quả không phụ thuộc thứ tự đọc thư mục. */
    public static List<Path> listClassFiles(Path classesDir) {
        List<Path> result = new ArrayList<>();
        try (var stream = Files.walk(classesDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".class"))
                    .forEach(result::add);
        } catch (IOException e) {
            log.warn("Failed to list class files under {}: {}", classesDir, e.getMessage());
        }
        result.sort((a, b) -> a.toString().compareTo(b.toString()));
        return result;
    }

    /**
     * Đọc một file {@code .class} và trả về tên lớp nếu nó có phương thức main hợp lệ.
     */
    public static Optional<String> detectMainClassInFile(Path classFile) {
        try (InputStream in = Files.newInputStream(classFile)) {
            return detectMainClass(new DataInputStream(in));
        } catch (IOException | RuntimeException e) {
            log.debug("Cannot parse class file {}: {}", classFile, e.getMessage());
            return Optional.empty();
        }
    }

    /** Đọc bytecode từ stream (dùng cho test với dữ liệu giả). */
    static Optional<String> detectMainClass(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != 0xCAFEBABE) {
            return Optional.empty();
        }
        in.readUnsignedShort(); // minor version
        in.readUnsignedShort(); // major version

        int constantPoolCount = in.readUnsignedShort();
        String[] utf8 = new String[constantPoolCount];
        int[] classNameIndex = new int[constantPoolCount];

        for (int i = 1; i < constantPoolCount; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case CONSTANT_UTF8 -> utf8[i] = in.readUTF();
                case CONSTANT_INTEGER, CONSTANT_FLOAT -> in.skipBytes(4);
                case CONSTANT_LONG, CONSTANT_DOUBLE -> {
                    in.skipBytes(8);
                    i++; // long/double chiếm 2 slot
                }
                case CONSTANT_CLASS -> classNameIndex[i] = in.readUnsignedShort();
                case CONSTANT_STRING -> in.skipBytes(2);
                // JVMS §4.4: CONSTANT_Fieldref / Methodref / InterfaceMethodref chỉ có
                // HAI u2 (class_index, name_and_type_index) — tổng cộng 4 byte.
                // Đọc ba u2 sẽ làm lệch offset của toàn bộ constant pool còn lại
                // ⇒ mọi class có lời gọi hàm đều hỏng parse.
                case CONSTANT_FIELDREF, CONSTANT_METHODREF, CONSTANT_INTERFACE_METHODREF -> in.skipBytes(4);
                case CONSTANT_NAME_AND_TYPE -> in.skipBytes(4);
                case CONSTANT_METHOD_HANDLE -> in.skipBytes(3);
                case CONSTANT_METHOD_TYPE -> in.skipBytes(2);
                case CONSTANT_DYNAMIC, CONSTANT_INVOKE_DYNAMIC -> in.skipBytes(4);
                default -> throw new IOException("Unsupported constant pool tag: " + tag);
            }
        }

        in.readUnsignedShort(); // access flags
        int thisClass = in.readUnsignedShort();
        in.readUnsignedShort(); // super class
        int interfaceCount = in.readUnsignedShort();
        in.skipBytes(interfaceCount * 2);

        int fieldCount = in.readUnsignedShort();
        for (int i = 0; i < fieldCount; i++) {
            in.readUnsignedShort(); // access flags
            in.readUnsignedShort(); // name index
            in.readUnsignedShort(); // descriptor index
            skipAttributes(in);
        }

        int methodCount = in.readUnsignedShort();
        boolean hasMain = false;
        for (int i = 0; i < methodCount; i++) {
            int accessFlags = in.readUnsignedShort();
            int nameIndex = in.readUnsignedShort();
            int descriptorIndex = in.readUnsignedShort();
            skipAttributes(in);
            String name = utf8[nameIndex];
            String descriptor = utf8[descriptorIndex];
            boolean isPublicStatic = (accessFlags & ACC_PUBLIC) != 0 && (accessFlags & ACC_STATIC) != 0;
            if ("main".equals(name) && MAIN_DESCRIPTOR.equals(descriptor) && isPublicStatic) {
                hasMain = true;
            }
        }

        if (!hasMain) {
            return Optional.empty();
        }
        return Optional.of(internalNameToClassName(utf8[classNameIndex[thisClass]]));
    }

    private static void skipAttributes(DataInputStream in) throws IOException {
        int attributeCount = in.readUnsignedShort();
        for (int i = 0; i < attributeCount; i++) {
            in.readUnsignedShort(); // name index
            int length = in.readInt();
            int skipped = 0;
            while (skipped < length) {
                long n = in.skipBytes(length - skipped);
                if (n <= 0) {
                    throw new IOException("Truncated class attribute");
                }
                skipped += (int) n;
            }
        }
    }

    /** {@code com/example/Circle} → {@code com.example.Circle}. */
    static String internalNameToClassName(String internalName) {
        if (internalName == null) {
            return null;
        }
        return internalName.replace('/', '.');
    }
}
