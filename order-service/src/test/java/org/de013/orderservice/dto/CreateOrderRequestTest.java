package org.de013.orderservice.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CreateOrderRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    void deserialize_WhenUserIdIsPresentInBody_ShouldIgnoreUserId() throws Exception {
        // Regression R4/S2-BOLA: client-submitted userId must be ignored
        String json = "{\n" +
                "  \"user_id\": \"malicious-user-uuid\",\n" +
                "  \"cart_id\": 123,\n" +
                "  \"order_type\": \"STANDARD\",\n" +
                "  \"payment_method\": \"CREDIT_CARD\",\n" +
                "  \"currency\": \"USD\"\n" +
                "}";

        CreateOrderRequest request = objectMapper.readValue(json, CreateOrderRequest.class);

        assertNull(request.getUserId());
        assertEquals(123L, request.getCartId());
        assertEquals("CREDIT_CARD", request.getPaymentMethod());
    }
}
