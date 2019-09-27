package top.wangjc.blockchain_snapshot.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class TronAccount implements Serializable {
    private String address;
    private BigDecimal balance;
    private List<KeyValue> asset = new ArrayList<>();

    @Data
    public static class KeyValue {
        private String key;
        private BigDecimal value;

    }

    public TronAccount(String address) {
        this.address = address;
    }
}
