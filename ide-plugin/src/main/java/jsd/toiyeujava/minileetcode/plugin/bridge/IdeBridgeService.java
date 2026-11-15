package jsd.toiyeujava.minileetcode.plugin.bridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import jsd.toiyeujava.minileetcode.plugin.actions.NotificationPresenter;
import jsd.toiyeujava.minileetcode.plugin.actions.OpenStageAction;
import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.model.PlanDetail;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodePasswordSafe;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeSettingsState;
import jsd.toiyeujava.minileetcode.plugin.ui.ItemTreeNode;
import jsd.toiyeujava.minileetcode.plugin.ui.PlanTreeNode;
import jsd.toiyeujava.minileetcode.plugin.ui.StageTreeNode;
import jsd.toiyeujava.minileetcode.plugin.ui.StudyPlanTreeModel;
import jsd.toiyeujava.minileetcode.plugin.workspace.NewWindowProjectOpener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.RestService;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Cầu nối "Open in IntelliJ" (specs/practice-ide-rich-editor.spec.md §4) trên built-in server của IDE:
 * {@code http://localhost:63342/api/mini-leetcode?action=ping|connect|open}.
 *
 * <p>Bảo mật: IntelliJ tự hỏi người dùng với origin không phải localhost ({@link #isHostTrusted}). Thêm vào đó,
 * một trang chỉ được kết nối plugin tới <b>chính site của nó</b> (serverUrl phải trùng Origin) và chỉ site đã
 * kết nối mới mở được stage, nên trang lạ không thể trỏ plugin sang server khác.</p>
 */
public final class IdeBridgeService extends RestService {

    static final String PLUGIN_ID = "jsd.toiyeujava.minileetcode.plugin";
    private static final Pattern TOKEN = Pattern.compile("^mlc_pat_[A-Za-z0-9_-]{20,200}$");

    @Override
    protected @NotNull String getServiceName() {
        return "mini-leetcode";
    }

    @Override
    protected boolean isMethodSupported(@NotNull HttpMethod method) {
        return method == HttpMethod.GET || method == HttpMethod.POST;
    }

    @Override
    public @Nullable String execute(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request,
                                    @NotNull ChannelHandlerContext context) {
        String origin = origin(request);
        String action = getStringParameter("action", urlDecoder);
        JsonObject out = new JsonObject();
        HttpResponseStatus status = HttpResponseStatus.OK;
        try {
            switch (action == null ? "ping" : action) {
                case "ping" -> {
                    out.addProperty("version", pluginVersion());
                    out.addProperty("connected", isConnectedTo(origin));
                }
                case "connect" -> {
                    JsonObject body = body(request);
                    String serverUrl = trimSlash(str(body, "serverUrl"));
                    String token = str(body, "token");
                    if (origin == null || !origin.equals(originOf(serverUrl))) {
                        return reply(request, context, origin, HttpResponseStatus.FORBIDDEN, error("serverUrl must be the site that connects"));
                    }
                    if (!TOKEN.matcher(token).matches()) {
                        return reply(request, context, origin, HttpResponseStatus.BAD_REQUEST, error("Invalid personal access token"));
                    }
                    MiniLeetCodeSettingsState.getInstance().setServerUrl(serverUrl);
                    MiniLeetCodePasswordSafe.getInstance().setPassword(token);
                    notifyUser("Connected to " + serverUrl + ". Open the Mini LeetCode tool window and click Refresh.",
                            NotificationType.INFORMATION);
                    out.addProperty("connected", true);
                }
                case "open" -> {
                    if (!isConnectedTo(origin)) {
                        return reply(request, context, origin, HttpResponseStatus.CONFLICT, error("Not connected to this site"));
                    }
                    JsonObject body = body(request);
                    String planSlug = str(body, "planSlug");
                    long itemId = body.has("itemId") ? body.get("itemId").getAsLong() : -1;
                    int stageNo = body.has("stageNo") ? body.get("stageNo").getAsInt() : 1;
                    if (planSlug.isBlank()) {
                        return reply(request, context, origin, HttpResponseStatus.BAD_REQUEST, error("planSlug is required"));
                    }
                    ApplicationManager.getApplication().executeOnPooledThread(() -> openStage(planSlug, itemId, stageNo));
                    status = HttpResponseStatus.ACCEPTED;
                    out.addProperty("accepted", true);
                }
                default -> {
                    return reply(request, context, origin, HttpResponseStatus.BAD_REQUEST, error("Unknown action: " + action));
                }
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            return reply(request, context, origin, HttpResponseStatus.BAD_REQUEST, error(e.getMessage()));
        }
        return reply(request, context, origin, status, out);
    }

    /** Tải plan, dựng cây như tool window rồi giao cho {@link OpenStageAction} (kiểm tra khoá, chọn thư mục, mở project). */
    private void openStage(String planSlug, long itemId, int stageNo) {
        try {
            MiniLeetCodeSettingsState settings = MiniLeetCodeSettingsState.getInstance();
            ApiClient client = new ApiClient(settings.getNormalizedServerUrl(), MiniLeetCodePasswordSafe.getInstance().getPassword());
            PlanDetail plan = client.getStudyPlan(planSlug);
            PlanTreeNode planNode = StudyPlanTreeModel.buildPlanNode(plan);
            ItemTreeNode item = itemId >= 0 ? planNode.findItemById(itemId) : null;
            StageTreeNode stage = item != null ? item.findStageByNo(stageNo) : null;
            if (stage == null) {
                notifyUser("Stage " + stageNo + " was not found in " + planSlug + ". Refresh and try again.", NotificationType.WARNING);
                return;
            }
            // Mỗi project một thư mục cố định ⇒ không hỏi thư mục; mở lại chỉ thêm file của stage mới, không ghi đè code.
            Path dir = projectDirectory(planSlug, item);
            OpenStageAction action = new OpenStageAction(null, (project, suggested) -> dir, null,
                    new NewWindowProjectOpener(), null, null);
            ApplicationManager.getApplication().invokeLater(() -> action.openSelectedNode(null, stage));
        } catch (RuntimeException e) {
            notifyUser("Could not open the stage: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()),
                    NotificationType.ERROR);
        }
    }

    /** {@code ~/MiniLeetCode/<plan>/<project>}: mỗi project của plan một thư mục, các stage nối tiếp trong đó. */
    static Path projectDirectory(String planSlug, ItemTreeNode item) {
        String folder = slug(item.getTitle());
        return Path.of(System.getProperty("user.home"), "MiniLeetCode", slug(planSlug),
                folder.isEmpty() ? "item-" + item.getId() : folder);
    }

    static String slug(@Nullable String s) {
        String ascii = java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D');
        return ascii.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    private static boolean isConnectedTo(@Nullable String origin) {
        if (origin == null || !MiniLeetCodePasswordSafe.getInstance().hasPassword()) return false;
        return origin.equals(originOf(MiniLeetCodeSettingsState.getInstance().getNormalizedServerUrl()));
    }

    private static @Nullable String reply(FullHttpRequest request, ChannelHandlerContext context, @Nullable String origin,
                                          HttpResponseStatus status, JsonObject json) {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
        if (origin != null) {
            // Trang web đọc được phản hồi (request "simple": GET hoặc POST text/plain, không preflight).
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            response.headers().set(HttpHeaderNames.VARY, "Origin");
        }
        sendResponse(request, context, response);
        return null;
    }

    private static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return o;
    }

    private static JsonObject body(FullHttpRequest request) {
        String text = request.content().toString(StandardCharsets.UTF_8);
        if (text.isBlank()) throw new IllegalArgumentException("Request body is required");
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString().trim() : "";
    }

    private static @Nullable String origin(FullHttpRequest request) {
        String o = request.headers().get(HttpHeaderNames.ORIGIN);
        return o == null || o.isBlank() || "null".equals(o) ? null : trimSlash(o);
    }

    /** {@code scheme://host[:port]} của một URL http(s); URL không hợp lệ ⇒ null. */
    static @Nullable String originOf(@Nullable String url) {
        try {
            URI uri = URI.create(url == null ? "" : url.trim());
            String scheme = uri.getScheme();
            if (uri.getHost() == null || !("http".equals(scheme) || "https".equals(scheme))) return null;
            return scheme + "://" + uri.getHost() + (uri.getPort() >= 0 ? ":" + uri.getPort() : "");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String trimSlash(String s) {
        String r = s == null ? "" : s.trim();
        while (r.endsWith("/")) r = r.substring(0, r.length() - 1);
        return r;
    }

    private static String pluginVersion() {
        IdeaPluginDescriptor d = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
        return d != null ? d.getVersion() : "unknown";
    }

    private static void notifyUser(String message, NotificationType type) {
        ApplicationManager.getApplication().invokeLater(() ->
                NotificationPresenter.DEFAULT.showNotification(getLastFocusedOrOpenedProject(), "Mini LeetCode", message, type));
    }
}
