package us.dot.its.jpo.ode.api.asn1;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import us.dot.its.jpo.ode.api.ConflictMonitorApiProperties;
import us.dot.its.jpo.ode.api.models.messages.MessageType;
import us.dot.its.jpo.ode.api.models.messages.TimestampedMessageFrame;
import us.dot.its.jpo.ode.api.models.messages.TimestampedMessageFrameList;

@Slf4j
@Service
public class CodecClient {

  private final RestTemplate codecTemplate;

  final static String SINGLE_METHOD = "/j2735/uper/xer";
  final static String BATCH_METHOD = "/batch/j2735/uper/xer";

  // TODO Don't hardcode
  final int numberOfThreads = 5;


  private final String decodeSingleUrl;
  private final String decodeBatchUrl;
  private final Executor executor;

  @Autowired
  public CodecClient(RestTemplateBuilder builder, ConflictMonitorApiProperties properties) {
    codecTemplate = builder.build();
    String codecBaseUrl = properties.getAsn1CodecBaseUrl();
    decodeSingleUrl = UriComponentsBuilder.fromUriString(codecBaseUrl).path(SINGLE_METHOD).build()
        .toUriString();
    decodeBatchUrl = UriComponentsBuilder.fromUriString(codecBaseUrl).path(BATCH_METHOD).build()
        .toUriString();
    executor = Executors.newFixedThreadPool(numberOfThreads);

    log.info(
        "Created CodecClient service.  decodeSingleUrl = {}, decodeBatchUrl = {}, executor with {} threads",
        decodeSingleUrl, decodeBatchUrl, numberOfThreads);
  }

  /**
   * Call the ans1_codec HTTP endpoint to decode a single ASN.1 UPER/hex MessageFrame.
   * <p>This is a non-blocking, asynchronous call</p>
   *
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
          } else {
            log.error("Error sending batch to asn1_codec api", ex);
          }
          return null;
        });
  }

  /**
   * Call the asn1_codec HTTP endpoint to decode a batch of ASN.1 UPER/hex MessageFrames
   * <p>This is a non-blocking, asynchronous call</p>
   *
   * @param messageFrameList List of MessageFrames with metadata
   * @return Completable Future that resolves to Line-delimited XER MessageFrames and metadata
   * @throws JsonProcessingException JSON exception
   */
  public CompletableFuture<String> decodeBatch(TimestampedMessageFrameList messageFrameList) {

    return CompletableFuture.supplyAsync(
            () -> {
              // Filter out unknown messages
              List<TimestampedMessageFrame> filtered = messageFrameList.stream()
                  .filter(tmf -> tmf.getMessageFrameType() != MessageType.UNKNOWN)
                  .toList();
              var filteredList = new TimestampedMessageFrameList();
              filteredList.addAll(filtered);
              if (filteredList.size() < messageFrameList.size()) {
                log.warn("Filtered out {} unknown messages",
                    messageFrameList.size() - filteredList.size());
              }
              final HttpHeaders headers = new HttpHeaders();
              headers.setContentType(MediaType.APPLICATION_NDJSON);
              final HttpEntity<String> request;
              try {
                request = new HttpEntity<>(filteredList.toLineDelimitedJson(), headers);
              } catch (JsonProcessingException e) {
                throw new RuntimeException("JsonProcessingException serializing filtered list", e);
              }

              // Call the asn1_codec API
              return codecTemplate.postForObject(decodeBatchUrl, request, String.class);
            },
            executor
        )
        .orTimeout(30, TimeUnit.SECONDS)
        .exceptionally(ex -> {
          if (ex instanceof TimeoutException) {
            log.error("The call to {} exceeded the configured timeout", decodeBatchUrl);
          } else {
            log.error("Error sending batch to asn1_codec api", ex);
          }
          return null;
        });
  }
}
