package us.dot.its.jpo.ode.api.models.emails.contents.new_user_access_requests;

import java.util.List;

import lombok.Data;

@Data
public class NewUserAccessRequestEmailContents {
    private String email;
    private List<OrganizationAccess> accessRequests;
}
