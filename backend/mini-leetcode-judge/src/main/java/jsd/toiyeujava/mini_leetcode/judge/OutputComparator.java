package jsd.toiyeujava.mini_leetcode.judge;

public final class OutputComparator {

    private OutputComparator() {
    }

    public static boolean matches(String actual, String expected) {
        return normalize(actual).equals(normalize(expected));
    }

    private static String normalize(String text) {
        String unified = text.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder sb = new StringBuilder();
        for (String line : unified.split("\n", -1)) {
            sb.append(line.stripTrailing()).append('\n');
        }
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == '\n') {
            end--;
        }
        return sb.substring(0, end);
    }
}