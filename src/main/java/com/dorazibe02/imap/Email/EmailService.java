package com.dorazibe02.imap.Email;

import com.dorazibe02.imap.Auth.Auth;
import com.dorazibe02.imap.Auth.UserAuthCredentialService;
import com.dorazibe02.imap.Redis.RedisCacheService;
import com.dorazibe02.imap.Notion.EmailMessage;
import com.dorazibe02.imap.Notion.NotionService;
import com.dorazibe02.imap.SafeUrl.Dto.UrlAnalysisRequest;
import com.dorazibe02.imap.SafeUrl.Heuristic.HeuristicAnalysisService;
import com.dorazibe02.imap.Setting.SettingFeature;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {
    private final UserAuthCredentialService credentialService;
    private final NotionService notionService;
    private final RedisCacheService redisCacheService;
    private final MailSettings mailSettings;

    @Value("${spring.mail.imap.port}")
    private String port;

    @Value("${spring.mail.imap.protocol}")
    private String protocol;

    public String resolveImapHost(String email) {
        String domain = email.split("@")[1];
        String host = mailSettings.getImapHosts().get(domain);

        if (host == null) {
            throw new IllegalArgumentException("지원하지 않는 이메일 서비스: " + domain);
        }
        return host;
    }

    public void pollUserMail(long userId) throws Exception {
        Auth auth = credentialService.getById(userId);
        if (auth == null) return;

        String host = resolveImapHost(auth.getEmail());
        log.info(host);

        Store store = null;
        Folder inbox = null;
        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            Session session = Session.getInstance(props);

            store = session.getStore();
            store.connect(host, auth.getEmail(), credentialService.getDecryptedPassword(userId));

            inbox = store.getFolder("INBOX");
            if (!(inbox instanceof UIDFolder)) {
                // UIDFolder를 지원하지 않으면 로직을 처리할 수 없으므로 종료
                return;
            }
            UIDFolder uidFolder = (UIDFolder) inbox;
            inbox.open(Folder.READ_ONLY);

            // Redis 마지막으로 읽은 UID 조회
            String userId_Str = String.valueOf(userId);
            long lastUid = redisCacheService.getLastUid(userId_Str);

            Message[] messages = inbox.getMessages();

            for (Message message : messages) {
                long uid = uidFolder.getUID(message);
                if (uid <= lastUid) continue;

                processEmail(message, userId, uid);

                // UID 갱신
                lastUid = uid;
            }
            redisCacheService.setLastUid(userId_Str, lastUid);

        } catch (Exception e) {
            log.error("메일 폴링 실패: {} - {}", userId, e.getMessage());
        } finally {
            if (inbox != null && inbox.isOpen()) {
                inbox.close(false);
            }
            if (store != null && store.isConnected()) {
                store.close();
            }
        }
    }

    public void processEmail(Message message, long userId, long uid) {
        try {
            String subject = message.getSubject();
            Date receivedDate = message.getReceivedDate();
            String isoDate = toIso8601(receivedDate);
            // 메일 발신자 추출
            Address[] froms = message.getFrom();

            if (froms != null && froms[0] instanceof InternetAddress) {
                InternetAddress address = (InternetAddress) froms[0];
                String email = address.getAddress();
                String personal = MimeUtility.decodeText(address.getPersonal());

                String from;
                if (personal != null && !personal.isEmpty()) {
                    from = personal + " <" + email + ">";
                } else {
                    from = email;
                }

                String textContent = getTextFromMessage(message);
                List<String> attachments = extractAttachments(message); // 첨부파일 이름 추출

                EmailMessage emailMessage = new EmailMessage(subject, from, isoDate, List.of("Waiting..."), textContent,"Unread");

                // url 추출
                List<String> urlsFromHtml = UrlExtractor.extractUrls(textContent);
                Set<String> uniqueUrls = new LinkedHashSet<>(urlsFromHtml);

                if (uniqueUrls.isEmpty()) return;

                // notion 저장 기능 켜져있다면 저장
                String notionPageId = null;
                if (redisCacheService.getSettingValue(userId, SettingFeature.NOTION_ENABLED)) {
                    notionPageId = notionService.saveEmailToNotion(userId, emailMessage);
                }

                UrlAnalysisRequest req = new UrlAnalysisRequest(userId, notionPageId, String.valueOf(uid), new ArrayList<>(uniqueUrls), subject, from, isoDate, "INITIAL");

                // url 검증 기능 켜져있다면 큐 삽입
                if (redisCacheService.getSettingValue(userId, SettingFeature.URL_CHECK)) {
                    redisCacheService.enqueueUrlAnalysis(req);
                }

                // 이벤트 기반 아키텍처로 설계해서 각 기능과 메일 연동 간 종속성을 제거해야 하지만
                // 백엔드 코드 완성 후 프론트 작성 과정에서 기능 On/Off 필요성을 인지
                // 향후 개선 필요

                log.info("Subject: {}", subject);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getTextFromMessage(Message message) throws Exception {
        String result = "";
        if (message.isMimeType("text/plain")) {
            result = message.getContent().toString();
        } else if (message.isMimeType("multipart/*")) {
            MimeMultipart multipart = (MimeMultipart) message.getContent();
            result = getTextFromMimeMultipart(multipart);
        }
        return result;
    }

    // 메일 -> Text
    private String getTextFromMimeMultipart(MimeMultipart multipart) throws Exception {
        StringBuilder plainText = new StringBuilder();
        // HTML을 우선적으로 찾기
        boolean htmlFound = false;
        String htmlContent = "";

        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);

            if (bodyPart.isMimeType("text/html")) {
                // HTML을 찾으면 내용을 저장하고 탐색 중단
                if (!htmlFound) {
                    htmlContent = (String) bodyPart.getContent();
                    htmlFound = true;
                }
            } else if (bodyPart.isMimeType("text/plain")) {
                plainText.append(bodyPart.getContent().toString());
            } else if (bodyPart.getContent() instanceof MimeMultipart) {
                // 중첩된 multipart 처리
                String nestedContent = getTextFromMimeMultipart((MimeMultipart) bodyPart.getContent());
                // 재귀 호출 결과가 HTML인지 일반 텍스트인지 판단하기 위해 반환한 내용이 HTML 태그를 포함하는지 판단
                if (nestedContent.matches(".*<[a-z][\\s\\S]*>.*")) {
                    if (!htmlFound) {
                        htmlContent = nestedContent;
                        htmlFound = true;
                    }
                } else {
                    plainText.append(nestedContent);
                }
            }
        }

        // HTML이 있으면 HTML, 없으면 plain text 반환
        if (htmlFound) {
            // HTML -> text 추출 반환
            return Jsoup.parse(htmlContent).text();
        } else {
            return plainText.toString();
        }
    }

    // html url 추출
    private List<String> extractUrlsFromHtml(String html) {
        Set<String> urlSet = new LinkedHashSet<>();
        Document doc = Jsoup.parse(html);
        Elements links = doc.select("a[href]"); // <a href="...">
        for (Element link : links) {
            String href = link.attr("href").trim();

            // 자바스크립트 링크, 메일 링크 등은 제외
            if (href.isEmpty() || href.startsWith("javascript:") || href.startsWith("mailto:")) {
                continue;
            }

            // http/https 스킴이 없는 경우 http:// 붙임
            if (!href.startsWith("http://") && !href.startsWith("https://")) {
                href = "http://" + href;
            }

            urlSet.add(href);
        }
        return new ArrayList<>(urlSet); // 중복 제거 + 순서 유지
    }

    // 첨부파일 이름 추출
    // 추후 첨부파일 검증 기능 구현
    private List<String> extractAttachments(Part part) throws Exception {
        List<String> attachments = new ArrayList<>();
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                String disposition = bodyPart.getDisposition();
                if (Part.ATTACHMENT.equalsIgnoreCase(disposition) ||
                        Part.INLINE.equalsIgnoreCase(disposition)) {
                    attachments.add(bodyPart.getFileName());
                }
            }
        }
        return attachments;
    }

    // 본문 내 url 추출
    public class UrlExtractor {
        private static final Pattern URL_PATTERN = Pattern.compile(
                "\\b(?:(?:https?|ftp)://|www\\.)[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
        );

        public static List<String> extractUrls(String text) {
            Set<String> urls = new LinkedHashSet<>();
            Matcher matcher = URL_PATTERN.matcher(text);
            while (matcher.find()) {
                String url = matcher.group();
                if (url.startsWith("www.")) {
                    url = "http://" + url;
                }
                urls.add(url);
            }
            return new ArrayList<>(urls);
        }
    }

    // 오류 발생 시 재처리 로직
    public void reprocessWaitingNotionEmails(long userId) throws Exception {
        log.info("Notion에서 'Waiting' 이메일 재처리 시작 (User ID: {})", userId);
        List<Map<String, String>> waitingEmails = notionService.getWaitingEmailsFromNotion(userId);

        if (waitingEmails.isEmpty()) {
            log.info("재처리할 'Waiting' 이메일이 없습니다.");
            return;
        }

        for (Map<String, String> emailInfo : waitingEmails) {
            String pageId = emailInfo.get("pageId");
            String subject = emailInfo.get("subject");
            String from = emailInfo.get("from");
            String date = emailInfo.get("date");
            String content = emailInfo.get("content");

            // Notion 이메일에서 URL 추출
            List<String> urls = UrlExtractor.extractUrls(content);
            if (urls.isEmpty()) {
                System.out.println("Notion Page ID: " + pageId + "에서 추출할 URL이 없습니다.");
                notionService.updateNotionTags(userId, pageId, List.of("No URLs"), 0, "URL이 감지되지 않았습니다.");
                continue;
            }

            // 재처리 시점에서는 원본 메일 UID 모르니(사용할 이유도 없음) pageId 사용
            UrlAnalysisRequest request = new UrlAnalysisRequest(userId, pageId, pageId, urls, subject, from, date, "INITIAL");

            redisCacheService.enqueueUrlAnalysis(request);
            log.info("Notion Page ID: {}의 URL을 재처리 큐에 추가했습니다: {}", pageId, urls);
        }
    }

    // 수신 날짜 표준화
    public static String toIso8601(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC")); // 또는 원하는 타임존
        return sdf.format(date);
    }
}
