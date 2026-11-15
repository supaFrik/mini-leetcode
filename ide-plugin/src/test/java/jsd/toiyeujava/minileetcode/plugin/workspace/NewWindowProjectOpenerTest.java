package jsd.toiyeujava.minileetcode.plugin.workspace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Chọn JDK cho project mở từ web: đọc số phiên bản chính từ chuỗi version của SDK. */
class NewWindowProjectOpenerTest {

    @Test
    void readsMajorJavaVersion() {
        assertEquals(21, NewWindowProjectOpener.javaMajor("Oracle OpenJDK 21.0.2"));
        assertEquals(17, NewWindowProjectOpener.javaMajor("Eclipse Temurin version 17.0.9"));
        assertEquals(25, NewWindowProjectOpener.javaMajor("25"));
        assertEquals(8, NewWindowProjectOpener.javaMajor("java version \"1.8.0_392\""));
        assertEquals(11, NewWindowProjectOpener.javaMajor("corretto-11.0.21"));
        assertEquals(0, NewWindowProjectOpener.javaMajor(null));
        assertEquals(0, NewWindowProjectOpener.javaMajor("unknown"));
    }
}
