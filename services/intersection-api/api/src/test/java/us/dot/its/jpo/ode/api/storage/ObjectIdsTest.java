package us.dot.its.jpo.ode.api.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class ObjectIdsTest {
    @Test
    void roundTripsProviderAndObjectName() {
        String id = ObjectIds.encode("gcp", "ota/Commsignia/y20.48.2/ob4-generic-ro-y20.48.2.tar.sig");

        assertThat(id).doesNotContain("=", "+", "/");
        assertThat(ObjectIds.decode(id))
                .isEqualTo(new ObjectIds.Identity("gcp", "ota/Commsignia/y20.48.2/ob4-generic-ro-y20.48.2.tar.sig"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "gcp", "gcp\n", "gcp\n ", "gcp\nfolder/", "gcp\nfile\u0001name" })
    void rejectsIdentifiersThatDoNotNameOneObject(String identity) {
        String id = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(identity.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> ObjectIds.decode(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid firmware object ID");
    }

    @Test
    void rejectsMalformedBase64() {
        assertThatThrownBy(() -> ObjectIds.decode("not base64!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid firmware object ID");
    }
}
