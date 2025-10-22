package us.dot.its.jpo.ode.api.controllers.data;

import static org.assertj.core.api.Assertions.assertThat;
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
import us.dot.its.jpo.geojsonconverter.pojos.ssm.ProcessedSsm;
import us.dot.its.jpo.ode.api.accessors.ssm.ProcessedSsmRepository;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.mockdata.MockSsmGenerator;

@SpringBootTest
@RunWith(SpringRunner.class)
@AutoConfigureEmbeddedDatabase
@ActiveProfiles("test")
public class SsmControllerTest {

    private final ProcessedSsmController controller;

    @MockitoBean
    ProcessedSsmRepository processedSsmRepo;

    @MockitoBean
    PermissionService permissionService;

    @Autowired
    public SsmControllerTest(ProcessedSsmController controller) {
        this.controller = controller;
    }

    @Test
    public void testProcessedSsm() {
        ProcessedSsm ssm = MockSsmGenerator.getProcessedSsms().getFirst();

        List<ProcessedSsm> ssms = new ArrayList<>();
        ssms.add(ssm);

        when(permissionService.hasIntersection(ssm.getIntersectionId(), "USER")).thenReturn(true);
        when(permissionService.hasRole("USER")).thenReturn(true);

        PageRequest page = PageRequest.of(1, 1);
        when(processedSsmRepo.find(ssm.getIntersectionId(),
                ssm.getTimeStamp().toEpochSecond() - 1,
                ssm.getTimeStamp().toEpochSecond() + 1, false, PageRequest.of(1, 1)))
                .thenReturn(new PageImpl<>(ssms, page, 1L));

        ResponseEntity<Page<ProcessedSsm>> result = controller
                .findSsms(
                        ssm.getIntersectionId(),
                        ssm.getTimeStamp().toEpochSecond() - 1,
                        ssm.getTimeStamp().toEpochSecond() + 1, false, false, 1, 1, false);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getContent()).isEqualTo(ssms);
    }
}