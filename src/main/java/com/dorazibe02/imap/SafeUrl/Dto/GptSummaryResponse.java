package com.dorazibe02.imap.SafeUrl.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GptSummaryResponse {
    private String Tag;
    private String Content;
    private int ThreatScore;
}
