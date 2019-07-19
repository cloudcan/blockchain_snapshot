package top.wangjc.blockchain_snapshot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Data
public class TronBlock implements Serializable {
    private String blockID;
    private List<Transaction> transactions=new ArrayList<>();

    @Data
    public static class Transaction {
        private String txID;
        @JsonProperty("raw_data")
        private RawData rawData;

        @Data
        public static class RawData {
            private List<Contract> contract=new ArrayList<>();

            @Data
            public static class Contract {
                private Parameter parameter;
                private String type;

                @Data
                public static class Parameter {
                    private Value value;

                    @Data
                    public static class Value {

                        @JsonProperty("owner_address")
                        private String ownerAddress;
//                        @JsonProperty("contract_address")
//                        private String contractAddress;
//                        @JsonProperty("call_value")
//                        private BigInteger callValue;
//                        @JsonProperty("to_address")
//                        private String toAddress;
//                        private BigInteger amount;
//                        private String data;
                    }
                }

            }
        }
    }
}
