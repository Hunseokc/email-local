package com.dorazibe02.imap.Controller.Page;

import com.dorazibe02.imap.Member.MemberService;
import com.dorazibe02.imap.Notion.NotionQueryService;
import com.dorazibe02.imap.Redis.RedisCacheService;
import com.dorazibe02.imap.Setting.ThreatAction;
import com.dorazibe02.imap.Setting.UserSetting;
import com.dorazibe02.imap.Setting.UserSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;


@Controller
@RequiredArgsConstructor
@RequestMapping("/email")
public class EmailPageController {
    private final MemberService memberService;
    private final NotionQueryService notionQueryService;
    private final UserSettingRepository userSettingRepository;
    private final RedisCacheService redisCacheService;

    @GetMapping("/list")
    public String list(Model model, @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        if (userDetails == null) {
            return "redirect:/auth/login";
        }
        long userId = memberService.getUserIdByEmail(userDetails.getUsername());
        boolean isConnect = notionQueryService.isConnect(userId);

        model.addAttribute("userId", userId);
        model.addAttribute("email", userDetails.getUsername());
        model.addAttribute("isConnect", isConnect);
        return "list.html";
    }

    @GetMapping("/log")
    public String mailLog() throws Exception {

        return "log-mail.html";
    }

    @GetMapping("/notion")
    public String notionRegister() {

        return "notion.html";
    }

    @GetMapping("/setting")
    public String settings(Model model, @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        long userId = memberService.getUserIdByAuth();
        String email = userDetails.getUsername();
        ThreatAction userAction = redisCacheService.getThreatAction(userId);
        Optional<UserSetting> userSetting = userSettingRepository.findByAuthId(userId);
        boolean scanSetting = false;

        if (userSetting.isPresent()) {
            scanSetting = userSetting.get().isAlwaysDetailedScan();
        }

        model.addAttribute("userId", userId);
        model.addAttribute("email", email);
        model.addAttribute("currentThreatAction", userAction);
        model.addAttribute("isNotionConnected", notionQueryService.isConnect(userId));
        model.addAttribute("isAlwaysDetailedScanEnabled", scanSetting);

        return "setting.html";
    }
}
