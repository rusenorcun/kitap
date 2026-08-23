package app.kitapla.config;

import app.kitapla.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import app.kitapla.security.FreshPrincipalFilter;
import app.kitapla.security.LoginAttemptHandlers;
import app.kitapla.security.LoginAttemptService;
import app.kitapla.security.LoginRateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UserRepository users,
                                           LoginAttemptService attempts) throws Exception {
        http
            // Yetki kararı verilmeden önce kullanıcıyı tazele: yönetici işlemleri
            // (onay, yetki, askı) yeniden giriş beklemeden geçerli olsun.
            .addFilterBefore(new FreshPrincipalFilter(users), AuthorizationFilter.class)
            // Kaba kuvvet denemeleri kimlik doğrulamaya ulaşmadan durdurulur
            .addFilterBefore(new LoginRateLimitFilter(attempts), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> {
                // H2 konsolu yalnızca açıkken (geliştirme) erişilebilir; üretimde kural hiç eklenmez
                if (h2ConsoleEnabled) auth.requestMatchers("/h2/**").permitAll();
                auth
                // Herkese açık: tanıtım sayfaları, keşif ve kitap detayı, kimlik, statik dosyalar.
                // Öğrenci belgeleri BİLEREK dışarıda: yalnızca /admin ucundan erişilir.
                .requestMatchers("/", "/sss", "/kurallar", "/gizlilik", "/iletisim",
                        "/kesfet/**", "/kesfet", "/kitap/**", "/istekler", "/register", "/login",
                        "/css/**", "/js/**", "/webjars/**", "/uploads/covers/**",
                        "/favicon.ico", "/error").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated();
            })
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(LoginAttemptHandlers.success(attempts))
                .failureHandler(LoginAttemptHandlers.failure(attempts))
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?cikis")
                .permitAll()
            )
            // H2 konsolu için (yalnızca dev)
            .csrf(csrf -> csrf.ignoringRequestMatchers(new AntPathRequestMatcher("/h2/**")))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}
