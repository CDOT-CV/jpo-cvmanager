package us.dot.its.jpo.ode.api.models.credentials;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SnmpCredentialDTO {

    @JsonProperty
    private Integer id;

    @JsonProperty
    private String nickname;

    @JsonProperty
    private String username;

    @JsonProperty
    private String password;

    @JsonProperty
    private Integer ownerOrganizationId;
}
