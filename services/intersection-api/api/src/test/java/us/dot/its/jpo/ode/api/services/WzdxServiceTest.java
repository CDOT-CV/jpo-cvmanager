package us.dot.its.jpo.ode.api.services;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.wzdx.WzdxFeedProperties;

@ExtendWith(MockitoExtension.class)
class WzdxServiceTest {

    @Mock
    private WzdxFeedProperties properties;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private WzdxService wzdxService;

    @Test
    void callWzdxApi_buildsUrlAndReturnsBody() {
        when(properties.getBaseUrl()).thenReturn("https://data.cotrip.org");
        when(properties.getApiKey()).thenReturn("test-api-key");

        String feed = "{\"type\":\"FeatureCollection\",\"features\":[]}";
        when(restTemplate.getForEntity(any(URI.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(feed, HttpStatus.OK));

        String result = wzdxService.callWzdxApi();

        assertEquals(feed, result);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForEntity(uriCaptor.capture(), eq(String.class));
        assertEquals("https://data.cotrip.org/api/v1/wzdx?apiKey=test-api-key",
                uriCaptor.getValue().toString());
    }

    @Test
    void callWzdxApi_wrapsUpstreamFailureAsBadGateway() {
        when(properties.getBaseUrl()).thenReturn("https://data.cotrip.org");
        when(properties.getApiKey()).thenReturn("test-api-key");

        when(restTemplate.getForEntity(any(URI.class), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> wzdxService.callWzdxApi());
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }
}
