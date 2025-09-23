package org.de013.shoppingcart.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * DTO for updating gift options of cart items
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
@Schema(description = "Request to update gift options for cart item")
public class GiftOptionsDto {

    @Schema(description = "Mark item as gift", example = "true")
    @JsonProperty("is_gift")
    private Boolean isGift;

    @Schema(description = "Gift message", example = "Happy Birthday! Hope you enjoy this gift.")
    @Size(max = 500, message = "{gift.message.size}")
    @JsonProperty("gift_message")
    private String giftMessage;

    @Schema(description = "Gift wrap type", example = "premium", allowableValues = {"basic", "premium", "luxury"})
    @Size(max = 50, message = "{gift.wrap.size}")
    @JsonProperty("gift_wrap_type")
    private String giftWrapType;

    @Schema(description = "Gift wrap color", example = "red")
    @Size(max = 30, message = "{gift.wrap.color.size}")
    @JsonProperty("gift_wrap_color")
    private String giftWrapColor;

    @Schema(description = "Sender name", example = "John Doe")
    @Size(max = 100, message = "{gift.sender.size}")
    @JsonProperty("sender_name")
    private String senderName;

    @Schema(description = "Recipient name", example = "Jane Smith")
    @Size(max = 100, message = "{gift.recipient.size}")
    @JsonProperty("recipient_name")
    private String recipientName;

    @Schema(description = "Special delivery instructions for gift", example = "Please deliver between 9-12 AM")
    @Size(max = 300, message = "{gift.delivery.instructions.size}")
    @JsonProperty("delivery_instructions")
    private String deliveryInstructions;

    /**
     * Validate gift message when gift status is true
     */
    @JsonIgnore
    @AssertTrue(message = "{gift.message.required}")
    public boolean isValidGiftMessage() {
        if (Boolean.TRUE.equals(isGift)) {
            return giftMessage != null && !giftMessage.trim().isEmpty();
        }
        return true;
    }

    /**
     * Validate that at least one gift field is being updated
     */
    @JsonIgnore
    @AssertTrue(message = "{gift.field.required}")
    public boolean hasGiftFields() {
        return isGift != null || giftMessage != null || giftWrapType != null || 
               giftWrapColor != null || senderName != null || recipientName != null ||
               deliveryInstructions != null;
    }

    /**
     * Check if this is enabling gift mode
     */
    @JsonIgnore
    public boolean isEnablingGift() {
        return Boolean.TRUE.equals(isGift);
    }

    /**
     * Check if this is disabling gift mode
     */
    @JsonIgnore
    public boolean isDisablingGift() {
        return Boolean.FALSE.equals(isGift);
    }

    /**
     * Check if gift wrap is being updated
     */
    @JsonIgnore
    public boolean isUpdatingGiftWrap() {
        return giftWrapType != null || giftWrapColor != null;
    }

    /**
     * Check if gift message is being updated
     */
    @JsonIgnore
    public boolean isUpdatingGiftMessage() {
        return giftMessage != null && !giftMessage.trim().isEmpty();
    }

    /**
     * Check if sender/recipient info is being updated
     */
    @JsonIgnore
    public boolean isUpdatingParticipants() {
        return senderName != null || recipientName != null;
    }
}
