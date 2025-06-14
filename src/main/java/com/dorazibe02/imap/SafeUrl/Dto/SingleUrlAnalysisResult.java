package com.dorazibe02.imap.SafeUrl.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SingleUrlAnalysisResult<T> {
    private String url;

    // 분석 요약 정보 (옵션, 예: GPT 요약 메시지)
    private String summary;

    // 안전 여부
    private boolean isSafe;

    // Notion에 부여할 태그 (예: "Safety", "Malware", "Phishing")
    private List<String> tags;

    // 위협 레벨 점수 (0~100)
    private int threatScore;

    // API 응답 원본 데이터
    private String rawResponse;

    public <E> SingleUrlAnalysisResult(String url, String summary, boolean isSafe, List<String> tags, int threatScore, Object rawResponse) {
    }
}
