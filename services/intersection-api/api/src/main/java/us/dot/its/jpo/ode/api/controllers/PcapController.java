package us.dot.its.jpo.ode.api.controllers;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import us.dot.its.jpo.geojsonconverter.DateJsonMapper;
import us.dot.its.jpo.geojsonconverter.converter.spat.SpatProcessedJsonConverter;
import us.dot.its.jpo.ode.api.asn1.DecoderManager;
import us.dot.its.jpo.ode.api.models.messages.TimestampedMessageFrameHexList;
import us.dot.its.jpo.ode.api.pcap.PcapDecoder;


import java.io.IOException;
import java.util.Formatter;
import java.util.List;
import java.util.Optional;

import us.dot.its.jpo.ode.api.models.messages.TimestampedMessageFrameList;

import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.model.*;

@RestController
@Slf4j
public class PcapController {

    @Autowired
    PcapDecoder decoder;

    @Autowired
    DecoderManager decoderManager;

    RestTemplate codecTemplate = new RestTemplate();
    String decodeBatchUrl = "http://172.26.19.45:4000/batch/uper/b64/xer";
    SpatProcessedJsonConverter spatConverter = new SpatProcessedJsonConverter();

    /**
     * Convert pcap data to a {@link TimestampedMessageFrameList}.
     * Attempts to extract UDP or unsecured WAVE payloads.
     * If this method fails, try the verbose-json endpoint to retrieve the detailed wireshark json.
     * @param bytes PCAP data
     * @return JSON array of Timestamped Hex data 
     * @throws IOException
     */
    @RequestMapping(
        value = "/pcap/uper/b64",
        method = RequestMethod.POST,
        consumes = {MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    "application/pcap",
                    "application/vnd.tcpdump.pcap"},
        produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody ResponseEntity<TimestampedMessageFrameList> pcapToTimestampedBase64(
            @RequestBody byte[] bytes) throws IOException {
        log.info("pcapToTimestampedBase64 received {}", bytes.length);
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(decoder.decodePcap(bytes));
        } catch (Exception ex) {
            log.error("Exception in /pcap/json", ex);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TimestampedMessageFrameList());
        }
    }

    @RequestMapping(
            value = "/pcap/uper/hex",
            method = RequestMethod.POST,
            consumes = {MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    "application/pcap",
                    "application/vnd.tcpdump.pcap"},
            produces = {MediaType.APPLICATION_JSON_VALUE,
                        MediaType.TEXT_PLAIN_VALUE})
    public @ResponseBody ResponseEntity<String> pcapToTimestampedHex(
            @RequestBody byte[] bytes,
            @RequestParam Optional<String> text) throws IOException {
        log.info("pcapToTimestampedHex received {}", bytes.length);
        try {
            var hexList = new TimestampedMessageFrameHexList(decoder.decodePcap(bytes));
            if (text.isPresent()) {
                // Send hex as plain line-delimited text
                Formatter lines = new Formatter();
                for (var hex : hexList) {
                    lines.format("%s%n", hex.getHex());
                }
                return ResponseEntity
                        .status(HttpStatus.OK)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(lines.toString());
            } else {
                // Send TimestmapedMessageFrameHexList as JSON
                return ResponseEntity
                        .status(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(DateJsonMapper.getInstance().writeValueAsString(hexList));
            }
        } catch (Exception ex) {
            log.error("Exception in /pcap/json", ex);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(ExceptionUtils.getStackTrace(ex));
        }
    }

    @RequestMapping(
            value = "/pcap/decode",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public @ResponseBody ResponseEntity<String> decodeMessageFrames(
            @RequestBody TimestampedMessageFrameList messageFrameList) {
        log.info("decodeMessageFrames received {} messages", messageFrameList.size());
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<TimestampedMessageFrameList> request = new HttpEntity<>(messageFrameList, headers);
            String xmlBatch = codecTemplate.postForObject(decodeBatchUrl, request, String.class);
            log.info("Received xmlBatch of {} chars", xmlBatch != null ? xmlBatch.length() : 0);
            List<OdeData> odeDataList = decoderManager.convertBatchXmlToOdeData(xmlBatch);
            List<String> decodedMessages = decoderManager.convertBatchOdeDataToJson(odeDataList);
            String json = "[" + String.join(",", decodedMessages) + "]";
            log.info("Finished converting ode Json to {} processed json items", decodedMessages.size());
            if (decodedMessages.size() < messageFrameList.size()) {
                log.error("{} items were dropped due to errors or unknown message types while converting message frames to json",
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
