package us.dot.its.jpo.ode.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;
import us.dot.its.jpo.ode.api.models.postgres.tables.ObuOtaRequest;
import us.dot.its.jpo.ode.api.repositories.ObuOtaRequestRepository;
import us.dot.its.jpo.ode.api.services.ObuOtaRequestService.Filters;

class ObuOtaRequestServiceTest {
    private ObuOtaRequestRepository repository;
    private ObuOtaRequestService service;

    @BeforeEach
    void setUp() {
        repository = mock(ObuOtaRequestRepository.class);
        service = new ObuOtaRequestService(repository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsRequestsAndUsesNewestFirstByDefault() throws Exception {
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setName("Commsignia");
        ObuOtaRequest request = new ObuOtaRequest();
        request.setRequestId(7);
        request.setManufacturer(manufacturer);
        request.setObuSn("OBU-1");
        request.setRequestDatetime(LocalDateTime.parse("2026-06-01T12:30:00"));
        request.setOriginIp(InetAddress.getByName("10.1.2.3"));
        request.setObuFirmwareVersion("y20.47.0");
        request.setRequestedFirmwareVersion("ob4-generic-ro-y20.48.2.tar.sig");
        request.setErrorStatus(true);
        request.setErrorMessage("404: Firmware not found");
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(request), PageRequest.of(2, 25), 51));

        var page = service.search(2, 25, Filters.none(), null);

        assertThat(page.totalElements()).isEqualTo(51);
        var item = page.items().get(0);
        assertThat(item.requestId()).isEqualTo(7);
        assertThat(item.requestDatetime()).isEqualTo(Instant.parse("2026-06-01T12:30:00Z"));
        assertThat(item.originIp()).isEqualTo("10.1.2.3");
        assertThat(item.status()).isEqualTo("error");
        assertThat(item.manufacturer()).isEqualTo("Commsignia");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "requestDatetime").and(Sort.by(Sort.Direction.DESC, "requestId")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsSupportedSortFields() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.search(0, 10, Filters.none(), "obu_sn,asc");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "obuSn").and(Sort.by(Sort.Direction.ASC, "requestId")));
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 10, , , page must not be negative",
            "0, 0, , , size must be between 1 and 200",
            "0, 201, , , size must be between 1 and 200",
            "0, 10, unknown, , status must be success or error",
            "0, 10, , origin_ip, sort field is not supported",
            "0, 10, , 'request_datetime,sideways', sort direction must be asc or desc",
    })
    void rejectsInvalidRequestsBeforeQuerying(int page, int size, String status, String sort, String message) {
        assertThatThrownBy(() -> service.search(page, size, new Filters(null, null, status, null, null), sort))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsStartAfterEnd() {
        var filters = new Filters(null, null, null, Instant.parse("2026-06-02T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z"));

        assertThatThrownBy(() -> service.search(0, 10, filters, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("start must not be after end");
        verifyNoInteractions(repository);
    }
}
