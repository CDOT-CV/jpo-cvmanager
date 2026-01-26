package us.dot.its.jpo.ode.api.models.emails.contents.access_requests;

import java.util.List;

import lombok.Data;

@Data
public class AccessRequestEmailContents {
    private String email;
    private List<OrganizationAccess> accessRequests;
}
