package us.dot.its.jpo.ode.api.models.credentials;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Listing view of an SSH or SNMP credential for one organization. Deliberately
 * has no password field: it is returned to organizations that use the
 * credential on an RSU without owning it.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CredentialSummaryDTO {

    private String nickname;

    private String username;

    /** Name of the organization that owns (and may manage) the credential. */
    private String ownerOrganization;

    /** IPs of the RSUs in the requested organization that use the credential. */
    private List<String> rsuIps;
}
