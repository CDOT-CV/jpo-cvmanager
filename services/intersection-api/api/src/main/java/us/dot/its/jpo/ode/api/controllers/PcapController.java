package us.dot.its.jpo.ode.api.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import us.dot.its.jpo.ode.api.pcap.PcapDecoder;

import java.io.IOException;
import java.time.ZonedDateTime;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.dot.its.jpo.ode.api.models.MessageType;
import us.dot.its.jpo.ode.api.models.messages.DecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.EncodedMessage;
import us.dot.its.jpo.ode.mockdata.MockDecodedMessageGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import us.dot.its.jpo.ode.api.ConflictMonitorApiProperties;
import us.dot.its.jpo.ode.api.asn1.DecoderManager;

import us.dot.its.jpo.ode.api.models.messages.TimestampedHexList;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class PcapController {

    @Autowired
    PcapDecoder decoder;

    /**
     * Converts pcap data to Wireshark's JSON format
     * @param bytes PCAP data
     * @return JSON data output by Wireshark
     * @throws IOException
     */
    @RequestMapping(
        value = "/pcap/verbose-json", 
        method = RequestMethod.POST,
        consumes = {MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    "application/pcap",
                    "application/vnd.tcpdump.pcap"},
        produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody ResponseEntity<String> pcapToJson(@RequestBody byte[] bytes) throws IOException {
            return ResponseEntity
                .status(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(decoder.pcapToJson(bytes));

    }

    /**
     * Convert pcap data to CSV.
     * Attempts to extract UDP or unsecured WAVE payloads.
     * If this fails, try the verbose-json endpoint to retrieve raw wireshark json.
     * @param bytes PCAP data
     * @return JSON array of Timestamped Hex data 
     * @throws IOException
     */
    @RequestMapping(
        value = "/pcap/csv", 
        method = RequestMethod.POST,
        consumes = {MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    "application/pcap",
                    "application/vnd.tcpdump.pcap"},
        produces = MediaType.TEXT_PLAIN_VALUE)
    public @ResponseBody ResponseEntity<String> pcapToTimestampedCsv(@RequestBody byte[] bytes) throws IOException {
        return ResponseEntity
            .status(HttpStatus.OK)
            .contentType(MediaType.TEXT_PLAIN)
            .body(decoder.pcapToCsv(bytes));
    }

    /**
     * Convert pcap data to a {@link TimestampedHexList}.
     * Attempts to extract UDP or unsecured WAVE payloads.
     * If this fails, try the verbose-json endpoint to retrieve the raw wireshark json.
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
        produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody ResponseEntity<TimestampedHexList> pcapToTimestampedHex(@RequestBody byte[] bytes) throws IOException {
        return ResponseEntity
            .status(HttpStatus.OK)
            .contentType(MediaType.APPLICATION_JSON)
            .body(decoder.csvToTimestampedHexList(decoder.pcapToCsv(bytes)));
    }



}
