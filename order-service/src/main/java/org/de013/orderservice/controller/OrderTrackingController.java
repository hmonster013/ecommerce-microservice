package org.de013.orderservice.controller;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.dto.request.AddTrackingRequest;
import org.de013.orderservice.dto.response.OrderResponse;
import org.de013.orderservice.dto.response.OrderTrackingResponse;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.repository.OrderTrackingRepository;
import org.de013.orderservice.service.OrderTrackingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderTrackingController {

    private final OrderTrackingRepository trackingRepository;
    private final OrderTrackingService trackingService;

    @GetMapping("/{orderId}/tracking")
    public java.util.List<OrderTrackingResponse> getTracking(@PathVariable Long orderId) {
        return trackingRepository.findByOrderIdOrderByTimestampDesc(orderId).stream().map(ot -> OrderTrackingResponse.builder()
                .id(ot.getId())
                .orderId(ot.getOrder().getId())
                .trackingStatus(ot.getTrackingStatus())
                .statusDisplayName(ot.getTrackingStatus() != null ? ot.getTrackingStatus().getDisplayName() : null)
                .statusDescription(ot.getTrackingStatus() != null ? ot.getTrackingStatus().getDescription() : null)
                .location(ot.getLocation())
                .locationDetails(ot.getLocationDetails())
                .completeLocation(ot.getCompleteLocation())
                .city(ot.getCity())
                .state(ot.getState())
                .country(ot.getCountry())
                .postalCode(ot.getPostalCode())
                .timestamp(ot.getTimestamp())
                .notes(ot.getNotes())
                .trackingNumber(ot.getTrackingNumber())
                .carrier(ot.getCarrier())
                .carrierService(ot.getCarrierService())
                .estimatedDeliveryDate(ot.getEstimatedDeliveryDate())
                .actualDeliveryDate(ot.getActualDeliveryDate())
                .deliveryAttempt(ot.getDeliveryAttempt())
                .deliveryFailureReason(ot.getDeliveryFailureReason())
                .receivedBy(ot.getReceivedBy())
                .signatureRequired(ot.getSignatureRequired())
                .signatureObtained(ot.getSignatureObtained())
                .proofOfDeliveryUrl(ot.getProofOfDeliveryUrl())
                .isAutomated(ot.getIsAutomated())
                .updateSource(ot.getUpdateSource())
                .updatedByUserId(ot.getUpdatedByUserId())
                .externalTrackingId(ot.getExternalTrackingId())
                .isCustomerVisible(ot.getIsCustomerVisible())
                .priorityLevel(ot.getPriorityLevel())
                .progressPercentage(ot.getProgressPercentage())
                .isDeliveryUpdate(ot.isDeliveryUpdate())
                .isProblemUpdate(ot.isProblemUpdate())
                .requiresCustomerAction(ot.requiresCustomerAction())
                .isDeliveryAttempt(ot.isDeliveryAttempt())
                .isDeliverySuccessful(ot.isDeliverySuccessful())
                .isDeliveryFailed(ot.isDeliveryFailed())
                .hoursSinceUpdate(ot.getHoursSinceUpdate())
                .isRecent(ot.isRecent())
                .createdAt(ot.getCreatedAt())
                .build()).collect(Collectors.toList());
    }

    @PostMapping("/{orderId}/tracking")
    public OrderResponse addTracking(@PathVariable Long orderId, @RequestBody AddTrackingRequest req) {
        // Prefer explicit shipping info
        if (req.getTrackingNumber() != null && req.getCarrier() != null) {
            return trackingService.markShipped(orderId, req.getTrackingNumber(), req.getCarrier());
        }
        // Delivered states
        if (req.getTrackingStatus() != null && req.getTrackingStatus().isDelivered()) {
            return trackingService.markDelivered(orderId);
        }
        // Map tracking status to an order status progression
        OrderStatus next = mapTrackingToOrderStatus(req.getTrackingStatus());
        return trackingService.advanceStatus(orderId, next, req.getNotes());
    }

    private OrderStatus mapTrackingToOrderStatus(org.de013.orderservice.entity.enums.TrackingStatus ts) {
        if (ts == null) return OrderStatus.PROCESSING;
        return switch (ts) {
            case ORDER_PLACED -> OrderStatus.CONFIRMED;
            case PROCESSING, PICKING, PACKING -> OrderStatus.PROCESSING;
            case READY_FOR_SHIPMENT -> OrderStatus.PREPARING;
            case SHIPPED, IN_TRANSIT, ARRIVED_AT_FACILITY -> OrderStatus.SHIPPED;
            case OUT_FOR_DELIVERY, DELIVERY_ATTEMPTED, READY_FOR_PICKUP, SIGNATURE_REQUIRED -> OrderStatus.OUT_FOR_DELIVERY;
            case DELIVERY_CONFIRMED, PICKED_UP -> OrderStatus.DELIVERED;
            case RETURNING -> OrderStatus.RETURNING;
            case RETURNED -> OrderStatus.RETURNED;
            case LOST, DAMAGED, CANCELLED, ON_HOLD -> OrderStatus.ON_HOLD;
            case IN_CUSTOMS, CUSTOMS_CLEARED, DELIVERY_RESCHEDULED -> OrderStatus.PROCESSING;
            default -> OrderStatus.PROCESSING;
        };
    }

    @GetMapping("/tracking/{trackingNumber}")
    public Page<OrderTrackingResponse> trackByNumber(@PathVariable String trackingNumber, Pageable pageable) {
        return trackingRepository.findByTrackingNumber(trackingNumber, pageable)
                .map(ot -> OrderTrackingResponse.builder()
                        .id(ot.getId())
                        .orderId(ot.getOrder().getId())
                        .trackingStatus(ot.getTrackingStatus())
                        .statusDisplayName(ot.getTrackingStatus() != null ? ot.getTrackingStatus().getDisplayName() : null)
                        .statusDescription(ot.getTrackingStatus() != null ? ot.getTrackingStatus().getDescription() : null)
                        .location(ot.getLocation())
                        .timestamp(ot.getTimestamp())
                        .trackingNumber(ot.getTrackingNumber())
                        .carrier(ot.getCarrier())
                        .build());
    }
}

