package com.dorazibe02.imap.SafeUrl.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UrlAnalysisResult {
    private Map<String, SingleUrlAnalysisResult> results;
}
