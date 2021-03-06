package org.tdar.core.bean.notification;

public enum Status {
    IN_REVIEW,
    QUEUED,
    ERROR,
    SENT,
    DISCARD,
    BOUNCED;

    public boolean isInReview() {
        return this == IN_REVIEW;
    }
}
