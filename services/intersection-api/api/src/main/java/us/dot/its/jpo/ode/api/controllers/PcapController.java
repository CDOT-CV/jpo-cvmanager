package us.dot.its.jpo.ode.api.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import us.dot.its.jpo.ode.api.asn1.CodecClient;
import us.dot.its.jpo.ode.api.asn1.DecoderManager;
import us.dot.its.jpo.ode.api.models.messages.*;
import us.dot.its.jpo.ode.api.pcap.PcapDecoder;

import java.io.IOException;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.model.*;

import java.util.concurrent.CompletableFuture;

@RestController
@Slf4j
public class PcapController {

    private final PcapDecoder decoder;
    private final DecoderManager decoderManager;
    private final CodecClient codecClient;

    @Autowired
    public PcapController(PcapDecoder decoder, DecoderManager decoderManager, CodecClient codecClient) {
        this.decoder = decoder;
        this.decoderManager = decoderManager;
        this.codecClient = codecClient;
    }

    /**
     * Convert standard binary pcap data to JSON array of
     * {@link TimestampedMessageFrameList}.
     * Find and extract PCAP frame timestamps and J2735 MessageFrames.
     *
     * @param bytes Raw PCAP data
     * @return Line delimited JSON. Timestamped message frame data, hex format.
     */
    @RequestMapping(value = "/pcap/uper", method = RequestMethod.POST, consumes = {
            MediaType.APPLICATION_OCTET_STREAM_VALUE,
            "application/pcap",
            "application/vnd.tcpdump.pcap" }, produces = MediaType.APPLICATION_JSON_VALUE)
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
     * Convert a JSON array of hex MessageFrames data to ProcessedMap and
     * ProcessedSpat
     * JSON, or ODE JSON for other message types.
     * <p>Performs the call the the decoder endpoint asynchronously, without blocking a server thread</p>
     * @param messageFrameList Hex data
     * @return A CompletableFuture that resolves to a JSON array of Processed/ODE JSON.
     */
    @RequestMapping(value = "/uper/json", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.TEXT_PLAIN_VALUE })
    public @ResponseBody CompletableFuture<ResponseEntity<String>> decodeMessageFrames(
            @RequestBody TimestampedMessageFrameList messageFrameList) {
        log.info("decodeMessageFrames received {} messages", messageFrameList.size());

        CompletableFuture<String> xmlBatchFuture = null;
        try {
            // Filter out unknown messages
            List<TimestampedMessageFrame> filtered = messageFrameList.stream()
                    .filter(tmf -> tmf.getMessageFrameType() != MessageType.UNKNOWN)
                    .toList();
            var filteredList = new TimestampedMessageFrameList();
            filteredList.addAll(filtered);
            if (filteredList.size() < messageFrameList.size()) {
                log.warn("Filtered out {} unknown messages", messageFrameList.size() - filteredList.size());
            }

            // Call the Codec API asynchronously, doesn't block server thread.
            xmlBatchFuture = codecClient.decodeBatch(filteredList);

        } catch (JsonProcessingException e) {
            String message = String.format("Json processing exception: %s", e.getMessage());
            log.error(message, e);
            return CompletableFuture.completedFuture(
                    ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(message + ", " + ExceptionUtils.getStackTrace(e)));
        }

        return xmlBatchFuture.thenApply((xmlBatch) -> {



            log.info("Received xmlBatch of {} chars", xmlBatch != null ? xmlBatch.length() : 0);
            if (xmlBatch == null) {
                log.error("The batch result is empty maybe because the codec call timed out");
                return ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("Codec returned empty batch XML or timed out");
            }
            List<OdeData> odeDataList = decoderManager.convertBatchXmlToOdeData(xmlBatch);
            List<String> decodedMessages = decoderManager.convertBatchOdeDataToJson(odeDataList);
            String json = "[" + String.join(",", decodedMessages) + "]";
            log.info("Finished converting ode Json to {} processed json items", decodedMessages.size());
            if (decodedMessages.size() < messageFrameList.size()) {
                log.error(
                        "{} items were dropped due to errors or unknown message types while converting message frames to json",
                        messageFrameList.size() - decodedMessages.size());
            }
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
