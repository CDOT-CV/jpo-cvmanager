package us.dot.its.jpo.ode.api.controllers.advice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@WebMvcTest(controllers = UnmatchedEndpointTest.StubController.class)
@ContextConfiguration(classes = { UnmatchedEndpointTest.StubController.class, GlobalExceptionHandler.class,
        UnmatchedEndpointTest.MvcConfiguration.class })
@AutoConfigureMockMvc(addFilters = false)
class UnmatchedEndpointTest {

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class MvcConfiguration {
    }

    @RestController
    static class StubController {
        @GetMapping("/exists")
        String ok() {
            return "ok";
        }

        @GetMapping("/missing-handler")
        void missingHandler() throws NoHandlerFoundException {
            throw new NoHandlerFoundException("GET", "/missing-handler", new HttpHeaders());
        }

        @GetMapping("/missing-resource")
        void missingResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/missing-resource", "missing-resource");
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

    @Test
    void missingResourceWithImageAcceptReturnsEmpty404() throws Exception {
        mockMvc.perform(get("/favicon.ico").accept("image/avif,image/webp,image/*"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.ACCEPT))
                .andExpect(content().string(emptyOrNullString()));
    }

    @Test
    void browserWildcardAcceptsProblemDetail() throws Exception {
        mockMvc.perform(get("/favicon.ico").accept("image/avif,image/webp,image/*,*/*;q=0.8"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void missingHandlerWithImageAcceptReturnsEmpty404() throws Exception {
        mockMvc.perform(get("/missing-handler").accept("image/avif,image/webp,image/*"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(emptyOrNullString()));
    }

    @Test
    void missingHandlerWithJsonAcceptReturnsProblem() throws Exception {
        mockMvc.perform(get("/missing-handler").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void jsonExplicitlyRejectedReturnsEmpty404() throws Exception {
        mockMvc.perform(get("/favicon.ico")
                .header(HttpHeaders.ACCEPT, "application/json;q=0, application/problem+json;q=0, */*;q=1"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(emptyOrNullString()));
    }

    @Test
    void missingResourceWithInvalidAcceptReturnsEmpty404() throws Exception {
        mockMvc.perform(get("/favicon.ico").header(HttpHeaders.ACCEPT, "invalid"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(emptyOrNullString()));
    }

    @Test
    void missingResourceOnHeadHasNoBody() throws Exception {
        mockMvc.perform(head("/favicon.ico").accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string(emptyOrNullString()));
    }

    @Test
    void missingEndpointsWorkWithoutStringOrJsonConverters() throws Exception {
        MockMvc withoutStringOrJsonConverters = standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new ByteArrayHttpMessageConverter())
                .build();

        for (String path : new String[] { "/missing-handler", "/missing-resource" }) {
            for (MediaType mediaType : new MediaType[] {
                    MediaType.APPLICATION_PROBLEM_JSON, MediaType.APPLICATION_JSON }) {
                withoutStringOrJsonConverters.perform(get(path).accept(mediaType))
                        .andExpect(status().isNotFound())
                        .andExpect(content().contentTypeCompatibleWith(mediaType))
                        .andExpect(jsonPath("$.type").value("about:blank"))
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.instance").value(path));
            }
        }
    }
}