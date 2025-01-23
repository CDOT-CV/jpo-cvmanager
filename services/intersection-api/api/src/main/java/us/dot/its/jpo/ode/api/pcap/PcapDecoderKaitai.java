package us.dot.its.jpo.ode.api.pcap;

import io.kaitai.struct.ByteBufferKaitaiStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import us.dot.its.jpo.ode.api.models.messages.TimestampedHex;
import us.dot.its.jpo.ode.api.models.messages.TimestampedHexList;
import us.dot.its.jpo.ode.api.pcap.kaitai.Pcap;

import java.io.IOException;

@Component("kaitaiPcapDecoder")
@Slf4j
public class PcapDecoderKaitai implements PcapDecoder {
    @Override
    public TimestampedHexList decodePcap(byte[] bytes) throws IOException {
        log.info("decodePcap received {} bytes", bytes.length);
        var hexList = new TimestampedHexList();
        try (var byteStream = new ByteBufferKaitaiStream(bytes)) {
            Pcap pcap = new Pcap(byteStream);
            log.info("Pcap decoded {} packets", pcap.packets().size());
            log.info("Pcap header: {}", pcap.hdr());
            for (Pcap.Packet packet : pcap.packets()) {
                //byte[] packetBytes = packet._raw_body();
                byte[] packetBytes = packet._raw_body() != null ? packet._raw_body() : (byte[])packet.body();
                log.info("packet has {} bytes, inclLen: {}, origLen: {}, tsUsec: {}",
                        packetBytes.length,
                        packet.inclLen(), packet.origLen(), packet.tsUsec());

                var hex = new TimestampedHex();
                //hex.setPath("_raw_body");
                hex.setTimestamp(packet.tsUsec());
                hex.setMessageFrame(packetBytes);
                hexList.add(hex);
            }
        }
        log.info("finished decodePcap");
        return hexList;
    }

    @Override
    public String decodeVerbosely(byte[] bytes) throws IOException {
        return "";
    }
}
