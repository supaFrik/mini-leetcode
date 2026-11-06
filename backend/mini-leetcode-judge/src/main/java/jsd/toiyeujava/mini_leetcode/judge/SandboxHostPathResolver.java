package jsd.toiyeujava.mini_leetcode.judge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Xác định đường dẫn trên HOST để bind-mount vào {@code /workspace} của sandbox.
 *
 * <p>Đường dẫn trong {@code docker run -v} do Docker daemon trên HOST phân giải,
 * nên nó phải là đường dẫn nhìn thấy được từ HOST — không phải đường dẫn bên
 * trong container của judge worker.
 *
 * <p>Cách phát hiện đáng tin cậy nhất là hỏi chính Docker daemon qua
 * {@code docker inspect} về các mount của container đang chạy, vì trường
 * "source" của {@code /proc/self/mountinfo} trên Docker Desktop bị biến đổi
 * thành dạng 9p khó hiểu.
 */
@Slf4j
@Component
public class SandboxHostPathResolver {

    /** Thư mục chứa file nộp bài tạm; giống với TEMP_ROOT của JudgeServiceImpl. */
    public static final String TEMP_ROOT_NAME = "temp-submissions";

    /** Thư mục chứa workspace của các phiên console OOP. */
    public static final String CONSOLE_ROOT_NAME = "temp-console";

    /** Các thư mục gốc cần bind-mount ra HOST. */
    private static final List<String> KNOWN_ROOTS = List.of(TEMP_ROOT_NAME, CONSOLE_ROOT_NAME);

    /** Cache đường dẫn HOST theo từng thư mục gốc. */
    private final Map<String, String> cachedHostBaseDirs = new ConcurrentHashMap<>();

    /**
     * Đường dẫn HOST của thư mục làm việc (không có dấu {@code /} cuối).
     *
     * @return đường dẫn HOST, hoặc {@code null} nếu không phát hiện được
     */
    public String resolveHostBaseDir() {
        return resolveHostBaseDir(TEMP_ROOT_NAME);
    }

    /**
     * Đường dẫn HOST của một thư mục gốc cụ thể (VD {@code temp-console}).
     *
     * @return đường dẫn HOST, hoặc {@code null} nếu không phát hiện được
     */
    public String resolveHostBaseDir(String rootName) {
        String root = (rootName == null || rootName.isBlank()) ? TEMP_ROOT_NAME : rootName.trim();
        String cached = cachedHostBaseDirs.get(root);
        if (cached != null) {
            return cached;
        }
        String detected = detectHostBaseDirFromDaemon(root);
        if (detected == null) {
            detected = detectHostBaseDirFromEnv(root);
        }
        if (detected != null) {
            cachedHostBaseDirs.put(root, detected);
            log.info("Resolved sandbox bind-mount host base dir for {}: {}", root, detected);
        } else {
            log.warn("Could not detect host base dir for {}; "
                            + "sandbox containers may not see the source files. "
                            + "Set HOST_WORK_DIR explicitly to the host path of the work directory.",
                    root);
        }
        return detected;
    }

    /**
     * Đường dẫn HOST cụ thể của một thư mục làm việc.
     *
     * @return đường dẫn dùng cho {@code -v}, dấu {@code /} cuối đã bị bỏ
     */
    public String resolveHostPath(Path workDir) {
        if (workDir == null) {
            return null;
        }
        Path normalized = workDir.toAbsolutePath().normalize();
        String rootName = findKnownRoot(normalized);
        if (rootName != null) {
            String hostBase = resolveHostBaseDir(rootName);
            if (hostBase != null) {
                return (hostBase + "/" + normalized.getFileName()).replace('\\', '/');
            }
        }
        // Fallback: chạy ngoài Docker, dùng đường dẫn tuyệt đối trong container.
        return normalized.toString().replace('\\', '/');
    }

    /**
     * Tìm thư mục gốc đã biết chứa {@code workDir} (VD {@code temp-console}).
     */
    private String findKnownRoot(Path workDir) {
        for (Path p = workDir; p != null; p = p.getParent()) {
            Path fileName = p.getFileName();
            if (fileName == null) {
                continue;
            }
            String name = fileName.toString();
            if (KNOWN_ROOTS.contains(name)) {
                return name;
            }
        }
        return null;
    }

    private String detectHostBaseDirFromDaemon(String rootName) {
        try {
            String selfId = readSelfContainerId();
            if (selfId == null) {
                return null;
            }
            List<String> command = new ArrayList<>(List.of(
                    "docker", "inspect",
                    "--format", "{{range .Mounts}}{{.Destination}}|{{.Source}}{{println}}{{end}}",
                    selfId));
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            p.waitFor();
            for (String line : output.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int separator = trimmed.indexOf('|');
                if (separator < 0) {
                    continue;
                }
                String destination = trimmed.substring(0, separator);
                String source = trimmed.substring(separator + 1).trim();
                if (destination.endsWith("/" + rootName) && !source.isEmpty()) {
                    return stripTrailingSeparators(source);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to detect host base dir via docker inspect: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while detecting host base dir via docker inspect");
        }
        return null;
    }

    private String readSelfContainerId() throws IOException {
        Path hostname = Path.of("/etc/hostname");
        if (Files.isReadable(hostname)) {
            String value = Files.readString(hostname).trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private String detectHostBaseDirFromEnv(String rootName) {
        String hostWorkDir = System.getenv("HOST_WORK_DIR");
        if (hostWorkDir == null || hostWorkDir.isBlank()) {
            return null;
        }
        if (!TEMP_ROOT_NAME.equals(rootName) && !CONSOLE_ROOT_NAME.equals(rootName)) {
            return null;
        }
        // HOST_WORK_DIR trỏ tới thư mục gốc được mount (temp-submissions).
        // Thư mục console dùng chung cùng cấp cha trên HOST.
        Path base = Path.of(hostWorkDir).toAbsolutePath().normalize();
        if (TEMP_ROOT_NAME.equals(rootName)) {
            return stripTrailingSeparators(base.toString());
        }
        Path parent = base.getParent();
        if (parent == null) {
            return null;
        }
        return stripTrailingSeparators(parent.resolve(CONSOLE_ROOT_NAME).toString());
    }

    private String stripTrailingSeparators(String path) {
        return path.replaceAll("[/\\\\]+$", "");
    }
}
