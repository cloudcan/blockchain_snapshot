package top.wangjc.blockchain_snapshot.config;

import com.mongodb.MongoClientOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoConfig {

    @Bean
    public MongoClientOptions getMongoClientOptions(){
        return MongoClientOptions
                .builder()
                .maxConnectionIdleTime(60000)
                .build();
    }
}
