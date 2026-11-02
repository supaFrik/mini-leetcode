package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.inspector;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ModifierHelper {

    private ModifierHelper() {}

    public static int parseModifier(String name) {
        if (name == null) return 0;
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "public" -> Modifier.PUBLIC;
            case "private" -> Modifier.PRIVATE;
            case "protected" -> Modifier.PROTECTED;
            case "static" -> Modifier.STATIC;
            case "final" -> Modifier.FINAL;
            case "synchronized" -> Modifier.SYNCHRONIZED;
            case "volatile" -> Modifier.VOLATILE;
            case "transient" -> Modifier.TRANSIENT;
            case "native" -> Modifier.NATIVE;
            case "interface" -> Modifier.INTERFACE;
            case "abstract" -> Modifier.ABSTRACT;
            case "strictfp" -> Modifier.STRICT;
            default -> 0;
        };
    }

    public static List<String> checkModifiers(
            int actualModifiers,
            List<String> requiredModifiers,
            List<String> forbiddenModifiers,
            String memberDesc) {
        List<String> violations = new ArrayList<>();
        if (requiredModifiers != null) {
            for (String req : requiredModifiers) {
                if (req == null || req.isBlank()) continue;
                int mask = parseModifier(req);
                if (mask != 0 && (actualModifiers & mask) == 0) {
                    violations.add(memberDesc + " missing required modifier [" + req.toUpperCase(Locale.ROOT) + "]");
                }
            }
        }
        if (forbiddenModifiers != null) {
            for (String forb : forbiddenModifiers) {
                if (forb == null || forb.isBlank()) continue;
                int mask = parseModifier(forb);
                if (mask != 0 && (actualModifiers & mask) != 0) {
                    violations.add(memberDesc + " has forbidden modifier [" + forb.toUpperCase(Locale.ROOT) + "]");
                }
            }
        }
        return violations;
    }

    public static boolean matchesType(Class<?> actualType, String expectedTypeName) {
        if (expectedTypeName == null || expectedTypeName.isBlank() || "*".equals(expectedTypeName.trim())) {
            return true;
        }
        String exp = expectedTypeName.trim();
        String actualCanonical = actualType.getCanonicalName();
        String actualSimple = actualType.getSimpleName();
        String actualName = actualType.getName();

        if (exp.equalsIgnoreCase(actualCanonical)
                || exp.equalsIgnoreCase(actualSimple)
                || exp.equalsIgnoreCase(actualName)) {
            return true;
        }

        // Common primitive mappings
        if (isPrimitiveMatch(actualType, exp)) {
            return true;
        }

        // Package matching: if expected is "com.foo.Shape" and actual is simple "Shape" or vice versa
        if (exp.endsWith("." + actualSimple)) {
            return true;
        }
        if (actualCanonical != null && actualCanonical.endsWith("." + exp)) {
            return true;
        }

        return false;
    }

    private static boolean isPrimitiveMatch(Class<?> actualType, String exp) {
        String lower = exp.toLowerCase(Locale.ROOT);
        if (actualType == int.class && (lower.equals("int") || lower.equals("integer") || lower.equals("java.lang.integer"))) return true;
        if (actualType == double.class && (lower.equals("double") || lower.equals("java.lang.double"))) return true;
        if (actualType == float.class && (lower.equals("float") || lower.equals("java.lang.float"))) return true;
        if (actualType == long.class && (lower.equals("long") || lower.equals("java.lang.long"))) return true;
        if (actualType == short.class && (lower.equals("short") || lower.equals("java.lang.short"))) return true;
        if (actualType == byte.class && (lower.equals("byte") || lower.equals("java.lang.byte"))) return true;
        if (actualType == boolean.class && (lower.equals("boolean") || lower.equals("java.lang.boolean"))) return true;
        if (actualType == char.class && (lower.equals("char") || lower.equals("character") || lower.equals("java.lang.character"))) return true;
        if (actualType == void.class && (lower.equals("void") || lower.equals("java.lang.void"))) return true;
        return false;
    }
}
