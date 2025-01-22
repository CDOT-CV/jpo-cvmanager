package us.dot.its.jpo.ode.api.pcap;


import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import us.dot.its.jpo.ode.api.models.messages.TimestampedHex;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.containsString;

@Slf4j
public class PcapDecoderTests {

    @Test
    public void testParsePcapFrame_UDP() {
        var pcapDecoder = new PcapDecoder();
        Optional<TimestampedHex> optHex = pcapDecoder.parsePcapFrame(UDP_FRAME);

        assertThat(optHex.isPresent(), equalTo(true));
        TimestampedHex tsHex = optHex.get();
        assertThat(tsHex.getTimestamp(), equalTo(1683155397596L));
        assertThat(tsHex.getHexMessage(), startsWith("0103002080c003810"));
        assertThat(tsHex.getPath(), containsString("udp.payload_raw"));
        log.info("{}", tsHex);
    }

    @Test
    public void testParsePcapFrame_WSMP() {
        var pcapDecoder = new PcapDecoder();
        Optional<TimestampedHex> optHex = pcapDecoder.parsePcapFrame(WSMP_FRAME);

        assertThat(optHex.isPresent(), equalTo(true));
        TimestampedHex tsHex = optHex.get();
        assertThat(tsHex.getTimestamp(), equalTo(1683156721857L));
        assertThat(tsHex.getHexMessage(), startsWith("00134a42b3c30180c7ce400302b"));
        assertThat(tsHex.getPath(), containsString("ieee1609dot2.unsecuredData_raw"));
        log.info("{}", tsHex);
    }

    @Test
    public void testParsePcapFrame_WSMP_MISSING() {
        var pcapDecoder = new PcapDecoder();
        Optional<TimestampedHex> optHex = pcapDecoder.parsePcapFrame(WSMP_MISSING_FRAME);

        assertThat(optHex.isPresent(), equalTo(true));
        TimestampedHex tsHex = optHex.get();
        assertThat(tsHex.getTimestamp(), equalTo(1683156721857L));
        assertThat(tsHex.getHexMessage(), startsWith("00002c00020000400"));
        assertThat(tsHex.getPath(), containsString("frame_raw"));
        log.info("{}", tsHex);
    }

    public static final String UDP_FRAME = """
{
    "_index": "packets-2023-05-03",
    "_type": "doc",
    "_score": null,
    "_source": {
        "layers": {
            "frame_raw": [
                "000002120000000000000000000086dd6030000000ce110080f80f80f80f80f80000000000facc3e80f80f80f80f80f8650c80931b40a4fd2328232800ce00000103002080c0038100400380630014604edee641a6f6c566d3144e145c1cb823de1d9415ba70005510fdfa1fa1007fff8000a02801009cc061002dbfcd500019b81002bbecb6ffc1fca0fe713456efde2ab20fe36af550fce334afffec802107c0009000014e9b5fbfffc7ffff28000040012000022b029e7fa620807eee4644feddb2898080f9cc5b0e1fdf0341a83402f32ed81815bd20f6af31e46d907d255ab1e2bb544cf5420c3a91211db23249119980d24ed35f0fea3270309d118328a129f52189f9",
                0,
                262,
                0,
                1
            ],
            "frame": {
                "frame.time_epoch": "1683155397.596770000"
            },
            "udp": {
                "udp.payload_raw": [
                    "0103002080c0038100400380630014604edee641a6f6c566d3144e145c1cb823de1d9415ba70005510fdfa1fa1007fff8000a02801009cc061002dbfcd500019b81002bbecb6ffc1fca0fe713456efde2ab20fe36af550fce334afffec802107c0009000014e9b5fbfffc7ffff28000040012000022b029e7fa620807eee4644feddb2898080f9cc5b0e1fdf0341a83402f32ed81815bd20f6af31e46d907d255ab1e2bb544cf5420c3a91211db23249119980d24ed35f0fea3270309d118328a129f52189f9",
                    64,
                    198,
                    0,
                    30
                ],
                "udp.payload": "01:03:00:20:80:c0:03:81:00:40:03:80:63:00:14:60:4e:de:e6:41:a6:f6:c5:66:d3:14:4e:14:5c:1c:b8:23:de:1d:94:15:ba:70:00:55:10:fd:fa:1f:a1:00:7f:ff:80:00:a0:28:01:00:9c:c0:61:00:2d:bf:cd:50:00:19:b8:10:02:bb:ec:b6:ff:c1:fc:a0:fe:71:34:56:ef:de:2a:b2:0f:e3:6a:f5:50:fc:e3:34:af:ff:ec:80:21:07:c0:00:90:00:01:4e:9b:5f:bf:ff:c7:ff:ff:28:00:00:40:01:20:00:02:2b:02:9e:7f:a6:20:80:7e:ee:46:44:fe:dd:b2:89:80:80:f9:cc:5b:0e:1f:df:03:41:a8:34:02:f3:2e:d8:18:15:bd:20:f6:af:31:e4:6d:90:7d:25:5a:b1:e2:bb:54:4c:f5:42:0c:3a:91:21:1d:b2:32:49:11:99:80:d2:4e:d3:5f:0f:ea:32:70:30:9d:11:83:28:a1:29:f5:21:89:f9"
            }
        }
    }
}
            """;

    public static final String WSMP_FRAME = """
{
    "_index": "packets-2023-05-03",
    "_type": "doc",
    "_score": null,
    "_source": {
        "layers": {
            "frame_raw": [
                "00002c0002000040000004e548001c000100fc001d0adac00000000a260000000000000000000000e000000088000000ffffffffffff00e06a019bbcfffffffffffffeff270088dc0b030401930f01b410010c00800280b40381004003804d00134a42b3c30180c7ce400302b3c3dd6007001043425c1a5c1801021a130b530b400c10d09dbe9dbe00808c84b014b45005043425c1a5c1803021a13155315401c10c095bc01008c84ae64b4450018200022b02ed6e026a18095de9bd6aaa9d38f6809127648f49b09c018082cad6384a78c2345441b13fd1c899e1159493bce677e94b0097749787def147bd4ecff7e31f436d5ba48358e391c4e8cfe9c96c036bcab2a3744cb5cb734e6d06",
                0,
                268,
                0,
                1
            ],
            "frame": {
                "frame.time_epoch": "1683156721.857857000"
            },
            "wsmp": {
                "Wave Short Message": {
                    "ieee1609dot2.Ieee1609Dot2Data_element": {
                        "ieee1609dot2.content_tree": {
                            "ieee1609dot2.signedData_element": {
                                "ieee1609dot2.tbsData_element": {
                                    "ieee1609dot2.payload_element": {
                                        "ieee1609dot2.data_element": {
                                            "ieee1609dot2.content_tree": {
                                                "ieee1609dot2.unsecuredData_raw": [
                                                    "00134a42b3c30180c7ce400302b3c3dd6007001043425c1a5c1801021a130b530b400c10d09dbe9dbe00808c84b014b45005043425c1a5c1803021a13155315401c10c095bc01008c84ae64b44",
                                                    95,
                                                    77,
                                                    0,
                                                    30
                                                ],
                                                "ieee1609dot2.unsecuredData": "00:13:4a:42:b3:c3:01:80:c7:ce:40:03:02:b3:c3:dd:60:07:00:10:43:42:5c:1a:5c:18:01:02:1a:13:0b:53:0b:40:0c:10:d0:9d:be:9d:be:00:80:8c:84:b0:14:b4:50:05:04:34:25:c1:a5:c1:80:30:21:a1:31:55:31:54:01:c1:0c:09:5b:c0:10:08:c8:4a:e6:4b:44"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
            """;

    public static final String WSMP_MISSING_FRAME = """
{
    "_index": "packets-2023-05-03",
    "_type": "doc",
    "_score": null,
    "_source": {
        "layers": {
            "frame_raw": [
                "00002c0002000040000004e548001c000100fc001d0adac00000000a260000000000000000000000e000000088000000ffffffffffff00e06a019bbcfffffffffffffeff270088dc0b030401930f01b410010c00800280b40381004003804d00134a42b3c30180c7ce400302b3c3dd6007001043425c1a5c1801021a130b530b400c10d09dbe9dbe00808c84b014b45005043425c1a5c1803021a13155315401c10c095bc01008c84ae64b4450018200022b02ed6e026a18095de9bd6aaa9d38f6809127648f49b09c018082cad6384a78c2345441b13fd1c899e1159493bce677e94b0097749787def147bd4ecff7e31f436d5ba48358e391c4e8cfe9c96c036bcab2a3744cb5cb734e6d06",
                0,
                268,
                0,
                1
            ],
            "frame": {
                "frame.time_epoch": "1683156721.857857000"
            }
        }
    }
}
            """;
}
