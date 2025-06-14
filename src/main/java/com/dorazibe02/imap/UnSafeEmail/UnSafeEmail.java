package com.dorazibe02.imap.UnSafeEmail;

import com.dorazibe02.imap.Auth.Auth;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class UnSafeEmail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String fromAddress;

    @Column(nullable = false)
    private String receivedDate; // (ISO 8601)

    @Column(nullable = false)
    private String tag;

    @Column(nullable = false)
    private int threatScore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "authid", nullable = false)
    @Setter
    private Auth auth;
}
