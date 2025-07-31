package us.dot.its.jpo.ode.api.accessors.counts;

import java.util.List;

import us.dot.its.jpo.ode.api.models.MessageCount;

public interface CountsRepository {
    /**
     * Get message counts for an RSU over a specified timespan
     * 
     * @param rsuIp     the RSU IP address
     * @param startTime start time in UTC milliseconds
     * @param endTime   end time in UTC milliseconds
     * @return list of message counts
     */
    List<MessageCount> getRsuMessageCounts(String rsuIp, Long startTime, Long endTime);

    /**
     * Get message counts for all RSUs in an organization over a specified timespan
     * Returns the same format as getMessageCounts for consistency
     * 
     * @param organization the organization name
     * @param messageType  the message type to query for (e.g., "BSM", "MAP")
     * @param startTime    start time in UTC milliseconds
     * @param endTime      end time in UTC milliseconds
     * @return list of message counts for all RSUs in the organization
     */
    List<MessageCount> getRsuOrganizationMessageCounts(String organization, String messageType, Long startTime,
            Long endTime);
}