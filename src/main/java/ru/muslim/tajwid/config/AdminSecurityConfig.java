package ru.muslim.tajwid.config;

import static org.springframework.security.config.Customizer.withDefaults;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.util.StringUtils;

@Configuration
@RequiredArgsConstructor
public class AdminSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(AdminSecurityConfig.class);
    private static final String ADMIN_ROLE = "ADMIN";

    private final TajwidBotProperties properties;
    private final AdminIpAllowlistFilter adminIpAllowlistFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        TajwidBotProperties.AdminSecurityProperties adminSecurity = properties.getAdminSecurity();

        http.csrf(AbstractHttpConfigurer::disable);
        http.httpBasic(withDefaults());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(auth -> {
            if (adminSecurity.isEnabled()) {
                auth.requestMatchers("/api/admin/**").hasRole(ADMIN_ROLE);
            } else {
                auth.requestMatchers("/api/admin/**").permitAll();
            }
            auth.anyRequest().permitAll();
        });
        http.addFilterBefore(adminIpAllowlistFilter, BasicAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        TajwidBotProperties.AdminSecurityProperties adminSecurity = properties.getAdminSecurity();

        String username = adminSecurity.getUsername();
        String password = adminSecurity.getPassword();

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalStateException("tajwid.admin-security.username/password must be configured");
        }

        if ("change-me".equals(password)) {
            log.warn("Default admin security password is used. Change tajwid.admin-security.password in production");
        }

        UserDetails adminUser = User.builder()
            .username(username)
            .password(passwordEncoder.encode(password))
            .roles(ADMIN_ROLE)
            .build();

        return new InMemoryUserDetailsManager(adminUser);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
