package com.dorazibe02.imap.Email;

import com.dorazibe02.imap.Auth.UserAuthCredentialService;
import com.dorazibe02.imap.Redis.RedisCacheService;
import com.dorazibe02.imap.Setting.ThreatAction;
import jakarta.mail.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class MailActionService {

    private final UserAuthCredentialService credentialService;
    private final RedisCacheService redisCacheService;
    private final EmailService emailService;

    public void processMailAction(long userId, long mailUid, int score, List<String> tags) {
        // 심각한 위협은 설정 넘기고 삭제
        boolean isHighRiskMalware = tags.stream().anyMatch(tag -> "MALWARE".equalsIgnoreCase(tag)) && score >= 90;
        ThreatAction userAction;

        if (isHighRiskMalware) {
            System.out.println("[Mail Action] High-priority rule triggered: High-risk malware detected. Overriding user settings.");
            userAction = ThreatAction.DELETE;
            return;
        } else {
            userAction = redisCacheService.getThreatAction(userId);

            if (userAction == null || score < 40 || userAction == ThreatAction.DO_NOTHING) {
                System.out.println("[Mail Action] No action required for mail UID: " + mailUid);
                return;
            }
            System.out.println("[Mail Action] Performing '" + userAction + "' for mail UID: " + mailUid);
        }

        Store store = null;
        Folder inbox = null;
        Folder junkFolder = null;

        try {
            String email = credentialService.getById(userId).getEmail();
            String password = credentialService.getDecryptedPassword(userId);
            String host = emailService.resolveImapHost(email);

            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            Session session = Session.getInstance(props);
            store = session.getStore("imaps");
            store.connect(host, email, password);

            inbox = store.getFolder("INBOX");

            UIDFolder uidFolder = (UIDFolder) inbox;
            inbox.open(Folder.READ_WRITE);

            Message messageToAction = uidFolder.getMessageByUID(mailUid);
            if (messageToAction == null) {
                System.err.println("[Mail Action] Could not find mail UID: " + mailUid);
                return;
            }

            // 설정에 따라서 분기로 처리
            switch (userAction) {
                case MOVE_TO_JUNK:
                    moveMessageToJunk(store, inbox, messageToAction);
                    break;
                case DELETE:
                    deleteMessagePermanently(messageToAction);
                    break;
            }

        } catch (Exception e) {
            System.err.println("[Mail Action] Failed to perform action on mail UID " + mailUid + " for user " + userId);
            e.printStackTrace();
        } finally {
            try {
                inbox = store != null ? store.getFolder("INBOX") : null;
                if (inbox != null && inbox.isOpen()) {
                    inbox.close(true);
                }
                if (store != null && store.isConnected()) {
                    store.close();
                }
            } catch (MessagingException e) {
                e.printStackTrace();
            }
        }
    }

    private void moveMessageToJunk(Store store, Folder inbox, Message message) throws MessagingException {
        Folder junkFolder = store.getFolder("Junk");
        if (!junkFolder.exists()) {
            junkFolder.create(Folder.HOLDS_MESSAGES);
        }
        inbox.copyMessages(new Message[]{message}, junkFolder);
        message.setFlag(Flags.Flag.DELETED, true);
        System.out.println("[Mail Action] Message moved to Junk");
    }

    private void deleteMessagePermanently(Message message) throws MessagingException {
        message.setFlag(Flags.Flag.DELETED, true);
        System.out.println("[Mail Action] Message marked for delete");
    }
}
