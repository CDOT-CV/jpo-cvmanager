package us.dot.its.jpo.ode.api.asn1;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import us.dot.its.jpo.ode.api.ConflictMonitorApiProperties;
import us.dot.its.jpo.ode.api.models.messages.TimestampedMessageFrameList;

import java.util.concurrent.*;

@Slf4j
@Service
public class CodecClient {

    private final RestTemplate codecTemplate;

    final static String SINGLE_METHOD = "/j2735/uper/xer";
    final static String BATCH_METHOD = "/batch/j2735/uper/xer";


    private final String decodeSingleUrl;
    private final String decodeBatchUrl;
    private final Executor executor;

    // TODO Config setting
    final int numberOfThreads = 5;

    @Bean
    public Executor codecClientExecutor() {
        return Executors.newFixedThreadPool(numberOfThreads);
    }

    @Autowired
    public CodecClient(RestTemplateBuilder builder, ConflictMonitorApiProperties properties,
                       @Qualifier("codecClientExecutor") Executor executorBean) {
        codecTemplate = builder.build();
        String codecBaseUrl = properties.getAsn1CodecBaseUrl();
        decodeSingleUrl = UriComponentsBuilder.fromUriString(codecBaseUrl).path(SINGLE_METHOD).build().toUriString();
        decodeBatchUrl = UriComponentsBuilder.fromUriString(codecBaseUrl).path(BATCH_METHOD).build().toUriString();
        executor = executorBean;
        log.info("Created CodecClient service.  decodeSingleUrl = {}, decodeBatchUrl = {}, " +
                "thread pool with {} threads", decodeSingleUrl, decodeBatchUrl, numberOfThreads);
    }

    /**
     * Call the ans1_codec HTTP endpoint to decode a single ASN.1 UPER/hex MessageFrame.
     * <p>This is a non-blocking, asynchronous call</p>
     * @param hex an UPER/hex encoded J2735 MessageFrame
     * @return A CompletableFuture that resolves to the decoded XER MessageFrame
     */
    public CompletableFuture<String> decodeSingle(String hex) {
        log.info("decodeSingle: {}", hex);
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        final HttpEntity<String> request = new HttpEntity<>(hex, headers);
        return CompletableFuture.supplyAsync(
                () -> codecTemplate.postForObject(decodeSingleUrl, request, String.class),
                executor
                )
                .orTimeout(10, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    if (ex instanceof TimeoutException) {
                        log.error("The call to {} exceeded the configured timeout", decodeSingleUrl);
                    }
                    return null;
                });
    }

    /**
     * Call the asn1_codec HTTP endpoint to decode a batch of ASN.1 UPER/hex MessageFrames
     * <p>This is a non-blocking, asynchronous call</p>
     * @param messageFrameList List of MessageFrame with metadata
     * @return Completable Future that resolves to Line-delimited XER MessageFrames and metadata
     * @throws JsonProcessingException JSON exception
     */
    public CompletableFuture<String> decodeBatch(TimestampedMessageFrameList messageFrameList) throws JsonProcessingException {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_NDJSON);
        final HttpEntity<String> request = new HttpEntity<>(messageFrameList.toLineDelimitedJson(), headers);
        return CompletableFuture.supplyAsync(
                () -> codecTemplate.postForObject(decodeBatchUrl, request, String.class),
                    executor
                )
                .orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    if (ex instanceof TimeoutException) {
                        log.error("The call to {} exceeded the configured timeout", decodeBatchUrl);
                    }
                    return null;
                });
    }
}
