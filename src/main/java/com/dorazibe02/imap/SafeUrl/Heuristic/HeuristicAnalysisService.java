package com.dorazibe02.imap.SafeUrl.Heuristic;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HeuristicAnalysisService {

    private static final Set<String> PHISHING_KEYWORDS = Set.of(
            "비밀번호", "패스워드", "계정", "로그인", "긴급", "인증",
            "업데이트", "본인인증", "결제", "은행", "카드"
    );

    private static final Set<String> SHORTENER_DOMAINS = Set.of(
            "bit.ly", "me2.do", "is.gd", "vo.la", "han.gl",
            "buly.kr", "url.kr", "goo.gl", "adf.ly", "ow.ly", "naver.me", "jmb.tw"
    );

    private static final Pattern FROM_HEADER_PATTERN = Pattern.compile(".*<(.*@(.*))>");

    public boolean isSuspicious(String fromHeader, List<String> urls) {
        if (usesUrlShortener(urls)) {
            System.out.println("[Heuristic] URL shortener detected.");
            return true;
        }
        if (isSenderMismatched(fromHeader)) {
            System.out.println("[Heuristic] Sender domain mismatch detected.");
            return true;
        }
        return false;
    }

    private boolean usesUrlShortener(List<String> urls) {
        if (urls == null) return false;
        for (String url : urls) {
            try {
                // URL에서 도메인 부분만 추출
                String domain = url.split("/")[2];
                if (SHORTENER_DOMAINS.contains(domain)) {
                    return true;
                }
            } catch (Exception e) {
                // URL 파싱 실패 시 무시
            }
        }
        return false;
    }

    private boolean isSenderMismatched(String fromHeader) {
        if (fromHeader == null) return false;
        Matcher matcher = FROM_HEADER_PATTERN.matcher(fromHeader);
        if (matcher.matches()) {
            String emailAddress = matcher.group(1); // email@example.com
            String emailDomain = matcher.group(2);  // example.com

            // From 헤더에서 이메일 주소를 제외한 나머지 부분 (보통 표시 이름)
            String displayName = fromHeader.replace("<" + emailAddress + ">", "").trim();

            // 표시 이름에 '@'가 포함되어 있고(다른 이메일 주소처럼 보이고), 그 도메인이 실제 발신 도메인과 다른지 확인
            if (displayName.contains("@")) {
                try {
                    String displayNameDomain = displayName.split("@")[1];
                    if (!displayNameDomain.equalsIgnoreCase(emailDomain)) {
                        return true;
                    }
                } catch (Exception e) {
                    // 파싱 실패 시 무시
                }
            }
        }
        return false;
    }
}
