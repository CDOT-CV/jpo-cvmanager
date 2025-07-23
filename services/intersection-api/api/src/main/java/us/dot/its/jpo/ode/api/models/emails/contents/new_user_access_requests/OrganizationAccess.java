package us.dot.its.jpo.ode.api.models.emails.contents.new_user_access_requests;

import lombok.Data;

@Data
public class OrganizationAccess {
    private String organizationName;
    private UserRole role;
}
