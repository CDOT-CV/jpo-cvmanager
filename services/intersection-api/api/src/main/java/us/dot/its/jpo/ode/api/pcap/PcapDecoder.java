package us.dot.its.jpo.ode.api.pcap;

import java.util.UUID;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;
import us.dot.its.jpo.ode.api.models.messages.TimestampedHexList;
import java.nio.file.Path;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

@Component
public class PcapDecoder {

    /**
     * Extract timestamps and hex values from tshark json output
     * @param json
     * @return Timestmaped hex
     */
    public TimestampedHexList jsonToTimestampedHexList(String json) {
        var hexList = new TimestampedHexList();
        // TODO
        return hexList;
    }

    /**
     * Use the tshark command line tool to decode pcap bytes to json
     * @param bytes
     * @return json
     * @throws IOException
     */
    public String pcapToJson(byte[] bytes) throws IOException {
        String result = null;
        File tempFile = null;
        try {
            String tempDir = FileUtils.getTempDirectoryPath();
            String tempFileName = "tshark-" + UUID.randomUUID().toString() + ".pcap";
            Path tempFilePath = Path.of(tempDir, tempFileName);
            tempFile = new File(tempFilePath.toString());
            FileUtils.writeByteArrayToFile(tempFile, bytes);

            var pb = new ProcessBuilder(
                "/usr/bin/tshark",
                "-r", tempFile.getAbsolutePath(),
                "-T", "json", "-x");

            pb.directory(new File(tempDir));
            Process process = pb.start();
            result = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }
        return result;
    }
}
