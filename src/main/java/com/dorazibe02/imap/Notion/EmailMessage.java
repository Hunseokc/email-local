package com.dorazibe02.imap.Notion;

import java.time.LocalDateTime;
import java.util.List;

public class EmailMessage {
    private String subject;               // 제목
    private String from;                  // 발신자 이메일
    private String date;           // 수신 일시
    private List<String> tags;            // 다중 선택 태그
    private String content;               // 본문
    private String status;                // 상태 (Unread, Read, ...)

    public EmailMessage() {}

    public EmailMessage(String subject, String from, String date, List<String> tags, String content, String status) {
        this.subject = subject;
        this.from = from;
        this.date = date;
        this.tags = tags;
        this.content = content;
        this.status = status;
    }

    // Getter & Setter
    public String getSubject() {
        return subject;
    }
    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getFrom() {
        return from;
    }
    public void setFrom(String from) {
        this.from = from;
    }

    public String getDate() {
        return date;
    }
    public void setDate(String date) {
        this.date = date;
    }

    public List<String> getTags() {
        return tags;
    }
    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getContent() {
        return content;
    }
    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "EmailMessage{" +
                "subject='" + subject + '\'' +
                ", from='" + from + '\'' +
                ", date=" + date +
                ", tags=" + tags +
                ", content='" + content + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
