package us.dot.its.jpo.ode.api.controllers.advice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UnmatchedEndpointTest.StubController.class)
@ContextConfiguration(classes = { UnmatchedEndpointTest.StubController.class, GlobalExceptionHandler.class })
@AutoConfigureMockMvc(addFilters = false)
class UnmatchedEndpointTest {

    @RestController
    static class StubController {
        @GetMapping("/exists")
        String ok() {
            return "ok";
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void unknownPathReturns404ProblemDetail() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No endpoint GET /does-not-exist."));
    }
}