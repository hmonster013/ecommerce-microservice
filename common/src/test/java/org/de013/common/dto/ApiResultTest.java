package org.de013.common.dto;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class ApiResultTest {

    @Test
    void success_WithData_ShouldCreateSuccessApiResult() {
        String testData = "Success Data";
        ApiResult<String> response = ApiResult.success(testData);

        assertTrue(response.isSuccess());
        assertEquals(testData, response.getData());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void success_WithDataAndMessage_ShouldCreateSuccessApiResult() {
        String testData = "Success Data";
        String message = "Operation Completed";
        ApiResult<String> response = ApiResult.success(testData, message);

        assertTrue(response.isSuccess());
        assertEquals(testData, response.getData());
        assertEquals(message, response.getMessage());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void success_WithAllParams_ShouldCreateSuccessApiResult() {
        String testData = "Success Data";
        String message = "Operation Completed";
        String code = "SUCCESS_200";
        ApiResult<String> response = ApiResult.success(testData, message, code);

        assertTrue(response.isSuccess());
        assertEquals(testData, response.getData());
        assertEquals(message, response.getMessage());
        assertEquals(code, response.getCode());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void error_WithMessage_ShouldCreateErrorApiResult() {
        String errorMessage = "Something went wrong";
        ApiResult<Void> response = ApiResult.error(errorMessage);

        assertFalse(response.isSuccess());
        assertEquals(errorMessage, response.getMessage());
        assertNull(response.getData());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void error_WithMessageAndCode_ShouldCreateErrorApiResult() {
        String errorMessage = "Something went wrong";
        String code = "ERR_500";
        ApiResult<Void> response = ApiResult.error(errorMessage, code);

        assertFalse(response.isSuccess());
        assertEquals(errorMessage, response.getMessage());
        assertEquals(code, response.getCode());
        assertNull(response.getData());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void error_WithAllParams_ShouldCreateErrorApiResult() {
        String errorMessage = "Something went wrong";
        String code = "ERR_404";
        String path = "/api/v1/test";
        ApiResult<Void> response = ApiResult.error(errorMessage, code, path);

        assertFalse(response.isSuccess());
        assertEquals(errorMessage, response.getMessage());
        assertEquals(code, response.getCode());
        assertEquals(path, response.getPath());
        assertNotNull(response.getTimestamp());
    }
}
