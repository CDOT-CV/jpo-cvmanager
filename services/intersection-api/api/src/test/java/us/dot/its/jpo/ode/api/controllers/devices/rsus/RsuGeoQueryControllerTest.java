package us.dot.its.jpo.ode.api.controllers.devices.rsus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import us.dot.its.jpo.ode.api.models.devices.RsuGeoQueryRequest;
import us.dot.its.jpo.ode.api.services.RsuGeoQueryService;

@ExtendWith(MockitoExtension.class)
class RsuGeoQueryControllerTest {

    @Mock
    private RsuGeoQueryService rsuGeoQueryService;

    @InjectMocks
    private RsuGeoQueryController controller;

    @Test
    void queryRsusByGeometry_returnsIpListFromService() {
        List<List<Double>> geometry = List.of(
                List.of(-105.34460908203145, 39.724583197251334),
                List.of(-105.34666901855489, 39.670180083300174));
        RsuGeoQueryRequest body = new RsuGeoQueryRequest();
        body.setGeometry(geometry);
        body.setVendor("Commsignia");
        when(rsuGeoQueryService.findRsuIps("TestOrg", geometry, "Commsignia"))
                .thenReturn(List.of("10.0.0.1", "10.0.0.2"));

        ResponseEntity<List<String>> response = controller.queryRsusByGeometry("TestOrg", body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("10.0.0.1", "10.0.0.2"), response.getBody());
        verify(rsuGeoQueryService).findRsuIps("TestOrg", geometry, "Commsignia");
    }
}
