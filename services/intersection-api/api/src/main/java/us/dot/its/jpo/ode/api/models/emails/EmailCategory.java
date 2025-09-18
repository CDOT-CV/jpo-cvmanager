package us.dot.its.jpo.ode.api.models.emails;

public enum EmailCategory {
    SUPPORT_REQUEST,
    FIRMWARE_UPGRADE_FAILURE,
    RSU_ERROR_SUMMARY,
    MESSAGE_COUNTS,
    ACCESS_REQUEST,
    INTERSECTION_NOTIFICATION,
    INTERSECTION_NOTIFICATION_SUMMARY,
    INTERSECTION_CEASE_BROADCAST_NOTIFICATION,
    INTERSECTION_REPORT_GENERATED,
    ANNOUNCEMENT,
    CRITICAL_ERROR_MESSAGE;

    public String getCategoryKey() {
        return switch (this) {
            case SUPPORT_REQUEST -> "Support Requests";
            case FIRMWARE_UPGRADE_FAILURE -> "Firmware Upgrade Failures";
            case RSU_ERROR_SUMMARY -> "RSU Error Summary";
            case MESSAGE_COUNTS -> "Daily Message Counts";
            case ACCESS_REQUEST -> "Access Requests";
            case INTERSECTION_NOTIFICATION -> "Intersection Notifications";
            case INTERSECTION_NOTIFICATION_SUMMARY -> "Intersection Notification Summary";
            case INTERSECTION_CEASE_BROADCAST_NOTIFICATION -> "Intersection Cease Broadcast Notifications";
            case INTERSECTION_REPORT_GENERATED -> "Intersection Report Generated";
            case ANNOUNCEMENT -> "Announcements";
            case CRITICAL_ERROR_MESSAGE -> "Critical Error Messages";
            default -> "";
        };
    }
}
