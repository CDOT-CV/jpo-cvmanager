package us.dot.its.jpo.ode.api.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import us.dot.its.jpo.geojsonconverter.DateJsonMapper;
import us.dot.its.jpo.ode.api.asn1.CodecClient;
import us.dot.its.jpo.ode.api.asn1.DecoderManager;
import us.dot.its.jpo.ode.api.models.messages.*;
import us.dot.its.jpo.ode.api.pcap.PcapDecoder;

import java.io.IOException;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.model.*;
import java.util.stream.Collectors;

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
     * Convert standard binary pcap data to JSON of
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
     * 
     * @param messageFrameList Hex data
     * @return A JSON array of Processed/ODE JSON.
     */
    @RequestMapping(value = "/uper/json", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.TEXT_PLAIN_VALUE })
    public @ResponseBody ResponseEntity<String> decodeMessageFrames(
            @RequestBody TimestampedMessageFrameList messageFrameList) {
        log.info("decodeMessageFrames received {} messages", messageFrameList.size());
        try {
            // Filter out unknown messages
            List<TimestampedMessageFrame> filtered = messageFrameList.stream()
                    .filter(tmf -> tmf.getMessageFrameType() != MessageType.UNKNOWN)
                    .collect(Collectors.toList());
            var filteredList = new TimestampedMessageFrameList();
            filteredList.addAll(filtered);
            if (filteredList.size() < messageFrameList.size()) {
                log.warn("Filtered out {} unknown messages", messageFrameList.size() - filteredList.size());
            }
            String xmlBatch = codecClient.decodeBatch(filteredList);
            log.info("Received xmlBatch of {} chars", xmlBatch != null ? xmlBatch.length() : 0);
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
        } catch (Exception e) {
            String message = String.format("Failed to decode message frames: %s", e.getMessage());
            log.error(message, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.TEXT_PLAIN)
                    .body(message + ", " + ExceptionUtils.getStackTrace(e));
        }
    }

}
