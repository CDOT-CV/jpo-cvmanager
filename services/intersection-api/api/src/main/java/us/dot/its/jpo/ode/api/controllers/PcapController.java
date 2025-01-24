package us.dot.its.jpo.ode.api.controllers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import us.dot.its.jpo.geojsonconverter.DateJsonMapper;
import us.dot.its.jpo.geojsonconverter.converter.spat.SpatProcessedJsonConverter;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.LineString;
import us.dot.its.jpo.geojsonconverter.pojos.geojson.map.ProcessedMap;
import us.dot.its.jpo.geojsonconverter.pojos.spat.ProcessedSpat;
import us.dot.its.jpo.ode.api.asn1.DecoderManager;
import us.dot.its.jpo.ode.api.pcap.PcapDecoder;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

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
    String decodeBatchUrl = "http://172.26.19.45:4000/batch";
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
        value = "/pcap/json", 
        method = RequestMethod.POST,
        consumes = {MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    "application/pcap",
                    "application/vnd.tcpdump.pcap"},
        produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public @ResponseBody ResponseEntity<TimestampedMessageFrameList> pcapToTimestampedHex(
            @RequestBody byte[] bytes) throws IOException {
        log.info("pcapToJson received {}", bytes.length);
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
            log.info("Failed to decode message frames");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.TEXT_PLAIN)
                    .body(ExceptionUtils.getStackTrace(e));
        }
    }

}
