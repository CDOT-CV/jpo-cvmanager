package us.dot.its.jpo.ode.api.models.emails.contents.access_requests;

import lombok.Data;

@Data
public class OrganizationAccess {
    private String organizationName;
    private UserRole role;
}
