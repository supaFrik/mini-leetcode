package jsd.toiyeujava.minileetcode.plugin.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Quy tắc "một trang chỉ kết nối plugin tới chính site của nó" dựa trên so khớp origin. */
class IdeBridgeServiceTest {

    @Test
    void originKeepsSchemeHostAndPort() {
        assertEquals("http://localhost:3100", IdeBridgeService.originOf("http://localhost:3100/"));
        assertEquals("http://localhost:3100", IdeBridgeService.originOf("http://localhost:3100/api/v1"));
        assertEquals("https://leetcode.example.com", IdeBridgeService.originOf("https://leetcode.example.com"));
    }

    @Test
    void differentPortOrHostIsADifferentSite() {
        assertEquals("http://localhost:8090", IdeBridgeService.originOf("http://localhost:8090"));
        assertEquals("http://127.0.0.1:3100", IdeBridgeService.originOf("http://127.0.0.1:3100"));
    }

    @Test
    void projectFolderNamesAreReadableAscii() {
        assertEquals("project-mini-bank-console", IdeBridgeService.slug("Project: Mini Bank Console"));
        assertEquals("oop-voi-java", IdeBridgeService.slug("OOP với Java"));
        assertEquals("dong-goi", IdeBridgeService.slug("Đóng gói"));
        assertEquals("", IdeBridgeService.slug("  ::  "));
    }

    @Test
    void rejectsNonHttpOrMalformedUrls() {
        assertNull(IdeBridgeService.originOf("file:///etc/passwd"));
        assertNull(IdeBridgeService.originOf("javascript:alert(1)"));
        assertNull(IdeBridgeService.originOf("not a url"));
        assertNull(IdeBridgeService.originOf(""));
        assertNull(IdeBridgeService.originOf(null));
    }
}
