package com.dorazibe02.imap.Setting;

import com.dorazibe02.imap.Auth.Auth;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
public class UserSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Setter
    @Column(nullable = false)
    private boolean isNotionEnabled = false;

    @Setter
    @Column(nullable = false)
    private boolean isUrlCheckEnabled = false;

    @Setter
    @Column(nullable = false)
    private boolean isFileCheckEnabled = false;

    @Setter
    @Column(nullable = false)
    private boolean isAlwaysDetailedScan = false;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ThreatAction threatAction = ThreatAction.MOVE_TO_JUNK;  // 기본값은 정크로 이동

    @OneToOne
    @JoinColumn(name = "auth_id")
    @Setter
    private Auth auth;

    public UserSetting() {

    }
}
