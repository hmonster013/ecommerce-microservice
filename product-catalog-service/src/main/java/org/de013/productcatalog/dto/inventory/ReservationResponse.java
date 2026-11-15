package org.de013.productcatalog.dto.inventory;

import java.util.UUID;

public record ReservationResponse(UUID operationId, ReservationRequest.TargetKind targetKind, Long targetId,
                                  Integer quantity, String owner, String state, String reason) { }
