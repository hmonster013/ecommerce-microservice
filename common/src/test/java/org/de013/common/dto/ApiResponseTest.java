package org.de013.common.dto;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    void success_WithData_ShouldCreateSuccessApiResponse() {
        String testData = "Success Data";
        ApiResponse<String> response = ApiResponse.success(testData);

        assertTrue(response.isSuccess());
        assertEquals(testData, response.getData());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void success_WithDataAndMessage_ShouldCreateSuccessApiResponse() {
        String testData = "Success Data";
        String message = "Operation Completed";
        ApiResponse<String> response = ApiResponse.success(testData, message);

        assertTrue(response.isSuccess());
        assertEquals(testData, response.getData());
        assertEquals(message, response.getMessage());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void success_WithAllParams_ShouldCreateSuccessApiResponse() {
        String testData = "Success Data";
        String message = "Operation Completed";
        String code = "SUCCESS_200";
        ApiResponse<String> response = ApiResponse.success(testData, message, code);

        assertTrue(response.isSuccess());
        assertEquals(testData, response.getData());
        assertEquals(message, response.getMessage());
        assertEquals(code, response.getCode());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void error_WithMessage_ShouldCreateErrorApiResponse() {
        String errorMessage = "Something went wrong";
        ApiResponse<Void> response = ApiResponse.error(errorMessage);

        assertFalse(response.isSuccess());
        assertEquals(errorMessage, response.getMessage());
        assertNull(response.getData());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void error_WithMessageAndCode_ShouldCreateErrorApiResponse() {
        String errorMessage = "Something went wrong";
        String code = "ERR_500";
        ApiResponse<Void> response = ApiResponse.error(errorMessage, code);

        assertFalse(response.isSuccess());
        assertEquals(errorMessage, response.getMessage());
        assertEquals(code, response.getCode());
        assertNull(response.getData());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void error_WithAllParams_ShouldCreateErrorApiResponse() {
        String errorMessage = "Something went wrong";
        String code = "ERR_404";
        String path = "/api/v1/test";
        ApiResponse<Void> response = ApiResponse.error(errorMessage, code, path);

        assertFalse(response.isSuccess());
        assertEquals(errorMessage, response.getMessage());
        assertEquals(code, response.getCode());
        assertEquals(path, response.getPath());
        assertNotNull(response.getTimestamp());
    }
}
