package com.dorazibe02.imap.Email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "mail-settings")
@Getter
@Setter
public class MailSettings {
    // email domain -> imap host
    private Map<String, String> imapHosts;
}
