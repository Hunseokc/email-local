package com.dorazibe02.imap.SafeUrl.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UrlAnalysisRequest {
    private long userId;
    private String pageId;
    private String mailUid;
    private List<String> urls;

    // Log 용 메일 데이터
    private String subject;
    private String from;
    private String date;

    private String startFromStep = "INITIAL";
}
