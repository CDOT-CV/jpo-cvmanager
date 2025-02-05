package us.dot.its.jpo.ode.api.asn1;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import us.dot.its.jpo.ode.api.ConflictMonitorApiProperties;
import us.dot.its.jpo.ode.api.models.messages.TimestampedMessageFrameHex;
import us.dot.its.jpo.ode.api.models.messages.TimestampedMessageFrameHexList;

@Service
public class CodecClient {

    private final RestTemplate codecTemplate;

    final static String SINGLE_METHOD = "/j2735/uper/xer";
    final static String BATCH_METHOD = "/batch/j2735/uper/xer";

    private final String decodeSingleUrl;
    private final String decodeBatchUrl;

    @Autowired
    public CodecClient(RestTemplateBuilder builder, ConflictMonitorApiProperties properties) {
        codecTemplate = builder.build();
        String codecBaseUrl = properties.getAsn1CodecBaseUrl();
        decodeSingleUrl = UriComponentsBuilder.fromUriString(codecBaseUrl).fragment(SINGLE_METHOD).build().toUriString();
        decodeBatchUrl = UriComponentsBuilder.fromUriString(codecBaseUrl).fragment(BATCH_METHOD).build().toUriString();
    }

    public String decodeSingle(String hex) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<String> request = new HttpEntity<>(hex, headers);
        return codecTemplate.postForObject(decodeSingleUrl, request, String.class);
    }

    public String decodeBatch(TimestampedMessageFrameHexList messageFrameList) throws JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_NDJSON);
        HttpEntity<String> request = new HttpEntity<>(messageFrameList.toLineDelimitedJson(), headers);
        return codecTemplate.postForObject(decodeBatchUrl, request, String.class);
    }
}
