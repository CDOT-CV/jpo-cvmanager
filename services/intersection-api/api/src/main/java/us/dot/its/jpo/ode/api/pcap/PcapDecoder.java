package us.dot.its.jpo.ode.api.pcap;

import us.dot.its.jpo.ode.api.models.messages.TimestampedHexList;

import java.io.IOException;

public interface PcapDecoder {
    TimestampedHexList decodePcap(byte[] bytes) throws IOException;
    String decodeVerbosely(byte[] bytes) throws IOException;
}
