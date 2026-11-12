package jsd.toiyeujava.minileetcode.plugin.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jsd.toiyeujava.minileetcode.plugin.model.PlanDetail;
import jsd.toiyeujava.minileetcode.plugin.model.PlanSummary;
import jsd.toiyeujava.minileetcode.plugin.model.ProblemDto;
import jsd.toiyeujava.minileetcode.plugin.model.SubmissionDto;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * HTTP Client giao tiếp giữa IntelliJ IDEA plugin và backend Mini LeetCode.
 * Sử dụng java.net.http.HttpClient (JDK 21) và Gson được đóng gói sẵn trong IntelliJ Platform.
 */
public class ApiClient {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final String USER_AGENT = "MiniLeetCode-IntelliJ-Plugin/0.1.0";
    private static final Type PLAN_SUMMARY_LIST_TYPE = new TypeToken<List<PlanSummary>>() {}.getType();

    private final String baseUrl;
    private final String pat;
    private final HttpClient httpClient;
    private final Gson gson;
    private final Duration requestTimeout;

    public ApiClient(String baseUrl) {
        this(baseUrl, null, DEFAULT_TIMEOUT);
    }

    public ApiClient(String baseUrl, String pat) {
        this(baseUrl, pat, DEFAULT_TIMEOUT);
    }

    public ApiClient(String baseUrl, String pat, Duration timeout) {
        this(baseUrl, pat, null, null, timeout != null ? timeout : DEFAULT_TIMEOUT);
    }

    public ApiClient(String baseUrl, String pat, HttpClient httpClient, Gson gson, Duration requestTimeout) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.pat = (pat != null && !pat.isBlank()) ? pat.trim() : null;
        this.requestTimeout = (requestTimeout != null) ? requestTimeout : DEFAULT_TIMEOUT;
        this.httpClient = (httpClient != null) ? httpClient : createDefaultHttpClient(this.requestTimeout);
        this.gson = (gson != null) ? gson : new Gson();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getPat() {
        return pat;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * Lấy danh sách tóm tắt tất cả Study Plans đã xuất bản (GET /api/v1/study-plans).
     */
    public List<PlanSummary> getStudyPlans() {
        URI uri = URI.create(baseUrl + "/api/v1/study-plans");
        HttpRequest request = createRequestBuilder(uri).GET().build();
        HttpResponse<String> response = send(request);

        if (response.statusCode() == 200) {
            String body = response.body();
            if (body == null || body.isBlank()) {
                return List.of();
            }
            List<PlanSummary> list = gson.fromJson(body, PLAN_SUMMARY_LIST_TYPE);
            return list != null ? list : List.of();
        }

        handleErrorResponse(response);
        return List.of(); // Unreachable, handleErrorResponse always throws
    }

    /**
     * Lấy chi tiết toàn bộ phân cấp một Study Plan (GET /api/v1/study-plans/{slug}).
     */
    public PlanDetail getStudyPlan(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("slug must not be null or blank");
        }

        String encodedSlug = URLEncoder.encode(slug.trim(), StandardCharsets.UTF_8).replace("+", "%20");
        URI uri = URI.create(baseUrl + "/api/v1/study-plans/" + encodedSlug);
        HttpRequest request = createRequestBuilder(uri).GET().build();
        HttpResponse<String> response = send(request);

        if (response.statusCode() == 200) {
            return gson.fromJson(response.body(), PlanDetail.class);
        }

        handleErrorResponse(response);
        return null; // Unreachable, handleErrorResponse always throws
    }

    /**
     * Kiểm tra kết nối và tính hợp lệ của token (dùng cho nút Test connection trong Settings - PL-1).
     * Trả về true nếu gọi GET /api/v1/study-plans thành công (HTTP 200).
     */
    public boolean testConnection() {
        getStudyPlans();
        return true;
    }

    /**
     * Tải starter template archive dạng byte array (GET /api/v1/problems/{idOrSlug}/starter-template).
     * Header: Accept: application/zip, Authorization: Bearer <pat> (nếu có PAT).
     *
     * @param idOrSlug ID hoặc slug của bài toán
     * @return raw byte[] của starter archive
     * @throws AuthenticationException nếu HTTP 401 Unauthorized
     * @throws MiniLeetCodeApiException nếu lỗi HTTP khác hoặc lỗi mạng/timeout
     * @throws IllegalArgumentException nếu idOrSlug null hoặc blank
     */
    public byte[] getStarterTemplate(String idOrSlug) {
        if (idOrSlug == null || idOrSlug.isBlank()) {
            throw new IllegalArgumentException("idOrSlug must not be null or blank");
        }

        String encoded = URLEncoder.encode(idOrSlug.trim(), StandardCharsets.UTF_8).replace("+", "%20");
        URI uri = URI.create(baseUrl + "/api/v1/problems/" + encoded + "/starter-template");
        HttpRequest request = createRequestBuilder(uri, "application/zip").GET().build();
        HttpResponse<byte[]> response = sendBytes(request);

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            byte[] body = response.body();
            return body != null ? body : new byte[0];
        }

        handleBytesErrorResponse(response);
        return new byte[0]; // Unreachable, handleBytesErrorResponse always throws
    }

    /**
     * Lấy thông tin chi tiết bài toán theo ID hoặc slug (GET /api/v1/problems/{idOrSlug}).
     * Header: Accept: application/json, Authorization: Bearer <pat> (nếu có PAT).
     *
     * @param idOrSlug ID hoặc slug của bài toán
     * @return đối tượng ProblemDto đã deserialized từ JSON
     * @throws AuthenticationException nếu HTTP 401 Unauthorized
     * @throws MiniLeetCodeApiException nếu lỗi HTTP khác hoặc lỗi mạng/timeout
     * @throws IllegalArgumentException nếu idOrSlug null hoặc blank
     */
    public ProblemDto getProblem(String idOrSlug) {
        if (idOrSlug == null || idOrSlug.isBlank()) {
            throw new IllegalArgumentException("idOrSlug must not be null or blank");
        }

        String encoded = URLEncoder.encode(idOrSlug.trim(), StandardCharsets.UTF_8).replace("+", "%20");
        URI uri = URI.create(baseUrl + "/api/v1/problems/" + encoded);
        HttpRequest request = createRequestBuilder(uri, "application/json").GET().build();
        HttpResponse<String> response = send(request);

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            String body = response.body();
            if (body == null || body.isBlank()) {
                return null;
            }
            return gson.fromJson(body, ProblemDto.class);
        }

        handleErrorResponse(response);
        return null; // Unreachable, handleErrorResponse always throws
    }

    /**
     * Nộp chính thức một stage (POST /api/v1/problems/{id}/submit-multipart, field {@code file} = ZIP,
     * {@code language} = JAVA). Backend trả 201 với {@code ApiResponse{data: SubmissionResponse}}.
     */
    public SubmissionDto submitStage(long problemId, byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalArgumentException("zipBytes must not be empty");
        }
        String boundary = "----MiniLeetCodeBoundary" + java.util.UUID.randomUUID().toString().replace("-", "");
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        byte[] crlf = "\r\n".getBytes(StandardCharsets.UTF_8);
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"language\"\r\n\r\nJAVA\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"submission.zip\"\r\n"
                + "Content-Type: application/zip\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(zipBytes);
        body.writeBytes(crlf);
        body.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        URI uri = URI.create(baseUrl + "/api/v1/problems/" + problemId + "/submit-multipart");
        HttpRequest request = createRequestBuilder(uri)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return unwrapData(response.body(), SubmissionDto.class);
        }
        handleErrorResponse(response);
        return null; // Unreachable
    }

    /** Kết quả bài nộp (GET /api/v1/submissions/{id}); backend bọc trong {@code ApiResponse{data: ...}}. */
    public SubmissionDto getSubmission(long submissionId) {
        URI uri = URI.create(baseUrl + "/api/v1/submissions/" + submissionId);
        HttpResponse<String> response = send(createRequestBuilder(uri).GET().build());
        if (response.statusCode() == 200) {
            return unwrapData(response.body(), SubmissionDto.class);
        }
        handleErrorResponse(response);
        return null; // Unreachable
    }

    private <T> T unwrapData(String body, Class<T> type) {
        if (body == null || body.isBlank()) {
            throw new MiniLeetCodeApiException("Empty response body from server");
        }
        com.google.gson.JsonElement root = com.google.gson.JsonParser.parseString(body);
        if (root.isJsonObject() && root.getAsJsonObject().has("data")) {
            root = root.getAsJsonObject().get("data");
        }
        return gson.fromJson(root, type);
    }

    private HttpRequest.Builder createRequestBuilder(URI uri) {
        return createRequestBuilder(uri, "application/json");
    }

    private HttpRequest.Builder createRequestBuilder(URI uri, String acceptHeader) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(requestTimeout)
                .header("Accept", acceptHeader != null ? acceptHeader : "application/json")
                .header("User-Agent", USER_AGENT);

        if (pat != null) {
            builder.header("Authorization", "Bearer " + pat);
        }

        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new MiniLeetCodeApiException("Connection timed out: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new MiniLeetCodeApiException("Network error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MiniLeetCodeApiException("Request was interrupted", e);
        }
    }

    private HttpResponse<byte[]> sendBytes(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException e) {
            throw new MiniLeetCodeApiException("Connection timed out: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new MiniLeetCodeApiException("Network error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MiniLeetCodeApiException("Request was interrupted", e);
        }
    }

    private void handleErrorResponse(HttpResponse<String> response) {
        handleErrorResponse(response.statusCode(), response.body());
    }

    private void handleBytesErrorResponse(HttpResponse<byte[]> response) {
        byte[] bytes = response.body();
        String body = (bytes != null && bytes.length > 0) ? new String(bytes, StandardCharsets.UTF_8) : null;
        handleErrorResponse(response.statusCode(), body);
    }

    private void handleErrorResponse(int status, String body) {
        ProblemDetail problem = parseProblemDetail(body);

        if (status == 401) {
            String code = (problem != null && problem.code() != null) ? problem.code() : "UNAUTHORIZED";
            String message = (problem != null && problem.detail() != null && !problem.detail().isBlank())
                    ? problem.detail()
                    : "Personal access token is invalid, expired, or revoked (HTTP 401)";
            throw new AuthenticationException(message, code, problem);
        }

        String code = (problem != null && problem.code() != null) ? problem.code() : null;
        String message;
        if (problem != null && problem.detail() != null && !problem.detail().isBlank()) {
            message = problem.detail();
        } else if (problem != null && problem.title() != null && !problem.title().isBlank()) {
            message = problem.title() + " (HTTP " + status + ")";
        } else {
            message = "API request failed with HTTP " + status;
        }

        throw new MiniLeetCodeApiException(status, message, code, problem, null);
    }

    private ProblemDetail parseProblemDetail(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return gson.fromJson(body, ProblemDetail.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be null or blank");
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static HttpClient createDefaultHttpClient(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout != null ? connectTimeout : DEFAULT_TIMEOUT)
                // Backend không redirect; không theo redirect để header Authorization (PAT) không bị gửi sang host khác.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
