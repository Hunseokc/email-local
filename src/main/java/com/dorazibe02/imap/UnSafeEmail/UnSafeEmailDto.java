package com.dorazibe02.imap.UnSafeEmail;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UnSafeEmailDto {
    private String subject;
    private String fromAddress;
    private String receivedDate;
    private String detectedTag;
    private int threatScore;

    public UnSafeEmailDto(UnSafeEmail email) {
        this.subject = email.getSubject();
        this.fromAddress = email.getFromAddress();
        this.receivedDate = email.getReceivedDate();
        this.detectedTag = email.getTag();
        this.threatScore = email.getThreatScore();
    }
}