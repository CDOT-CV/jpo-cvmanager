package us.dot.its.jpo.ode.api.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;
import us.dot.its.jpo.ode.api.models.postgres.tables.ObuOtaRequest;
import us.dot.its.jpo.ode.api.models.storage.ObuOtaRequestPage;
import us.dot.its.jpo.ode.api.services.ObuOtaRequestService;
import us.dot.its.jpo.ode.api.services.ObuOtaRequestService.Filters;

@SpringBootTest(properties = "firmware-upload.cleanup.enabled=false")
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
@Transactional
class ObuOtaRequestRepositoryTest {
    @Autowired
    private ObuOtaRequestRepository repository;

    @Autowired
    private ManufacturerRepository manufacturerRepository;

    @Autowired
    private ObuOtaRequestService service;

    @Autowired
    private EntityManager entityManager;

    private Manufacturer commsignia;
    private Manufacturer other;

    @BeforeEach
    void setUp() throws Exception {
        repository.deleteAll();
        commsignia = saveManufacturer("OTA Repository Commsignia");
        other = saveManufacturer("OTA Repository Other");

        save(commsignia, "OBU-100", "2026-06-01T00:00:00", "10.0.0.1", false, "");
        save(commsignia, "OBU-100", "2026-06-01T00:10:00", "10.0.0.1", true, "404: Firmware not found");
        save(commsignia, "obu-200_x", "2026-06-02T00:00:00", "2001:db8::1", false, "");
        save(other, "OBU-300", "2026-06-03T00:00:00", "10.0.0.3", false, "");
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void roundTripsBitStatusInetAddressAndUtcTimestamp() {
        var page = service.search(0, 10, Filters.none(), "request_datetime,asc");

        assertThat(page.totalElements()).isEqualTo(4);
        ObuOtaRequestPage.Item first = page.items().get(0);
        assertThat(first.obuSn()).isEqualTo("OBU-100");
        assertThat(first.status()).isEqualTo("success");
        assertThat(first.originIp()).isEqualTo("10.0.0.1");
        assertThat(first.requestDatetime()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(first.manufacturer()).isEqualTo("OTA Repository Commsignia");

        ObuOtaRequestPage.Item failed = page.items().get(1);
        assertThat(failed.status()).isEqualTo("error");
        assertThat(failed.errorMessage()).isEqualTo("404: Firmware not found");
        assertThat(page.items().get(2).originIp()).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    void defaultsToNewestFirstAndPagesStably() {
        var firstPage = service.search(0, 2, Filters.none(), "request_datetime,desc");
        var secondPage = service.search(1, 2, Filters.none(), "request_datetime,desc");

        assertThat(firstPage.totalElements()).isEqualTo(4);
        assertThat(firstPage.items()).extracting(ObuOtaRequestPage.Item::obuSn)
                .containsExactly("OBU-300", "obu-200_x");
        assertThat(secondPage.items()).extracting(ObuOtaRequestPage.Item::requestDatetime)
                .containsExactly(Instant.parse("2026-06-01T00:10:00Z"), Instant.parse("2026-06-01T00:00:00Z"));
    }

    @Test
    void breaksTimestampTiesByRequestIdInTheSortDirection() throws Exception {
        String tiedAt = "2026-06-04T00:00:00";
        int first = save(commsignia, "OBU-400", tiedAt, "10.0.0.4", false, "").getRequestId();
        int second = save(commsignia, "OBU-401", tiedAt, "10.0.0.5", false, "").getRequestId();
        int third = save(commsignia, "OBU-402", tiedAt, "10.0.0.6", false, "").getRequestId();
        entityManager.flush();
        entityManager.clear();
        var tied = new Filters(null, null, null, Instant.parse("2026-06-04T00:00:00Z"), null);

        assertThat(service.search(0, 10, tied, "request_datetime,asc").items())
                .extracting(ObuOtaRequestPage.Item::requestId).containsExactly(first, second, third);
        assertThat(service.search(0, 10, tied, "request_datetime,desc").items())
                .extracting(ObuOtaRequestPage.Item::requestId).containsExactly(third, second, first);
    }

    @Test
    void pagesAcrossTiedTimestampsWithoutRepeatingOrSkippingRequests() throws Exception {
        String tiedAt = "2026-06-04T00:00:00";
        int first = save(commsignia, "OBU-400", tiedAt, "10.0.0.4", false, "").getRequestId();
        int second = save(commsignia, "OBU-401", tiedAt, "10.0.0.5", false, "").getRequestId();
        int third = save(commsignia, "OBU-402", tiedAt, "10.0.0.6", false, "").getRequestId();
        entityManager.flush();
        entityManager.clear();

        // The page boundary falls between requests with the same timestamp
        var firstPage = service.search(0, 2, Filters.none(), "request_datetime,desc");
        var secondPage = service.search(1, 2, Filters.none(), "request_datetime,desc");

        assertThat(firstPage.items()).extracting(ObuOtaRequestPage.Item::requestId)
                .containsExactly(third, second);
        assertThat(secondPage.items()).extracting(ObuOtaRequestPage.Item::requestId).first().isEqualTo(first);
        assertThat(secondPage.items()).extracting(ObuOtaRequestPage.Item::obuSn).containsExactly("OBU-400", "OBU-300");
    }

    @Test
    void breaksSerialTiesByRequestIdInTheSortDirection() {
        var serial = new Filters("OBU-100", null, null, null, null);
        var ascending = service.search(0, 10, serial, "obu_sn,asc").items();
        var descending = service.search(0, 10, serial, "obu_sn,desc").items();

        assertThat(ascending).hasSize(2);
        assertThat(ascending.get(0).requestId()).isLessThan(ascending.get(1).requestId());
        assertThat(descending).extracting(ObuOtaRequestPage.Item::requestId)
                .containsExactly(ascending.get(1).requestId(), ascending.get(0).requestId());
    }

    @Test
    void filtersBySerialManufacturerStatusAndTimeRange() {
        assertThat(search(new Filters("obu-1", null, null, null, null))).isEqualTo(2);
        // Wildcards in the serial filter are matched literally
        assertThat(search(new Filters("_", null, null, null, null))).isEqualTo(1);
        assertThat(search(new Filters("%", null, null, null, null))).isZero();
        assertThat(search(new Filters(null, "ota repository other", null, null, null))).isEqualTo(1);
        assertThat(search(new Filters(null, null, "error", null, null))).isEqualTo(1);
        assertThat(search(new Filters(null, null, "success", null, null))).isEqualTo(3);
        assertThat(search(new Filters(null, null, null,
                Instant.parse("2026-06-01T00:10:00Z"), Instant.parse("2026-06-02T00:00:00Z")))).isEqualTo(2);
        assertThat(search(new Filters("OBU-100", "OTA Repository Commsignia", "success", null, null))).isEqualTo(1);
    }

    private long search(Filters filters) {
        return service.search(0, 50, filters, null).totalElements();
    }

    private Manufacturer saveManufacturer(String name) {
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setName(name);
        return manufacturerRepository.save(manufacturer);
    }

    private ObuOtaRequest save(Manufacturer manufacturer, String serial, String requestedAt, String ip,
            boolean error, String message) throws Exception {
        ObuOtaRequest request = new ObuOtaRequest();
        request.setManufacturer(manufacturer);
        request.setObuSn(serial);
        request.setRequestDatetime(LocalDateTime.parse(requestedAt));
        request.setOriginIp(InetAddress.getByName(ip));
        request.setObuFirmwareVersion("y20.47.0-b220001");
        request.setRequestedFirmwareVersion("ob4-generic-ro-secureboot-y20.48.2-b228647.tar.sig");
        request.setErrorStatus(error);
        request.setErrorMessage(message);
        return repository.save(request);
    }
}
