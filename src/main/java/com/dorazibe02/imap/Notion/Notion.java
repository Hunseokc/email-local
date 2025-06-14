package com.dorazibe02.imap.Notion;

import com.dorazibe02.imap.Auth.Auth;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
public class Notion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, unique = true)
    @Setter
    private String email;

    @Column
    @Setter
    private String api;

    @Setter
    @Getter
    private String notionDbId;

    @OneToOne
    @JoinColumn(name = "auth_id")
    @Setter
    private Auth auth;
}
