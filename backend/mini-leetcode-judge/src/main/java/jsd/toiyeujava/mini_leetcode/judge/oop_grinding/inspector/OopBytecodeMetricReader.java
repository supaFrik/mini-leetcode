package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.inspector;

import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopMainClassDetector;
import lombok.extern.slf4j.Slf4j;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Bytecode and Source inspector for strict OOP metrics:
 * 1. Method Line Count via bytecode LineNumberTable (with source fallback)
 * 2. CBO (Coupling Between Objects) via bytecode constant pool
 * 3. @Override annotation detection in Java source files
 */
@Slf4j
public final class OopBytecodeMetricReader {

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

    private OopBytecodeMetricReader() {
    }

    /**
     * Reads method line spans from the LineNumberTable attribute of a .class file.
     *
     * @param classFile path to the .class file
     * @return Map of methodName and methodName+descriptor to line count (maxLine - minLine + 1)
     */
    public static Map<String, Integer> getMethodLineCounts(Path classFile) {
        if (classFile == null || !Files.isRegularFile(classFile)) {
            return Collections.emptyMap();
        }
        try (InputStream in = Files.newInputStream(classFile)) {
            return parseMethodLineCounts(new DataInputStream(in));
        } catch (Exception e) {
            log.debug("Could not parse method line counts from {}: {}", classFile, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static Map<String, Integer> parseMethodLineCounts(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != 0xCAFEBABE) {
            return Collections.emptyMap();
        }
        in.readUnsignedShort(); // minor version
        in.readUnsignedShort(); // major version

        int constantPoolCount = in.readUnsignedShort();
        String[] utf8 = new String[constantPoolCount];

        for (int i = 1; i < constantPoolCount; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case CONSTANT_UTF8 -> utf8[i] = in.readUTF();
                case CONSTANT_INTEGER, CONSTANT_FLOAT -> skipFully(in, 4);
                case CONSTANT_LONG, CONSTANT_DOUBLE -> {
                    skipFully(in, 8);
                    i++;
                }
                case CONSTANT_CLASS, CONSTANT_STRING, CONSTANT_METHOD_TYPE -> skipFully(in, 2);
                case CONSTANT_FIELDREF, CONSTANT_METHODREF, CONSTANT_INTERFACE_METHODREF,
                        CONSTANT_NAME_AND_TYPE, CONSTANT_DYNAMIC, CONSTANT_INVOKE_DYNAMIC -> skipFully(in, 4);
                case CONSTANT_METHOD_HANDLE -> skipFully(in, 3);
                default -> throw new IOException("Unsupported constant pool tag: " + tag);
            }
        }

        skipFully(in, 6); // access_flags, this_class, super_class

        int interfacesCount = in.readUnsignedShort();
        skipFully(in, interfacesCount * 2);

        int fieldsCount = in.readUnsignedShort();
        for (int i = 0; i < fieldsCount; i++) {
            skipFully(in, 6); // access_flags, name_index, descriptor_index
            int attributesCount = in.readUnsignedShort();
            for (int a = 0; a < attributesCount; a++) {
                skipFully(in, 2); // attribute_name_index
                int length = in.readInt();
                skipFully(in, length);
            }
        }

        Map<String, Integer> lineCounts = new HashMap<>();
        int methodsCount = in.readUnsignedShort();
        for (int i = 0; i < methodsCount; i++) {
            skipFully(in, 2); // access_flags
            int nameIndex = in.readUnsignedShort();
            int descIndex = in.readUnsignedShort();
            String methodName = nameIndex > 0 && nameIndex < constantPoolCount ? utf8[nameIndex] : null;
            String methodDesc = descIndex > 0 && descIndex < constantPoolCount ? utf8[descIndex] : null;

            int attributesCount = in.readUnsignedShort();
            for (int a = 0; a < attributesCount; a++) {
                int attrNameIndex = in.readUnsignedShort();
                int attrLength = in.readInt();
                String attrName = attrNameIndex > 0 && attrNameIndex < constantPoolCount ? utf8[attrNameIndex] : null;

                if ("Code".equals(attrName)) {
                    skipFully(in, 4); // max_stack, max_locals
                    int codeLength = in.readInt();
                    skipFully(in, codeLength);

                    int exceptionTableLength = in.readUnsignedShort();
                    skipFully(in, exceptionTableLength * 8);

                    int codeAttributesCount = in.readUnsignedShort();
                    for (int ca = 0; ca < codeAttributesCount; ca++) {
                        int codeAttrNameIndex = in.readUnsignedShort();
                        int codeAttrLength = in.readInt();
                        String codeAttrName = codeAttrNameIndex > 0 && codeAttrNameIndex < constantPoolCount ? utf8[codeAttrNameIndex] : null;

                        if ("LineNumberTable".equals(codeAttrName)) {
                            int tableLength = in.readUnsignedShort();
                            int minLine = Integer.MAX_VALUE;
                            int maxLine = Integer.MIN_VALUE;
                            for (int entry = 0; entry < tableLength; entry++) {
                                skipFully(in, 2); // start_pc
                                int line = in.readUnsignedShort();
                                if (line < minLine) minLine = line;
                                if (line > maxLine) maxLine = line;
                            }
                            if (tableLength > 0 && methodName != null) {
                                int span = maxLine - minLine + 1;
                                lineCounts.put(methodName, span);
                                if (methodDesc != null) {
                                    lineCounts.put(methodName + methodDesc, span);
                                }
                            }
                        } else {
                            skipFully(in, codeAttrLength);
                        }
                    }
                } else {
                    skipFully(in, attrLength);
                }
            }
        }

        return lineCounts;
    }

    private static void skipFully(DataInputStream in, int bytes) throws IOException {
        int skipped = 0;
        while (skipped < bytes) {
            int n = in.skipBytes(bytes - skipped);
            if (n <= 0) {
                throw new IOException("Truncated class file attribute");
            }
            skipped += n;
        }
    }

    /**
     * Computes the JVM method descriptor for a Java reflection Method.
     */
    public static String getMethodDescriptor(Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> param : m.getParameterTypes()) {
            sb.append(getTypeDescriptor(param));
        }
        sb.append(")").append(getTypeDescriptor(m.getReturnType()));
        return sb.toString();
    }

    private static String getTypeDescriptor(Class<?> c) {
        if (c.isPrimitive()) {
            if (c == void.class) return "V";
            if (c == boolean.class) return "Z";
            if (c == byte.class) return "B";
            if (c == char.class) return "C";
            if (c == short.class) return "S";
            if (c == int.class) return "I";
            if (c == long.class) return "J";
            if (c == float.class) return "F";
            if (c == double.class) return "D";
        }
        if (c.isArray()) {
            return "[" + getTypeDescriptor(c.getComponentType());
        }
        return "L" + c.getName().replace('.', '/') + ";";
    }

    /**
     * Fallback to measure lines of a method from source code if LineNumberTable is absent.
     * Uses a brace-depth scanner to prevent premature truncation on nested braces.
     */
    public static int countMethodLinesFromSource(Path sourceFile, Method method) {
        if (sourceFile == null || !Files.isRegularFile(sourceFile) || method == null) {
            return 0;
        }
        try {
            String content = Files.readString(sourceFile);
            return countMethodLines(content, method);
        } catch (IOException e) {
            log.debug("Error reading source file {}: {}", sourceFile, e.getMessage());
            return 0;
        }
    }

    /**
     * Fallback to measure lines of a method by name from source code using a brace-depth scanner.
     */
    public static int countMethodLinesFromSource(Path sourceFile, String methodName) {
        if (sourceFile == null || !Files.isRegularFile(sourceFile) || methodName == null) {
            return 0;
        }
        try {
            String content = Files.readString(sourceFile);
            return countMethodLines(content, methodName);
        } catch (IOException e) {
            log.debug("Error reading source file {}: {}", sourceFile, e.getMessage());
            return 0;
        }
    }

    /**
     * Measures lines of a method declaration using a brace-depth scanner.
     */
    public static int countMethodLines(String content, Method method) {
        if (content == null || method == null) return 0;
        String masked = maskCommentsAndLiterals(content);
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(method.getName()) + "\\s*\\(");
        Matcher matcher = pattern.matcher(masked);
        while (matcher.find()) {
            int openParen = matcher.end() - 1;
            int closeParen = findMatchingParen(masked, openParen);
            if (closeParen == -1) continue;

            String paramText = masked.substring(openParen + 1, closeParen).trim();
            if (!matchesParameterTypes(paramText, method.getParameterTypes())) {
                continue;
            }

            int braceStart = findOpeningBrace(masked, closeParen);
            if (braceStart == -1) continue;

            int braceEnd = findClosingBrace(masked, braceStart);
            if (braceEnd != -1) {
                int startLine = countLinesBefore(content, braceStart) + 1;
                int endLine = countLinesBefore(content, braceEnd) + 1;
                return endLine - startLine + 1;
            }
        }
        return 0;
    }

    /**
     * Measures lines of a method by name using a brace-depth scanner.
     */
    public static int countMethodLines(String content, String methodName) {
        if (content == null || methodName == null) return 0;
        String masked = maskCommentsAndLiterals(content);
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(methodName) + "\\s*\\(");
        Matcher matcher = pattern.matcher(masked);
        while (matcher.find()) {
            int openParen = matcher.end() - 1;
            int closeParen = findMatchingParen(masked, openParen);
            if (closeParen == -1) continue;

            int braceStart = findOpeningBrace(masked, closeParen);
            if (braceStart == -1) continue;

            int braceEnd = findClosingBrace(masked, braceStart);
            if (braceEnd != -1) {
                int startLine = countLinesBefore(content, braceStart) + 1;
                int endLine = countLinesBefore(content, braceEnd) + 1;
                return endLine - startLine + 1;
            }
        }
        return 0;
    }

    /**
     * Finds the external classes coupled to the given class by inspecting constant pool references.
     * Excludes self, inner classes, standard JDK packages, and harness probes.
     */
    public static Set<String> getCoupledClasses(Path classFile, Class<?> clazz) {
        if (classFile == null || !Files.isRegularFile(classFile) || clazz == null) {
            return Collections.emptySet();
        }
        Set<String> referenced = OopMainClassDetector.getReferencedClasses(classFile);
        Set<String> coupled = new HashSet<>();
        String fqcn = clazz.getName();
        String simple = clazz.getSimpleName();

        for (String ref : referenced) {
            if (ref == null || ref.isBlank()) continue;
            if (ref.equals(fqcn) || ref.equals(simple)) continue;
            if (ref.startsWith(fqcn + "$") || ref.startsWith(simple + "$")) continue;
            if (ref.startsWith("java.") || ref.startsWith("javax.") || ref.startsWith("jdk.")
                    || ref.startsWith("sun.") || ref.startsWith("com.sun.")
                    || ref.startsWith("org.w3c.") || ref.startsWith("org.xml.")) {
                continue;
            }
            if (OopMainClassDetector.isHarnessClass(ref)) continue;
            coupled.add(ref);
        }
        return coupled;
    }

    /**
     * Checks if a method overrides a method in a superclass or interface.
     *
     * @return the Class<?> representing the superclass or interface declaring the overridden method, or null.
     */
    public static Class<?> findOverriddenSuperType(Class<?> clazz, Method method) {
        if (clazz == null || method == null) return null;
        if (Modifier.isStatic(method.getModifiers()) || Modifier.isPrivate(method.getModifiers())) {
            return null;
        }

        // 1. Check superclasses (ignoring Object)
        Class<?> curr = clazz.getSuperclass();
        while (curr != null && !curr.equals(Object.class)) {
            try {
                Method superMethod = curr.getDeclaredMethod(method.getName(), method.getParameterTypes());
                if (!Modifier.isPrivate(superMethod.getModifiers()) && !Modifier.isStatic(superMethod.getModifiers())) {
                    return curr;
                }
            } catch (NoSuchMethodException ignored) {
            }
            curr = curr.getSuperclass();
        }

        // 2. Check all interfaces
        Set<Class<?>> interfaces = getAllInterfaces(clazz);
        for (Class<?> iface : interfaces) {
            try {
                iface.getDeclaredMethod(method.getName(), method.getParameterTypes());
                return iface;
            } catch (NoSuchMethodException ignored) {
            }
        }

        return null;
    }

    private static Set<Class<?>> getAllInterfaces(Class<?> clazz) {
        Set<Class<?>> result = new LinkedHashSet<>();
        Class<?> curr = clazz;
        while (curr != null && !curr.equals(Object.class)) {
            for (Class<?> iface : curr.getInterfaces()) {
                result.add(iface);
                result.addAll(getAllInterfaces(iface));
            }
            curr = curr.getSuperclass();
        }
        return result;
    }

    /**
     * Checks if a specific method declaration in a .java source file carries the @Override annotation.
     * Evaluates overloads independently by matching parameter types.
     */
    public static boolean hasOverrideAnnotationInSource(Path sourceFile, Method method) {
        if (sourceFile == null || !Files.isRegularFile(sourceFile) || method == null) {
            return false;
        }
        try {
            String content = Files.readString(sourceFile);
            return hasOverrideAnnotation(content, method);
        } catch (IOException e) {
            log.debug("Error checking @Override annotation in {}: {}", sourceFile, e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a method declaration in a .java source file carries the @Override annotation (by method name).
     */
    public static boolean hasOverrideAnnotationInSource(Path sourceFile, String methodName) {
        if (sourceFile == null || !Files.isRegularFile(sourceFile) || methodName == null) {
            return false;
        }
        try {
            String content = Files.readString(sourceFile);
            return hasOverrideAnnotation(content, methodName);
        } catch (IOException e) {
            log.debug("Error checking @Override annotation in {}: {}", sourceFile, e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a specific method declaration carries @Override, matching its parameter types.
     */
    public static boolean hasOverrideAnnotation(String sourceCode, Method method) {
        if (sourceCode == null || method == null) return false;
        String masked = maskCommentsAndLiterals(sourceCode);
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(method.getName()) + "\\s*\\(");
        Matcher matcher = pattern.matcher(masked);
        Pattern overridePattern = Pattern.compile("@(?:java\\.lang\\.)?Override\\b");

        while (matcher.find()) {
            int openParen = matcher.end() - 1;
            int closeParen = findMatchingParen(masked, openParen);
            if (closeParen == -1) continue;

            String paramText = masked.substring(openParen + 1, closeParen).trim();
            if (!matchesParameterTypes(paramText, method.getParameterTypes())) {
                continue;
            }

            int delim = findDeclarationDelimiter(masked, closeParen);
            if (delim == -1) continue;

            int prevPos = findPreviousMemberBoundary(masked, matcher.start());
            String prefix = masked.substring(prevPos, matcher.start());
            return overridePattern.matcher(prefix).find();
        }
        return false;
    }

    /**
     * Regex check for @Override preceding the method name without intermediate method boundaries.
     */
    public static boolean hasOverrideAnnotation(String sourceCode, String methodName) {
        if (sourceCode == null || methodName == null) return false;
        String regex = "@(?:java\\.lang\\.)?Override\\s*(?:/\\*[\\s\\S]*?\\*/\\s*)*(?://.*\\n\\s*)*[^;{}]*\\b"
                + Pattern.quote(methodName) + "\\s*\\(";
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(sourceCode).find();
    }

    static String maskCommentsAndLiterals(String source) {
        if (source == null) return "";
        char[] chars = source.toCharArray();
        int n = chars.length;
        int i = 0;
        while (i < n) {
            char c = chars[i];
            if (c == '"') {
                chars[i] = ' ';
                i++;
                while (i < n) {
                    if (chars[i] == '\\' && i + 1 < n) {
                        chars[i] = ' ';
                        chars[i + 1] = ' ';
                        i += 2;
                    } else if (chars[i] == '"') {
                        chars[i] = ' ';
                        i++;
                        break;
                    } else {
                        if (chars[i] != '\n' && chars[i] != '\r') {
                            chars[i] = ' ';
                        }
                        i++;
                    }
                }
            } else if (c == '\'') {
                chars[i] = ' ';
                i++;
                while (i < n) {
                    if (chars[i] == '\\' && i + 1 < n) {
                        chars[i] = ' ';
                        chars[i + 1] = ' ';
                        i += 2;
                    } else if (chars[i] == '\'') {
                        chars[i] = ' ';
                        i++;
                        break;
                    } else {
                        if (chars[i] != '\n' && chars[i] != '\r') {
                            chars[i] = ' ';
                        }
                        i++;
                    }
                }
            } else if (c == '/' && i + 1 < n && chars[i + 1] == '/') {
                chars[i] = ' ';
                chars[i + 1] = ' ';
                i += 2;
                while (i < n && chars[i] != '\n' && chars[i] != '\r') {
                    chars[i] = ' ';
                    i++;
                }
            } else if (c == '/' && i + 1 < n && chars[i + 1] == '*') {
                chars[i] = ' ';
                chars[i + 1] = ' ';
                i += 2;
                while (i < n) {
                    if (chars[i] == '*' && i + 1 < n && chars[i + 1] == '/') {
                        chars[i] = ' ';
                        chars[i + 1] = ' ';
                        i += 2;
                        break;
                    } else {
                        if (chars[i] != '\n' && chars[i] != '\r') {
                            chars[i] = ' ';
                        }
                        i++;
                    }
                }
            } else {
                i++;
            }
        }
        return new String(chars);
    }

    static boolean matchesParameterTypes(String paramText, Class<?>[] expectedTypes) {
        if (expectedTypes == null || expectedTypes.length == 0) {
            return paramText.isEmpty();
        }
        if (paramText.isEmpty()) {
            return false;
        }

        List<String> rawParams = splitParameters(paramText);
        if (rawParams.size() != expectedTypes.length) {
            return false;
        }

        for (int i = 0; i < expectedTypes.length; i++) {
            Class<?> expected = expectedTypes[i];
            String raw = rawParams.get(i).trim();
            if (!paramMatches(raw, expected)) {
                return false;
            }
        }
        return true;
    }

    static List<String> splitParameters(String paramText) {
        List<String> result = new ArrayList<>();
        int depthAngle = 0;
        int depthParen = 0;
        int start = 0;
        for (int i = 0; i < paramText.length(); i++) {
            char c = paramText.charAt(i);
            if (c == '<') depthAngle++;
            else if (c == '>') {
                if (depthAngle > 0) depthAngle--;
            } else if (c == '(') depthParen++;
            else if (c == ')') {
                if (depthParen > 0) depthParen--;
            } else if (c == ',' && depthAngle == 0 && depthParen == 0) {
                result.add(paramText.substring(start, i));
                start = i + 1;
            }
        }
        if (start < paramText.length()) {
            result.add(paramText.substring(start));
        }
        return result;
    }

    static boolean paramMatches(String rawParam, Class<?> expectedType) {
        if (expectedType == null) return false;
        String cleaned = rawParam.replaceAll("@[a-zA-Z0-9_.]+(?:\\([^)]*\\))?", " ").trim();
        cleaned = cleaned.replaceAll("\\bfinal\\b", " ").trim();

        String typePart;
        int lastSpace = cleaned.lastIndexOf(' ');
        if (lastSpace != -1) {
            typePart = cleaned.substring(0, lastSpace).trim();
        } else if (cleaned.contains("...")) {
            typePart = cleaned.substring(0, cleaned.indexOf("...") + 3).trim();
        } else {
            typePart = cleaned;
        }

        if (expectedType.isPrimitive()) {
            return typePart.equals(expectedType.getName());
        }

        if (expectedType.isArray()) {
            Class<?> comp = expectedType.getComponentType();
            String compName = comp.getSimpleName();
            boolean isArrayInSource = typePart.endsWith("[]") || typePart.endsWith("...");
            if (!isArrayInSource) return false;
            String base = typePart.substring(0, typePart.length() - (typePart.endsWith("...") ? 3 : 2)).trim();
            if (comp.isPrimitive()) {
                return base.equals(comp.getName());
            }
            if (base.contains(".")) {
                base = base.substring(base.lastIndexOf('.') + 1);
            }
            return base.equals(compName);
        }

        String baseType = typePart.replaceAll("<.*>", "").trim();
        if (baseType.endsWith("[]") || baseType.endsWith("...")) {
            return false;
        }
        if (baseType.contains(".")) {
            baseType = baseType.substring(baseType.lastIndexOf('.') + 1);
        }
        return baseType.equals(expectedType.getSimpleName());
    }

    private static int findMatchingParen(String s, int openIndex) {
        int depth = 1;
        for (int i = openIndex + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static int findOpeningBrace(String masked, int closeParen) {
        for (int i = closeParen + 1; i < masked.length(); i++) {
            char c = masked.charAt(i);
            if (Character.isWhitespace(c)) continue;
            if (c == '{') return i;
            if (c == ';') return -1;
            if (masked.startsWith("throws", i)) {
                i += 5;
                continue;
            }
            if (c == '=' || c == '.' || c == ')') return -1;
        }
        return -1;
    }

    private static int findClosingBrace(String masked, int braceStart) {
        int depth = 1;
        for (int i = braceStart + 1; i < masked.length(); i++) {
            char c = masked.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static int findDeclarationDelimiter(String masked, int closeParen) {
        for (int i = closeParen + 1; i < masked.length(); i++) {
            char c = masked.charAt(i);
            if (Character.isWhitespace(c)) continue;
            if (c == '{' || c == ';') return i;
            if (masked.startsWith("throws", i)) {
                i += 5;
                continue;
            }
            if (c == '=' || c == '.' || c == ')') return -1;
        }
        return -1;
    }

    private static int findPreviousMemberBoundary(String masked, int beforePos) {
        for (int i = beforePos - 1; i >= 0; i--) {
            char c = masked.charAt(i);
            if (c == ';' || c == '}' || c == '{') {
                return i + 1;
            }
        }
        return 0;
    }

    private static int countLinesBefore(String content, int index) {
        int lines = 0;
        int limit = Math.min(index, content.length());
        for (int i = 0; i < limit; i++) {
            if (content.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    /**
     * Attempts to find the .java source file corresponding to a class in classesDir or parent workDir.
     */
    public static Path findSourceFile(Path classesDir, Class<?> clazz) {
        if (classesDir == null || clazz == null) return null;
        String simpleName = clazz.getSimpleName();
        // Remove inner class suffix if any
        if (simpleName.contains("$")) {
            simpleName = simpleName.substring(0, simpleName.indexOf('$'));
        }
        String targetFileName = simpleName + ".java";

        // 1. Search in classesDir
        Path found = searchFile(classesDir, targetFileName);
        if (found != null) return found;

        // 2. Search in parent directory (e.g. workDir containing both src and classes)
        if (classesDir.getParent() != null && Files.isDirectory(classesDir.getParent())) {
            found = searchFile(classesDir.getParent(), targetFileName);
            if (found != null) return found;
        }

        return null;
    }

    private static Path searchFile(Path dir, String fileName) {
        try (Stream<Path> stream = Files.walk(dir, 4)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
