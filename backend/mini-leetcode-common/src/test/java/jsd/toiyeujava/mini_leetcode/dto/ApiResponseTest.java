package jsd.toiyeujava.mini_leetcode.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    @DisplayName("success(data) phải trả về ApiResponse với success=true, statusCode=200, timestamp không null")
    void testSuccessWithData() {
        String payload = "Hello Mini LeetCode";
        ApiResponse<String> response = ApiResponse.success(payload);

        assertNotNull(response, "Response không được null");
        assertTrue(response.isSuccess(), "success phải là true");
        assertEquals(200, response.getStatusCode(), "statusCode mặc định phải là 200");
        assertEquals("Success", response.getMessage(), "Message mặc định phải là 'Success'");
        assertEquals(payload, response.getData(), "Data trả về phải khớp với payload truyền vào");
        assertNotNull(response.getTimestamp(), "Timestamp không được null");
    }

    @Test
    @DisplayName("success(message, data) phải trả về đúng thông điệp tùy biến")
    void testSuccessWithMessageAndData() {
        Integer payload = 42;
        ApiResponse<Integer> response = ApiResponse.success("Custom Message", payload);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(200, response.getStatusCode());
        assertEquals("Custom Message", response.getMessage());
        assertEquals(payload, response.getData());
    }

    @Test
    @DisplayName("error(statusCode, message) phải trả về success=false và data=null")
    void testError() {
        ApiResponse<Void> response = ApiResponse.error(404, "Problem Not Found");

        assertNotNull(response);
        assertFalse(response.isSuccess(), "success phải là false khi có lỗi");
        assertEquals(404, response.getStatusCode());
        assertEquals("Problem Not Found", response.getMessage());
        assertNull(response.getData(), "Data khi có lỗi phải là null");
        assertNotNull(response.getTimestamp());
    }
}
