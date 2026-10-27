package us.dot.its.jpo.ode.api.services;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class FirmwareUpgradeStatusServiceTest {
    private FirmwareUpgradeStatusService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        service = new FirmwareUpgradeStatusService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "endpoint", "http://scheduler/");
        server = MockRestServiceServer.createServer((RestTemplate) ReflectionTestUtils.getField(service, "client"));
    }

    @Test
    void readsSchedulerJsonPreservesQueueOrderAndDoesNotForwardUnexpectedCredentials() {
        server.expect(requestTo("http://scheduler/list_active_upgrades"))
                .andRespond(withSuccess("""
                        {"active_upgrades":{"192.0.2.1":{"manufacturer":"Commsignia","model":"ITS-RS4-M",
                        "target_firmware_version":"v2","ssh_password":"not-for-the-webapp","install_package":"file.tar.sig"}},
                        "upgrade_queue":["192.0.2.3","192.0.2.2"]}
                        """, MediaType.APPLICATION_JSON));
        var status = service.getStatus();
        assertThat(status.activeUpgrades().get("192.0.2.1").targetFirmwareVersion()).isEqualTo("v2");
        assertThat(status.upgradeQueue()).containsExactly("192.0.2.3", "192.0.2.2");
        assertThat(JsonMapper.builder().build().writeValueAsString(status)).doesNotContain("ssh_password", "not-for-the-webapp", "install_package");
        server.verify();
    }

    @Test
    void emptySchedulerIsAValidSnapshot() {
        server.expect(anything()).andRespond(withSuccess("{\"active_upgrades\":{},\"upgrade_queue\":[]}", MediaType.APPLICATION_JSON));
        assertThat(service.getStatus().activeUpgrades()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "not-json", "{\"active_upgrades\":{},\"upgrade_queue\":[null]}",
            "{\"active_upgrades\":{\"192.0.2.1\":null},\"upgrade_queue\":[]}"})
    void malformedSnapshotsAreNotReportedAsAnIdleScheduler(String body) {
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(service::getStatus).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void schedulerErrorsAreReportedWithoutLeakingItsResponse() {
        server.expect(anything()).andRespond(withServerError().body("private backend details"));
        assertThatThrownBy(service::getStatus).isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(ex.getReason()).doesNotContain("private backend details");
        });
    }

    @Test
    void networkFailuresAreReportedAsBadGateway() {
        server.expect(anything()).andRespond(withException(new IOException("timeout")));
        assertThatThrownBy(service::getStatus).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void unconfiguredSchedulerDoesNotMakeAnHttpRequest() {
        ReflectionTestUtils.setField(service, "endpoint", "");
        assertThatThrownBy(service::getStatus).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        server.verify();
    }
}
