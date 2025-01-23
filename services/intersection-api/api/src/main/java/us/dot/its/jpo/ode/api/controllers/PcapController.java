package us.dot.its.jpo.ode.api.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RestController;
import us.dot.its.jpo.ode.api.pcap.PcapDecoder;


import java.io.IOException;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import us.dot.its.jpo.ode.api.models.messages.TimestampedHexList;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class PcapController {

    @Autowired
    @Qualifier("kaitaiPcapDecoder")
    PcapDecoder decoder;

    /**
     * Converts pcap data to Wireshark's JSON format with full details.
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
    public @ResponseBody ResponseEntity<String> pcapToJson(
            @RequestBody byte[] bytes) throws IOException {
        log.info("pcapToVerboseJson");
        return ResponseEntity
            .status(HttpStatus.OK)
            .contentType(MediaType.APPLICATION_JSON)
            .body(decoder.decodeVerbosely(bytes));
    }

    /**
     * Convert pcap data to a {@link TimestampedHexList}.
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
    public @ResponseBody ResponseEntity<TimestampedHexList> pcapToTimestampedHex(
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
                    .body(new TimestampedHexList());
        }
    }



}
