package us.dot.its.jpo.ode.api.controllers.devices.rsus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.models.devices.RsuGeoQueryRequest;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.services.RsuGeoQueryService;

@ExtendWith(MockitoExtension.class)
class RsuGeoQueryControllerTest {

    private static final String ORGANIZATION = "TestOrg";
    private static final String VENDOR = "Commsignia";
    private static final String FOUR_POINT_POLYGON = "POLYGON((0.0 0.0,1.0 0.0,0.0 1.0,0.0 0.0))";

    @Mock
    private RsuRepository rsuRepository;

    private RsuGeoQueryController controller;

    @BeforeEach
    void setUp() {
        controller = new RsuGeoQueryController(new RsuGeoQueryService(rsuRepository));
    }

    @Test
    void queryRsusByGeometry_rejectsTwoPointRing() {
        RsuGeoQueryRequest body = new RsuGeoQueryRequest();
        body.setGeometry(List.of(
                List.of(-105.34460908203145, 39.724583197251334),
                List.of(-105.34666901855489, 39.670180083300174)));
        body.setVendor(VENDOR);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.queryRsusByGeometry(ORGANIZATION, body));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Polygon ring must have at least 4 positions", exception.getReason());
        verifyNoInteractions(rsuRepository);
    }

    @Test
    void queryRsusByGeometry_returnsIpsForClosedFourPointRing() {
        List<List<Double>> geometry = List.of(
                List.of(0.0, 0.0),
                List.of(1.0, 0.0),
                List.of(0.0, 1.0),
                List.of(0.0, 0.0));
        RsuGeoQueryRequest body = new RsuGeoQueryRequest();
        body.setGeometry(geometry);
        body.setVendor(VENDOR);
        when(rsuRepository.findIpv4AddressesInPolygonByManufacturer(ORGANIZATION, FOUR_POINT_POLYGON, VENDOR))
                .thenReturn(List.of("10.0.0.1", "10.0.0.2"));

        ResponseEntity<List<String>> response = controller.queryRsusByGeometry(ORGANIZATION, body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("10.0.0.1", "10.0.0.2"), response.getBody());
        verify(rsuRepository).findIpv4AddressesInPolygonByManufacturer(ORGANIZATION, FOUR_POINT_POLYGON, VENDOR);
    }
}
