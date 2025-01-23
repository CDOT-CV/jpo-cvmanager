package us.dot.its.jpo.ode.api.controllers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RestController;
import us.dot.its.jpo.geojsonconverter.DateJsonMapper;
import us.dot.its.jpo.ode.api.asn1.DecoderManager;
import us.dot.its.jpo.ode.api.models.messages.DecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.EncodedMessage;
import us.dot.its.jpo.ode.api.models.messages.TimestampedMessageFrame;
import us.dot.its.jpo.ode.api.pcap.PcapDecoder;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import us.dot.its.jpo.ode.api.models.messages.TimestampedMessageFrameList;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class PcapController {

    @Autowired
    PcapDecoder decoder;

    @Autowired
    DecoderManager decoderManager;

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
            List<String> decodedMessages = new ArrayList<>();
            for (TimestampedMessageFrame messageFrame : messageFrameList) {
                EncodedMessage encodedMessage = new EncodedMessage();
                encodedMessage.setType(messageFrame.getMessageFrameType());
                encodedMessage.setAsn1Message(messageFrame.getMessageFrameHex());
                DecodedMessage decodedMessage = decoderManager.decode(encodedMessage);
                decodedMessages.add(decodedMessage.toString());
            }
            String json = "[" + String.join(",", decodedMessages) + "]";
            log.info("finished decoding messages");
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
        } catch (Exception e) {
            log.info("Failed to decode message frames");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.TEXT_PLAIN)
                    .body(ExceptionUtils.getStackTrace(e));
        }
    }

    public ObjectMapper defaultMapper() {
        ObjectMapper objectMapper = DateJsonMapper.getInstance();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return objectMapper;
    }

}
