package us.dot.its.jpo.ode.api.controllers.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.srm.ProcessedSrm;
import us.dot.its.jpo.ode.api.accessors.srm.ProcessedSrmRepository;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.mockdata.MockSrmGenerator;

@SpringBootTest
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase
@ActiveProfiles("test")
public class SrmControllerTest {

    private final ProcessedSrmController controller;

    @MockitoBean
    ProcessedSrmRepository processedSrmRepo;

    @MockitoBean
    PermissionService permissionService;

    @Autowired
    public SrmControllerTest(ProcessedSrmController controller) {
        this.controller = controller;
    }

    @Test
    public void testProcessedSrm() {
        ProcessedSrm srm = MockSrmGenerator.getProcessedSrms().getFirst();

        List<ProcessedSrm> srms = new ArrayList<>();
        srms.add(srm);

        when(permissionService.hasIntersection(srm.getProperties().getRequests().getFirst().getIntersectionId(),
                "USER")).thenReturn(true);
        when(permissionService.hasRole("USER")).thenReturn(true);

        PageRequest page = PageRequest.of(1, 1);
        when(processedSrmRepo.find(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
                .thenReturn(new PageImpl<>(srms, page, 1L));

        ResponseEntity<Page<ProcessedSrm>> result = controller.findProcessedSRMs(
                srm.getProperties().getRequests().getFirst().getIntersectionId(),
                srm.getProperties().getOriginIp(),
                srm.getProperties().getVehicleID(),
                srm.getProperties().getTimeStamp().toEpochSecond() - 1,
                srm.getProperties().getTimeStamp().toEpochSecond() + 1,
                srm.getGeometry().getCoordinates()[0],
                srm.getGeometry().getCoordinates()[1],
                1.0,
                0,
                1,
                false);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getContent()).isEqualTo(srms);
    }
}