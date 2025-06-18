package com.dorazibe02.imap.SafeUrl;

import com.dorazibe02.imap.SafeUrl.Dto.SingleUrlAnalysisResult;
import com.dorazibe02.imap.SafeUrl.Dto.UrlAnalysisResult;

import java.util.List;

public interface UrlScanService {
    UrlAnalysisResult analyzeUrls(List<String> urls);
}
