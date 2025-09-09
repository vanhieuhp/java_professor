package dev.hieunv.service;

import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExternalApiService {

    private final OkHttpClient okHttpClient;

    public String callSlowApi() throws Exception {
        Request request = new Request.Builder()
                .url("https://httpbin.org/delay/10") // simulates 10s delay
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Unexpected code " + response);
            }
            return response.body().string();
        }
    }
}
