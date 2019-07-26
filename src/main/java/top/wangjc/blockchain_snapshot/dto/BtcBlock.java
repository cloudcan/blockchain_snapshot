package top.wangjc.blockchain_snapshot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.web3j.protocol.core.Response;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BtcBlock extends Response<BtcBlock.Block> {
    public BtcBlock.Block getBlock() {
        return getResult();
    }

    @Data
    public static class Block {
        private String hash;
        private Integer height;
        @JsonProperty("tx")
        private List<Transaction> transactions = new ArrayList<>();

        @Data
        public static class Transaction {
            private String txid;
            private String hash;
            @JsonProperty("vin")
            private List<Input> inputs = new ArrayList<>();
            @JsonProperty("vout")
            private List<Output> outputs = new ArrayList<>();

            public boolean isCoinbase() {
                return inputs.size() == 1 && inputs.get(0).coinbase != null;
            }

            @Data
            public static class Input {
                private String coinbase;
                private String txid;
                @JsonProperty("vout")
                private Integer index;
            }

            @Data
            public static class Output {
                private BigDecimal value;
                @JsonProperty("n")
                private Integer index;
                private ScriptPubKey scriptPubKey;

                @Data
                public static class ScriptPubKey {
                    private List<String> addresses = new ArrayList<>();
                }
            }
        }
    }
}
