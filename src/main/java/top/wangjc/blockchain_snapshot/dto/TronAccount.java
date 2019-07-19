package top.wangjc.blockchain_snapshot.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class TronAccount implements Serializable {
    private String address;
    private long balance;
    private List<KeyValue> asset=new ArrayList<>();

    @Data
    public static class KeyValue {
        private String key;
        private long value;

    }

    public TronAccount(String address) {
        this.address = address;
    }
}
