package ru.muslim.tajwid.config;

import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tajwid")
@Getter
@Setter
public class TajwidBotProperties {

    private String botUsername = "tajwid_bot";
    private String schoolChannelUrl = "https://t.me/tartil_madrasa";
    private String courseChannelUrl = "https://t.me/+KdrocizaxglkNzgy";
    private Duration flow1ToFlow2Delay = Duration.ofSeconds(10);
    private Duration flow2IntroDelay = Duration.ofSeconds(40);
    private TelegramProperties telegram = new TelegramProperties();
    private AdminSecurityProperties adminSecurity = new AdminSecurityProperties();

    @Getter
    @Setter
    public static class TelegramProperties {

        private boolean enabled;
        private String apiBaseUrl = "https://api.telegram.org";
        private String botToken = "";
        private String webhookSecret = "";
        private String webhookUrl = "";
        private String schoolChannelId = "@tartil_madrasa";
        private String courseChannelId = "";
    }

    @Getter
    @Setter
    public static class AdminSecurityProperties {

        private boolean enabled = true;
        private String username = "admin";
        private String password = "change-me";
        private List<String> allowedIps = List.of("127.0.0.1/32", "::1/128");
    }
}
