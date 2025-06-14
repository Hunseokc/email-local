package com.dorazibe02.imap.SafeUrl.VirusTotal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class VirusTotalClient {
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    @Value("${api-keys.virustotal}")
    private String apiKey;

    public VirusTotalClient() {
        this.okHttpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        try {
            if (this.apiKey == null) {
                throw new IllegalStateException("VIRUSTOTAL_API_KEY not found in properties.");
            }
            System.out.println("VirusTotal API Key and WebClient loaded from Supabase Vault successfully.");
        } catch (Exception e) {
            throw new RuntimeException("VirusTotal API Key loading failed.", e);
        }
    }

    public JsonNode requestUrlScan(String url) {
        RequestBody body = new FormBody.Builder()
                .add("url", url)
                .build();

        // 요청 생성
        Request request = new Request.Builder()
                .url("https://www.virustotal.com/api/v3/urls")
                .post(body)
                .addHeader("x-apikey", this.apiKey)
                .addHeader("accept", "application/json")
                .addHeader("User-Agent", "My-Email-Plugin-Client/1.0")
                .build();

        System.out.println("[DEBUG] Sending request with OkHttpClient: " + request.toString());

        try (Response response = okHttpClient.newCall(request).execute()) {
            String responseBodyString = response.body().string();
            System.out.println("[DEBUG] OkHttp Response Code: " + response.code());
            System.out.println("[DEBUG] OkHttp Response Body: " + responseBodyString);

            if (!response.isSuccessful()) {
                // 실패 시에도 로그를 남깁니다.
                System.err.println("OkHttpClient request failed with code: " + response.code());
                return null;
            }
            return objectMapper.readTree(responseBodyString);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public JsonNode getAnalysisReport(String analysisId) {
        Request request = new Request.Builder()
                .url("https://www.virustotal.com/api/v3/analyses/" + analysisId)
                .get() // GET 요청
                .addHeader("x-apikey", this.apiKey)
                .addHeader("accept", "application/json")
                .addHeader("User-Agent", "My-Email-Plugin-Client/1.0")
                .build();

        System.out.println("[DEBUG] Getting analysis report with OkHttpClient: " + request.toString());

        try (Response response = okHttpClient.newCall(request).execute()) {
            String responseBodyString = response.body().string();
            System.out.println("[DEBUG] OkHttp Report Response Code: " + response.code());
            System.out.println("[DEBUG] OkHttp Report Response Body: " + responseBodyString);

            if (!response.isSuccessful()) {
                System.err.println("OkHttpClient report request failed with code: " + response.code());
                return null;
            }
            return objectMapper.readTree(responseBodyString);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
