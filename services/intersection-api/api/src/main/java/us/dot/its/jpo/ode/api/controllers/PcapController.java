package us.dot.its.jpo.ode.api.controllers;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import us.dot.its.jpo.ode.api.asn1.CodecClient;
import us.dot.its.jpo.ode.api.asn1.DecoderManager;
import us.dot.its.jpo.ode.api.models.messages.TimestampedMessageFrameList;
import us.dot.its.jpo.ode.api.pcap.PcapDecoder;

@RestController
@Slf4j
public class PcapController {

  private final PcapDecoder decoder;
  private final DecoderManager decoderManager;
  private final CodecClient codecClient;

  @Autowired
  public PcapController(PcapDecoder decoder, DecoderManager decoderManager,
      CodecClient codecClient) {
    this.decoder = decoder;
    this.decoderManager = decoderManager;
    this.codecClient = codecClient;
  }

  /**
   * Convert standard binary pcap data to JSON array of {@link TimestampedMessageFrameList}. Find
   * and extract PCAP frame timestamps and J2735 MessageFrames.
   *
   * @param bytes Raw PCAP data
   * @return Line delimited JSON. Timestamped message frame data, hex format.
   */
  @RequestMapping(value = "/pcap/uper", method = RequestMethod.POST, consumes = {
      MediaType.APPLICATION_OCTET_STREAM_VALUE,
      "application/pcap",
      "application/vnd.tcpdump.pcap"}, produces = MediaType.APPLICATION_JSON_VALUE)
  public @ResponseBody ResponseEntity<TimestampedMessageFrameList> pcapToTimestampedHex(
      @RequestBody byte[] bytes) throws IOException {
    log.info("pcapToTimestampedHex received {} bytes", bytes.length);
    var hexList = decoder.decodePcap(bytes);
    return ResponseEntity
        .status(HttpStatus.OK)
        .contentType(MediaType.APPLICATION_JSON)
        .body(hexList);
  }

  /**
   * Convert a JSON array of hex MessageFrames data to ProcessedMap and ProcessedSpat JSON, or ODE
   * JSON for other message types.
   * <p>Performs the call the the decoder endpoint asynchronously, without blocking a server
   * thread</p>
   *
   * @param messageFrameList Hex data
   * @return A CompletableFuture that resolves to a JSON array of Processed/ODE JSON.
   */
  @RequestMapping(value = "/uper/json",
      method = RequestMethod.POST,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = {
          MediaType.APPLICATION_JSON_VALUE,
          MediaType.TEXT_PLAIN_VALUE})
  public @ResponseBody CompletableFuture<ResponseEntity<String>> decodeMessageFrames(
      @RequestBody TimestampedMessageFrameList messageFrameList) {

    log.info("decodeMessageFrames received {} messages", messageFrameList.size());

    // Call the Codec API asynchronously, doesn't block server thread.
    return codecClient
        .decodeBatch(messageFrameList)
        .thenApply((xmlBatch) -> {
          String json = decoderManager.convertBatchXmlToOdeJson(xmlBatch, messageFrameList.size());
          return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
        }).exceptionally(ex -> {
          String message = String.format("Failed to decode message frames: %s", ex.getMessage());
          log.error(message, ex);
          return ResponseEntity
              .status(HttpStatus.INTERNAL_SERVER_ERROR)
              .contentType(MediaType.TEXT_PLAIN)
              .body(message + ", " + ExceptionUtils.getStackTrace(ex));
        });
  }

}
