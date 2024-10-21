package us.dot.its.jpo.ode.mockdata;

import us.dot.its.jpo.ode.api.models.messages.BsmDecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.MapDecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.SpatDecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.SrmDecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.SsmDecodedMessage;
import us.dot.its.jpo.ode.api.models.messages.TimDecodedMessage;

public class MockDecodedMessageGenerator {
    

    public static BsmDecodedMessage getBsmDecodedMessage(){
        BsmDecodedMessage message = new BsmDecodedMessage(MockBsmGenerator.getJsonBsms().get(0),"00145144ad0b7947c2ed9ad2748035a4e8ff880000000fd2229199307d7d07d0b17fff05407d12720038c000fe72c107b001ea88fffeb4002127c0009000000fdfffe3ffff9407344704000041910120100000000efc10609c26e900e11f61a947802127c0009000000fdfffe3ffff9407453304000041910120100000008ffffe501ca508100000000000a508100000404804000000849f00024000003f7fff8ffffe501ca508100000fe501ca508100000fffe501ca51c10000000024000003f7fff8ffffe501ca51c1" , "");
        return message;
    }

    public static MapDecodedMessage getMapDecodedMessage(){
        MapDecodedMessage message = new MapDecodedMessage(MockMapGenerator.getProcessedMaps().get(0),MockMapGenerator.getJsonMaps().get(0),"0012839338023000205e96094d40df4c2ca626c8516e02dc3c2010640000000289e01c009f603f42e88039900000000a41107b027d80fd0a4200c6400000002973021c09f603de0c16029200000080002a8a008d027d98fee805404fb0e1085f60588200028096021200000080002aa0007d027d98fe9802e04fb1200c214456228000a02b1240005022c03240000020000d56b40bc04fb35ff655e2c09f623fb81c835fec0db240a0a2bff4aebf82c660000804b0089000000800025670034013ecd7fb9578e027d9aff883c4e050515ffa567a41635000040258024800000400012b8f81f409f663fac094013ecd7fc83ddb02829affa480bc04fb02c6e0000804b09c5000000200035ea98a9604f60da6c7c113d505c35ffe941d409f65c05034c050500c9880004409bc800000006d2bd3cec813c40cde062c1fd400000200008791ea3db3cf380a009f666f05005813d80ffe0a0588c00040092106a00000000bc75cac009f66db54c04a813d80a100801241ed40000000078ebae3b6da7a008809e2050904008811f100000000bc72389009f60eca8002049c400000002f1b2ca3027d93a71fa813ec204bc400000002f1b2b34027b0397608880cd10000000039b8e1a51036820505080d51000000003a7461ed1036760505080dd1000000003b2f62311006260505160bca00000080002b785e2a80a0a6c028de728145037f1f9e456488000202b2540001022c1894000001000057058c5b81414d806dbcd4028a18f4df23a050502c8d0000404b05a5000000800035b6471bc05053602431f380a2864087bdb0141458064ab0d6c00053fc013ec0b0680006012c15940000020000d6c06c6581414d807fb972028a1901d78dc050536020ec1800a0a6c039d639813d80b0780006012c1494000002000096ab8c6581414d8062be32028a1b01417e04050a360172d77009e2058440003009409c200000040006b3486a480a0a1cab7134c8117dcc02879b018fae2c050f3601ced54809e21012720000000067fbad0007e7e84045c80000000100661580958004041c8000000019f3658401cdfa2c0d64000002000144016c02c36ddfff0282984acc1ee05052c36f0ac02828669d82da8f821480a0a10f140002c8e0001004b03190000008000519fd190c43b2e0066108b08401428c342a0ce02828258a0604a6be959aee0e6050502c920001004b02d90000008000459fa164404fb30a8580a00a14619c306701414c32ce10e02829659081f814141029030164b000080200" ,"");
        return message;
    }

    public static SpatDecodedMessage getSpatDecodedMessage(){
        SpatDecodedMessage message = new SpatDecodedMessage(MockSpatGenerator.getProcessedSpats().get(0),MockSpatGenerator.getJsonSpats().get(0),"001338000817a780000089680500204642b342b34802021a15a955a940181190acd0acd20100868555c555c00104342aae2aae002821a155715570" , "");
        return message;
    }

    public static TimDecodedMessage getTimDecodedMessage(){
        TimDecodedMessage message = new TimDecodedMessage(MockTimGenerator.getJsonTims().get(0),"005f498718cca69ec1a04600000100105d9b46ec5be401003a0103810040038081d4001f80d07016da410000000000000bbc2b0f775d9b0309c271431fa166ee0a27fff93f136b8205a0a107fb2ef979f4c5bfaeec97e4ad70c2fb36cd9730becdb355cc2fd2a7556b160b98b46ab98ae62c185fa55efb468d5b4000000004e2863f42cddc144ff7980040401262cdd7b809c509f5c62cdd35519c507b9062cdcee129c505cf262cdca5ff9c50432c62cdc5d3d9c502e3e62cdc13e79c501e9262cdbca2d9c5013ee62cdb80359c500e6a62cdb36299c500bc862cdaec1d9c50093c62cdaa2109c5006ea1080203091a859eeebb36006001830001aad27f4ff7580001aad355e39b5880a30029d6585009ef808332d8d9f80c3855151b38c772f765007967ec1170bcb7937f5cb880a25a52863493bcb87570dbcb5abc6bfb2faec606cfa34eb95a24790b2017366d3aabe7729e" , "");
        return message;
    }

    public static SrmDecodedMessage getSrmDecodedMessage(){
        SrmDecodedMessage message = new SrmDecodedMessage(MockSrmGenerator.getJsonSrms().get(0),"001d2130000010090bd341080d00855c6c0c6899853000a534f7c24cb29897694759b7c0" , "");
        return message;
    }

    public static SsmDecodedMessage getSsmDecodedMessage(){
        SsmDecodedMessage message = new SsmDecodedMessage(MockSsmGenerator.getJsonSsms().get(0),"001e120000000005e9c04071a26614c06000040ba0" , "");
        return message;
    }
}