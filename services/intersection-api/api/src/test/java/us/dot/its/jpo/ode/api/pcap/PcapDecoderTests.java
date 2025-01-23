//package us.dot.its.jpo.ode.api.pcap;
//
//
//import lombok.extern.slf4j.Slf4j;
//import org.junit.Test;
//import us.dot.its.jpo.ode.api.models.messages.TimestampedHex;
//
//import java.util.Optional;
//
//import static org.hamcrest.MatcherAssert.assertThat;
//import static org.hamcrest.Matchers.equalTo;
//import static org.hamcrest.Matchers.startsWith;
//import static org.hamcrest.Matchers.containsString;
//
//@Slf4j
//public class PcapDecoderTests {
//
//    @Test
//    public void testParsePcapFrame_UDP() {
//        var pcapDecoder = new PcapDecoderKaitai();
//        Optional<TimestampedHex> optHex = pcapDecoder.parsePcapFrame(UDP_FRAME);
//
//        assertThat(optHex.isPresent(), equalTo(true));
//        TimestampedHex tsHex = optHex.get();
//        assertThat(tsHex.getTimestamp(), equalTo(1683155397596L));
//        assertThat(tsHex.getRawDataHex(), startsWith("0103002080c003810"));
//        assertThat(tsHex.getMessageFrameHex(), startsWith("0014604"));
//        assertThat(tsHex.getPath(), containsString("udp.payload_raw"));
//        log.info("{}", tsHex);
//    }
//
//    @Test
//    public void testParsePcapFrame_UDP_MAP() {
//        var pcapDecoder = new PcapDecoderTshark();
//        Optional<TimestampedHex> optHex = pcapDecoder.parsePcapFrame(UDP_MAP_FRAME);
//
//        assertThat(optHex.isPresent(), equalTo(true));
//        TimestampedHex tsHex = optHex.get();
//        assertThat(tsHex.getTimestamp(), equalTo(1683155397596L));
//        assertThat(tsHex.getRawDataHex(), startsWith("010b030401930f01b410010c"));
//        assertThat(tsHex.getMessageFrameHex(), startsWith("001283fb78158"));
//        assertThat(tsHex.getPath(), containsString("udp.payload_raw"));
//        log.info("{}", tsHex);
//    }
//
//    @Test
//    public void testParsePcapFrame_UDP_BSM() {
//        var pcapDecoder = new PcapDecoderTshark();
//        Optional<TimestampedHex> optHex = pcapDecoder.parsePcapFrame(UDP_BSM_FRAME);
//
//        assertThat(optHex.isPresent(), equalTo(true));
//        TimestampedHex tsHex = optHex.get();
//        assertThat(tsHex.getTimestamp(), equalTo(1683155397596L));
//        assertThat(tsHex.getRawDataHex(), startsWith("0103002080e4038100400380818600148082519ee641a"));
//        assertThat(tsHex.getMessageFrameHex(), startsWith("00148082519ee641a"));
//        assertThat(tsHex.getPath(), containsString("udp.payload_raw"));
//        log.info("{}", tsHex);
//    }
//
//    @Test
//    public void testParsePcapFrame_WSMP() {
//        var pcapDecoder = new PcapDecoderTshark();
//        Optional<TimestampedHex> optHex = pcapDecoder.parsePcapFrame(WSMP_FRAME);
//
//        assertThat(optHex.isPresent(), equalTo(true));
//        TimestampedHex tsHex = optHex.get();
//        assertThat(tsHex.getTimestamp(), equalTo(1683156721857L));
//        assertThat(tsHex.getRawDataHex(), startsWith("00134a42b3c30180c7ce400302b"));
//        assertThat(tsHex.getMessageFrameHex(), startsWith("00134a42b3c30180c7ce400302b"));
//        assertThat(tsHex.getPath(), containsString("ieee1609dot2.unsecuredData_raw"));
//        log.info("{}", tsHex);
//    }
//
//    @Test
//    public void testParsePcapFrame_WSMP_MISSING() {
//        var pcapDecoder = new PcapDecoderTshark();
//        Optional<TimestampedHex> optHex = pcapDecoder.parsePcapFrame(WSMP_MISSING_FRAME);
//
//        assertThat(optHex.isPresent(), equalTo(true));
//        TimestampedHex tsHex = optHex.get();
//        assertThat(tsHex.getTimestamp(), equalTo(1683156721857L));
//        assertThat(tsHex.getRawDataHex(), startsWith("00002c00020000400"));
//        assertThat(tsHex.getMessageFrameHex(), startsWith("00134a42b3c30180c7ce400302b"));
//        assertThat(tsHex.getPath(), containsString("frame_raw"));
//        log.info("{}", tsHex);
//    }
//
//    public static final String UDP_FRAME = """
//{
//    "_index": "packets-2023-05-03",
//    "_type": "doc",
//    "_score": null,
//    "_source": {
//        "layers": {
//            "frame_raw": [
//                "000002120000000000000000000086dd6030000000ce110080f80f80f80f80f80000000000facc3e80f80f80f80f80f8650c80931b40a4fd2328232800ce00000103002080c0038100400380630014604edee641a6f6c566d3144e145c1cb823de1d9415ba70005510fdfa1fa1007fff8000a02801009cc061002dbfcd500019b81002bbecb6ffc1fca0fe713456efde2ab20fe36af550fce334afffec802107c0009000014e9b5fbfffc7ffff28000040012000022b029e7fa620807eee4644feddb2898080f9cc5b0e1fdf0341a83402f32ed81815bd20f6af31e46d907d255ab1e2bb544cf5420c3a91211db23249119980d24ed35f0fea3270309d118328a129f52189f9",
//                0,
//                262,
//                0,
//                1
//            ],
//            "frame": {
//                "frame.time_epoch": "1683155397.596770000"
//            },
//            "udp": {
//                "udp.payload_raw": [
//                    "0103002080c0038100400380630014604edee641a6f6c566d3144e145c1cb823de1d9415ba70005510fdfa1fa1007fff8000a02801009cc061002dbfcd500019b81002bbecb6ffc1fca0fe713456efde2ab20fe36af550fce334afffec802107c0009000014e9b5fbfffc7ffff28000040012000022b029e7fa620807eee4644feddb2898080f9cc5b0e1fdf0341a83402f32ed81815bd20f6af31e46d907d255ab1e2bb544cf5420c3a91211db23249119980d24ed35f0fea3270309d118328a129f52189f9",
//                    64,
//                    198,
//                    0,
//                    30
//                ],
//                "udp.payload": "01:03:00:20:80:c0:03:81:00:40:03:80:63:00:14:60:4e:de:e6:41:a6:f6:c5:66:d3:14:4e:14:5c:1c:b8:23:de:1d:94:15:ba:70:00:55:10:fd:fa:1f:a1:00:7f:ff:80:00:a0:28:01:00:9c:c0:61:00:2d:bf:cd:50:00:19:b8:10:02:bb:ec:b6:ff:c1:fc:a0:fe:71:34:56:ef:de:2a:b2:0f:e3:6a:f5:50:fc:e3:34:af:ff:ec:80:21:07:c0:00:90:00:01:4e:9b:5f:bf:ff:c7:ff:ff:28:00:00:40:01:20:00:02:2b:02:9e:7f:a6:20:80:7e:ee:46:44:fe:dd:b2:89:80:80:f9:cc:5b:0e:1f:df:03:41:a8:34:02:f3:2e:d8:18:15:bd:20:f6:af:31:e4:6d:90:7d:25:5a:b1:e2:bb:54:4c:f5:42:0c:3a:91:21:1d:b2:32:49:11:99:80:d2:4e:d3:5f:0f:ea:32:70:30:9d:11:83:28:a1:29:f5:21:89:f9"
//            }
//        }
//    }
//}
//            """;
//
//    public static final String UDP_MAP_FRAME = """
//{
//    "_index": "packets-2023-05-03",
//    "_type": "doc",
//    "_score": null,
//    "_source": {
//        "layers": {
//            "frame": {
//                "frame.time_epoch": "1683155397.596770000"
//            },
//            "udp": {
//                "udp.payload_raw": [
//                    "010b030401930f01b410010c00e000001784e40381004003808203ff001283fb7815888003020615abbb4f6a18796bef87cd3e987a65a7d3965cbd03168014da6253c28b81b314886029e5058022800000120000bc03e80b4c23019a0926200082c0214000002000105f09ca05a5ad80c72d473409d13fb37e5c9f7ab8364c551c92048e4000416018a000001000083c0a5374b4a3301e25a902812527f3cfc413ec27134989bb8f6091d800082c041400000200010788c9d51692ca038cb51e902024fe59f9d27d2ce2a930fd720c123d00010580a2800000280000f20f26e2d815c0950244d0000cb0185000000500001e63a2265a3d280be048a2000196038a0000010000c3d12be44b469b01985a8d682892600b0498fed4f88260e4f5d0fbe0e00261dae5e8247e0002860208800000800001f1c93e00f1212180922000002000007bf55a1071dc27180a22000002000007b636488765c18180b22000002000007ac370e87b7c122c0c34000000900047a7020d8e8323781d3503280908200022c0d340000020000878eb2248e8ea2db24ec241e12a1d06f8124f00004581c6800000400010eef045b1d26c4a249db883c2544a0df024a200008b03cd000000800021d8348da3a63072893af10744a8e01b90494c000116081a000000800023a54123c74ec0a10e772558048ea000296089a000000800023997128475310620a9e02f0123c8000a30248400000400000dcfe4e60cde2de30268400000400000dfd44d60ca93e230288400000400000e2a24c7050236160aaa0000004800020850042a732c0288244880010b05950000008000319ffd2e054ed3803c2abf9406315734e0234a9ffd01400908800042c17540000020000c6772563153d5a00d4aaff301e255cd380542a80340410242600010b06150000008000319c3581c54f9b803d2ac0ec07c95734e014ca9fdf016c090a800042c195400000140000667b6b414ed4e021012508000e5834a800000280000cc10ea32a37f402f824a50001cb06d500000080004195ea0485511f806e2130702990e9c7ed4ab99aff2a5500d806d0485c00048c0e310000010000031a958d82419fe0c0eb10000010000031de5344226a088c0f3100000100000320d4e4420c9fe8c0fb1000001000003264c724470eff25840e800000120008cae3b331ae33c4e361deb2812384000c5842e800000400008cdfbb361ad7bd2857285a8f204894000316113a000001000023434eca06b3af8995ca8aa39012270000c5846e800000400008d3d9b311abbfef257296a8d2048a4000316123a0000008000235a36c686ac8ff18d86fc320484a00009612ba000000800023644ec7c8d54e06225f0e730485200008c134100000100000393becd84cd1924304f0400000400000e23bb391348e100c14410000010000037d4ecb42b4fe20c14c1000001000003726eccc2bc3ac8600320409700022b029e8a6caf00022b03751e10af81010100030180c620fb90caad3b9c508208da641e2d6b81d4a13969210003245819df83279c830101800348010780032040958005000001e040800320409780050080012040800182800500800130408001838005008001f0400001870001260001808182157959ad9ed363db22443c1ec3ff2b092100e5eb7681956e402adda87a208aab8083a15b9a874ddd94930aa451d1d909a6f995c06b06acb9da1ffbaca0b9d13629301db1b76f017f97a9b15e4694ce4e6005748c7124e6012ba8899b57d4fd3c280c",
//                    64,
//                    198,
//                    0,
//                    30
//                ]
//            }
//        }
//    }
//}
//""";
//
//    public static final String UDP_BSM_FRAME = """
//            {
//    "_index": "packets-2023-05-03",
//    "_type": "doc",
//    "_score": null,
//    "_source": {
//        "layers": {
//            "frame": {
//                "frame.time_epoch": "1683155397.596770000"
//            },
//            "udp": {
//                "udp.payload_raw": [
//                    "0103002080e4038100400380818600148082519ee641a6c474e6d310a9145c0d49a3dc9414000070e4bd48fdfa1fa1007fff8000a028010124c0e0ff913ff11000004c0fdc03f7baffe01780fcb1bec0700002d20fc77bc0daffc093a0fc5abc23cffc0b260fc88bbf1affc4f380fc86baefcff655480facc307b4fd860327ed16402107c0009000014e9b5fbfffc7ffff28000040012000022b02a293f5e0807eee4644feddb2898080e802817ee7da55e29bbc33287dc06ebc99879ba87fa2bca8793690f96586f1c470905e069a1976b033efd88cf6c5f03abde9e2f28b992154db6bb8d822776c19",
//                    64,
//                    198,
//                    0,
//                    30
//                ]
//            }
//        }
//    }
//}
//            """;
//
//    public static final String WSMP_FRAME = """
//{
//    "_index": "packets-2023-05-03",
//    "_type": "doc",
//    "_score": null,
//    "_source": {
//        "layers": {
//            "frame_raw": [
//                "00002c0002000040000004e548001c000100fc001d0adac00000000a260000000000000000000000e000000088000000ffffffffffff00e06a019bbcfffffffffffffeff270088dc0b030401930f01b410010c00800280b40381004003804d00134a42b3c30180c7ce400302b3c3dd6007001043425c1a5c1801021a130b530b400c10d09dbe9dbe00808c84b014b45005043425c1a5c1803021a13155315401c10c095bc01008c84ae64b4450018200022b02ed6e026a18095de9bd6aaa9d38f6809127648f49b09c018082cad6384a78c2345441b13fd1c899e1159493bce677e94b0097749787def147bd4ecff7e31f436d5ba48358e391c4e8cfe9c96c036bcab2a3744cb5cb734e6d06",
//                0,
//                268,
//                0,
//                1
//            ],
//            "frame": {
//                "frame.time_epoch": "1683156721.857857000"
//            },
//            "wsmp": {
//                "Wave Short Message": {
//                    "ieee1609dot2.Ieee1609Dot2Data_element": {
//                        "ieee1609dot2.content_tree": {
//                            "ieee1609dot2.signedData_element": {
//                                "ieee1609dot2.tbsData_element": {
//                                    "ieee1609dot2.payload_element": {
//                                        "ieee1609dot2.data_element": {
//                                            "ieee1609dot2.content_tree": {
//                                                "ieee1609dot2.unsecuredData_raw": [
//                                                    "00134a42b3c30180c7ce400302b3c3dd6007001043425c1a5c1801021a130b530b400c10d09dbe9dbe00808c84b014b45005043425c1a5c1803021a13155315401c10c095bc01008c84ae64b44",
//                                                    95,
//                                                    77,
//                                                    0,
//                                                    30
//                                                ],
//                                                "ieee1609dot2.unsecuredData": "00:13:4a:42:b3:c3:01:80:c7:ce:40:03:02:b3:c3:dd:60:07:00:10:43:42:5c:1a:5c:18:01:02:1a:13:0b:53:0b:40:0c:10:d0:9d:be:9d:be:00:80:8c:84:b0:14:b4:50:05:04:34:25:c1:a5:c1:80:30:21:a1:31:55:31:54:01:c1:0c:09:5b:c0:10:08:c8:4a:e6:4b:44"
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }
//}
//            """;
//
//    public static final String WSMP_MISSING_FRAME = """
//{
//    "_index": "packets-2023-05-03",
//    "_type": "doc",
//    "_score": null,
//    "_source": {
//        "layers": {
//            "frame_raw": [
//                "00002c0002000040000004e548001c000100fc001d0adac00000000a260000000000000000000000e000000088000000ffffffffffff00e06a019bbcfffffffffffffeff270088dc0b030401930f01b410010c00800280b40381004003804d00134a42b3c30180c7ce400302b3c3dd6007001043425c1a5c1801021a130b530b400c10d09dbe9dbe00808c84b014b45005043425c1a5c1803021a13155315401c10c095bc01008c84ae64b4450018200022b02ed6e026a18095de9bd6aaa9d38f6809127648f49b09c018082cad6384a78c2345441b13fd1c899e1159493bce677e94b0097749787def147bd4ecff7e31f436d5ba48358e391c4e8cfe9c96c036bcab2a3744cb5cb734e6d06",
//                0,
//                268,
//                0,
//                1
//            ],
//            "frame": {
//                "frame.time_epoch": "1683156721.857857000"
//            }
//        }
//    }
//}
//            """;
//}
