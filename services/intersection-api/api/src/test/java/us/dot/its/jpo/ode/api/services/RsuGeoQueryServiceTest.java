package us.dot.its.jpo.ode.api.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.repositories.RsuRepository;

@ExtendWith(MockitoExtension.class)
class RsuGeoQueryServiceTest {
    private static final String ORGANIZATION = "Test";
    private static final String EXPECTED_POLYGON = "POLYGON((-105.34460908203145 39.724583197251334,"
            + "-105.34666901855489 39.670180083300174,"
            + "-105.25122529296911 39.679162192647944,"
            + "-105.2539718750002 39.72088725644132,"
            + "-105.34460908203145 39.724583197251334))";

    @Mock
    private RsuRepository rsuRepository;

    private RsuGeoQueryService service;

    @BeforeEach
    void setUp() {
        service = new RsuGeoQueryService(rsuRepository);
    }

    @Test
    void findRsuIps_buildsPolygonAndReturnsHostAddresses() {
        List<List<Double>> geometry = sampleRing();
        when(rsuRepository.findIpv4AddressesInPolygon(ORGANIZATION, EXPECTED_POLYGON))
                .thenReturn(List.of("10.11.81.12"));

        List<String> result = service.findRsuIps(ORGANIZATION, geometry, null);

        assertEquals(List.of("10.11.81.12"), result);
        assertEquals(2, geometry.getFirst().size());
        verify(rsuRepository, never()).findIpv4AddressesInPolygonByManufacturer(any(), any(), any());
    }

    @Test
    void findRsuIps_selectVendorOmitsManufacturerFilter() {
        when(rsuRepository.findIpv4AddressesInPolygon(eq(ORGANIZATION), eq(EXPECTED_POLYGON)))
                .thenReturn(List.of("10.0.0.1"));

        List<String> result = service.findRsuIps(ORGANIZATION, sampleRing(), "Select Vendor");

        assertEquals(List.of("10.0.0.1"), result);
        verify(rsuRepository, never()).findIpv4AddressesInPolygonByManufacturer(any(), any(), any());
    }

    @Test
    void findRsuIps_filtersByManufacturer() {
        when(rsuRepository.findIpv4AddressesInPolygonByManufacturer(ORGANIZATION, EXPECTED_POLYGON, "Commsignia"))
                .thenReturn(List.of("10.0.0.2"));

        List<String> result = service.findRsuIps(ORGANIZATION, sampleRing(), "Commsignia");

        assertEquals(List.of("10.0.0.2"), result);
        verify(rsuRepository, never()).findIpv4AddressesInPolygon(any(), any());
    }

    @Test
    void findRsuIps_emptyRepositoryResultBecomesEmptyList() {
        when(rsuRepository.findIpv4AddressesInPolygon(ORGANIZATION, EXPECTED_POLYGON)).thenReturn(List.of());

        assertEquals(List.of(), service.findRsuIps(ORGANIZATION, sampleRing(), "  "));
    }

    @Test
    void findRsuIps_rejectsPointWithFewerThanTwoNumbers() {
        List<List<Double>> geometry = new ArrayList<>();
        geometry.add(new ArrayList<>(List.of(5.1)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.findRsuIps(ORGANIZATION, geometry, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(rsuRepository, never()).findIpv4AddressesInPolygon(any(), any());
    }

    @Test
    void findRsuIps_rejectsRingShorterThanFourPositions() {
        List<List<Double>> geometry = new ArrayList<>();
        geometry.add(new ArrayList<>(List.of(0.0, 0.0)));
        geometry.add(new ArrayList<>(List.of(1.0, 0.0)));
        geometry.add(new ArrayList<>(List.of(0.0, 0.0)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.findRsuIps(ORGANIZATION, geometry, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Polygon ring must have at least 4 positions", exception.getReason());
        verifyRepositoryNotCalled();
    }

    @Test
    void findRsuIps_rejectsUnclosedRing() {
        List<List<Double>> geometry = new ArrayList<>();
        geometry.add(new ArrayList<>(List.of(0.0, 0.0)));
        geometry.add(new ArrayList<>(List.of(1.0, 0.0)));
        geometry.add(new ArrayList<>(List.of(1.0, 1.0)));
        geometry.add(new ArrayList<>(List.of(0.0, 1.0)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.findRsuIps(ORGANIZATION, geometry, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Polygon ring must be closed", exception.getReason());
        verifyRepositoryNotCalled();
    }

    @Test
    void findRsuIps_rejectsNonFiniteCoordinate() {
        List<List<Double>> geometry = new ArrayList<>(sampleRing());
        geometry.set(1, new ArrayList<>(List.of(Double.NaN, 39.670180083300174)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.findRsuIps(ORGANIZATION, geometry, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Each geometry coordinate must be a finite number", exception.getReason());

        geometry.set(1, new ArrayList<>(List.of(-105.34666901855489, Double.POSITIVE_INFINITY)));
        exception = assertThrows(ResponseStatusException.class,
                () -> service.findRsuIps(ORGANIZATION, geometry, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Each geometry coordinate must be a finite number", exception.getReason());
        verifyRepositoryNotCalled();
    }

    @Test
    void findRsuIps_passesPolygonBuiltFromSampleRing() {
        when(rsuRepository.findIpv4AddressesInPolygon(eq(ORGANIZATION), any())).thenReturn(List.of());

        service.findRsuIps(ORGANIZATION, sampleRing(), null);

        ArgumentCaptor<String> polygon = ArgumentCaptor.forClass(String.class);
        verify(rsuRepository).findIpv4AddressesInPolygon(eq(ORGANIZATION), polygon.capture());
        assertEquals(EXPECTED_POLYGON, polygon.getValue());
    }

    private void verifyRepositoryNotCalled() {
        verify(rsuRepository, never()).findIpv4AddressesInPolygon(any(), any());
        verify(rsuRepository, never()).findIpv4AddressesInPolygonByManufacturer(any(), any(), any());
    }

    private static List<List<Double>> sampleRing() {
        return new ArrayList<>(List.of(
                new ArrayList<>(List.of(-105.34460908203145, 39.724583197251334)),
                new ArrayList<>(List.of(-105.34666901855489, 39.670180083300174)),
                new ArrayList<>(List.of(-105.25122529296911, 39.679162192647944)),
                new ArrayList<>(List.of(-105.2539718750002, 39.72088725644132)),
                new ArrayList<>(List.of(-105.34460908203145, 39.724583197251334))));
    }
}
