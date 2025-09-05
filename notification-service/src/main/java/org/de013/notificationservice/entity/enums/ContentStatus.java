package org.de013.notificationservice.entity.enums;

import lombok.Getter;

/**
 * Content Status Enumeration
 * Defines the lifecycle status of template content
 */
@Getter
public enum ContentStatus {
    
    DRAFT("Draft", "Content is in draft state", false, false),
    PENDING_REVIEW("Pending Review", "Content is waiting for review", false, false),
    IN_REVIEW("In Review", "Content is currently being reviewed", false, false),
    APPROVED("Approved", "Content has been approved", true, false),
    REJECTED("Rejected", "Content has been rejected", false, false),
    PUBLISHED("Published", "Content is published and active", true, true),
    ARCHIVED("Archived", "Content has been archived", false, false),
    EXPIRED("Expired", "Content has expired", false, false);

    private final String displayName;
    private final String description;
    private final boolean isApproved;
    private final boolean isPublic;

    ContentStatus(String displayName, String description, boolean isApproved, boolean isPublic) {
        this.displayName = displayName;
        this.description = description;
        this.isApproved = isApproved;
        this.isPublic = isPublic;
    }

    /**
     * Check if content can be edited
     */
    public boolean canEdit() {
        return this == DRAFT || this == REJECTED;
    }

    /**
     * Check if content can be submitted for review
     */
    public boolean canSubmitForReview() {
        return this == DRAFT || this == REJECTED;
    }

    /**
     * Check if content can be approved
     */
    public boolean canApprove() {
        return this == PENDING_REVIEW || this == IN_REVIEW;
    }

    /**
     * Check if content can be rejected
     */
    public boolean canReject() {
        return this == PENDING_REVIEW || this == IN_REVIEW;
    }

    /**
     * Check if content can be published
     */
    public boolean canPublish() {
        return this == APPROVED;
    }

    /**
     * Check if content can be archived
     */
    public boolean canArchive() {
        return this == PUBLISHED || this == EXPIRED;
    }

    /**
     * Get next possible statuses
     */
    public ContentStatus[] getNextPossibleStatuses() {
        return switch (this) {
            case DRAFT -> new ContentStatus[]{PENDING_REVIEW, ARCHIVED};
            case PENDING_REVIEW -> new ContentStatus[]{IN_REVIEW, DRAFT, ARCHIVED};
            case IN_REVIEW -> new ContentStatus[]{APPROVED, REJECTED, DRAFT};
            case APPROVED -> new ContentStatus[]{PUBLISHED, DRAFT};
            case REJECTED -> new ContentStatus[]{DRAFT, ARCHIVED};
            case PUBLISHED -> new ContentStatus[]{ARCHIVED, EXPIRED};
            case ARCHIVED -> new ContentStatus[]{DRAFT};
            case EXPIRED -> new ContentStatus[]{ARCHIVED, DRAFT};
        };
    }

    /**
     * Check if status transition is valid
     */
    public boolean canTransitionTo(ContentStatus newStatus) {
        ContentStatus[] possibleStatuses = getNextPossibleStatuses();
        for (ContentStatus status : possibleStatuses) {
            if (status == newStatus) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get workflow stage
     */
    public WorkflowStage getWorkflowStage() {
        return switch (this) {
            case DRAFT, REJECTED -> WorkflowStage.CREATION;
            case PENDING_REVIEW, IN_REVIEW -> WorkflowStage.REVIEW;
            case APPROVED -> WorkflowStage.APPROVAL;
            case PUBLISHED -> WorkflowStage.PUBLICATION;
            case ARCHIVED, EXPIRED -> WorkflowStage.ARCHIVAL;
        };
    }

    /**
     * Workflow stages
     */
    public enum WorkflowStage {
        CREATION("Creation", "Content creation and editing"),
        REVIEW("Review", "Content review and feedback"),
        APPROVAL("Approval", "Content approval process"),
        PUBLICATION("Publication", "Content publication and distribution"),
        ARCHIVAL("Archival", "Content archival and cleanup");

        @Getter
        private final String displayName;
        @Getter
        private final String description;

        WorkflowStage(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }
}
