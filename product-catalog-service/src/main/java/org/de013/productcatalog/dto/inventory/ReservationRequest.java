package org.de013.productcatalog.dto.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReservationRequest(@NotNull UUID operationId, @NotNull TargetKind targetKind,
                                 @NotNull @Positive Long targetId, @NotNull @Positive Integer quantity,
                                 @NotBlank @Size(max = 255) String owner) {
    public enum TargetKind { PRODUCT, VARIANT }
}
