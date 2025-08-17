package org.de013.productcatalog.entity.enums;

import lombok.Getter;

@Getter
public enum ReviewStatus {
    PENDING("Pending", "Review is waiting for moderation"),
    APPROVED("Approved", "Review has been approved and is visible"),
    REJECTED("Rejected", "Review has been rejected and is not visible"),
    FLAGGED("Flagged", "Review has been flagged for inappropriate content"),
    SPAM("Spam", "Review has been marked as spam"),
    DELETED("Deleted", "Review has been deleted");

    private final String displayName;
    private final String description;

    ReviewStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public boolean isVisible() {
        return this == APPROVED;
    }

    public boolean needsModeration() {
        return this == PENDING || this == FLAGGED;
    }

    public boolean isRejected() {
        return this == REJECTED || this == SPAM || this == DELETED;
    }
}
