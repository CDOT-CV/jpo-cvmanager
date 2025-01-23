package us.dot.its.jpo.ode.api.pcap;

import io.kaitai.struct.ByteBufferKaitaiStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import us.dot.its.jpo.ode.api.models.messages.TimestampedMessageFrame;
import us.dot.its.jpo.ode.api.models.messages.TimestampedMessageFrameList;
import us.dot.its.jpo.ode.api.pcap.kaitai.Pcap;

import java.io.IOException;

@Component
@Slf4j
public class PcapDecoder  {

    public TimestampedMessageFrameList decodePcap(byte[] bytes) throws IOException {
        log.info("decodePcap received {} bytes", bytes.length);
        var hexList = new TimestampedMessageFrameList();
        try (var byteStream = new ByteBufferKaitaiStream(bytes)) {
            Pcap pcap = new Pcap(byteStream);
            log.info("Pcap decoded {} packets", pcap.packets().size());
            Pcap.Header h = pcap.hdr();
            log.info("Pcap header: version: {}.{}, time zone: {}, snaplen: {}, network: {}",
                    h.versionMajor(), h.versionMinor(), h.thiszone(), h.snaplen(), h.network());
            for (Pcap.Packet packet : pcap.packets()) {
                byte[] packetBytes = packet._raw_body() != null ? packet._raw_body() : (byte[])packet.body();
                var hex = new TimestampedMessageFrame();
                double seconds = packet.tsSec() + packet.tsUsec()/1e6d;
                long timestamp = (long)(seconds * 1000);
                hex.setTimestamp(timestamp);
                hex.setMessageFrame(packetBytes);
                hexList.add(hex);
            }
        }
        log.info("finished decodePcap");
        return hexList;
    }
}
