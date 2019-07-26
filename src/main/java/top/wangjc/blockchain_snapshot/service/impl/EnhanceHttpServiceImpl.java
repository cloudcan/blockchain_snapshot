package top.wangjc.blockchain_snapshot.service.impl;

import com.fasterxml.jackson.databind.type.CollectionType;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.exceptions.ClientConnectionException;
import org.web3j.protocol.http.HttpService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static okhttp3.ConnectionSpec.CLEARTEXT;

/**
 * 增强http服务
 */
@Slf4j
public class EnhanceHttpServiceImpl extends HttpService {
    private static final CipherSuite[] INFURA_CIPHER_SUITES = new CipherSuite[]{
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,

            // Note that the following cipher suites are all on HTTP/2's bad cipher suites list. We'll
            // continue to include them until better suites are commonly available. For example, none
            // of the better cipher suites listed above shipped with Android 4.4 or Java 7.
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_3DES_EDE_CBC_SHA,

            // Additional INFURA CipherSuites
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA256
    };

    private static final ConnectionSpec INFURA_CIPHER_SUITE_SPEC = new ConnectionSpec
            .Builder(ConnectionSpec.MODERN_TLS).cipherSuites(INFURA_CIPHER_SUITES).build();

    /**
     * The list of {@link ConnectionSpec} instances used by the connection.
     */
    private static final List<ConnectionSpec> CONNECTION_SPEC_LIST = Arrays.asList(
            INFURA_CIPHER_SUITE_SPEC, CLEARTEXT);
    private final OkHttpClient httpClient;

    public EnhanceHttpServiceImpl(String url, OkHttpClient client) {
        super(url, client);
        this.httpClient = client;
    }

    /**
     * 创建默认的实例
     *
     * @return
     */
    public static EnhanceHttpServiceImpl createDefault(String url) {
        return new EnhanceHttpServiceImpl(url, createOkHttpClient());
    }

    /**
     * 批量发送请求
     *
     * @param requests
     * @param responseType
     * @param <T>
     * @return
     * @throws IOException
     */
    public <T> List<T> sendBatch(
            List<Request> requests, Class<T> responseType) throws IOException {
        String payload = objectMapper.writeValueAsString(requests);

        try (InputStream result = performIO(payload)) {
            if (result != null) {
                CollectionType type = objectMapper.getTypeFactory().constructCollectionType(List.class, responseType);
                return objectMapper.readValue(result, type);
            } else {
                return null;
            }
        }
    }

    /**
     * 发送自定义请求
     */
    public <T> T sendCustomRequest(okhttp3.Request request, Class<T> responseType) throws IOException {

        Response response = httpClient.newCall(request).execute();
        ResponseBody responseBody = response.body();
        if (response.isSuccessful()) {
            InputStream result = responseBody.byteStream();
            if (result != null) {
                return objectMapper.readValue(result, responseType);
            } else {
                return null;
            }
        } else {
            int code = response.code();
            String text = responseBody == null ? "N/A" : responseBody.string();
            throw new ClientConnectionException("Invalid response received: " + code + "; " + text);
        }
    }

    public static OkHttpClient createOkHttpClient() {
        final OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectionSpecs(CONNECTION_SPEC_LIST)
                .connectionPool(new ConnectionPool())
                .connectTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120,TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS);
        configureLogging(builder);
        return builder.build();
    }

    private static void configureLogging(OkHttpClient.Builder builder) {
        if (log.isDebugEnabled()) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor(log::debug);
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(logging);
        }
    }
}
