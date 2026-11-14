package jsd.toiyeujava.minileetcode.plugin.client;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hồi quy: server Next.js (proxy /api) đóng kết nối khi request có "Upgrade: h2c" — Java HttpClient mặc định gửi
 * header này trên http://, plugin báo "header parser received no bytes". Client phải nói HTTP/1.1 thuần.
 */
class ApiClientHttp11Test {

    @Test
    void talksPlainHttp11SoProxiesThatRejectH2cUpgradeStillAnswer() throws Exception {
        List<String> headers = new ArrayList<>();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread t = new Thread(() -> {
                try (Socket s = server.accept()) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
                    for (String line; (line = in.readLine()) != null && !line.isEmpty(); ) headers.add(line);
                    boolean upgrade = headers.stream().anyMatch(h -> h.toLowerCase().startsWith("upgrade:"));
                    if (upgrade) return; // như Next.js: đóng mà không trả byte nào
                    byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
                    OutputStream out = s.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + body.length
                            + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    out.write(body);
                    out.flush();
                } catch (Exception ignored) {
                }
            });
            t.start();

            ApiClient client = new ApiClient("http://127.0.0.1:" + server.getLocalPort(), "mlc_pat_test");
            assertTrue(client.getStudyPlans().isEmpty());
            t.join(5000);
        }
        assertTrue(headers.get(0).endsWith("HTTP/1.1"), headers.get(0));
        assertFalse(headers.stream().anyMatch(h -> h.toLowerCase().startsWith("upgrade:")), headers.toString());
    }
}
