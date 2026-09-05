package app.kitapla.config;

import app.kitapla.repo.UserRepository;
import app.kitapla.security.FreshPrincipalFilter;
import app.kitapla.security.LoginAttemptHandlers;
import app.kitapla.security.LoginAttemptService;
import app.kitapla.security.LoginRateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    /**
     * Mobil ve REST istemcileri için API güvenlik zinciri.
     * CSRF devre dışıdır; yetkisiz erişimlerde 302 HTML yerine 401 JSON döner.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http, UserRepository users,
                                             SessionRegistry sessionRegistry) throws Exception {
        http
            .securityMatcher("/api/v1/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .maximumSessions(-1)
                .sessionRegistry(sessionRegistry)
            )
            .addFilterBefore(new FreshPrincipalFilter(users), AuthorizationFilter.class)
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET,
                        "/api/v1/features",
                        "/api/v1/pickup-points",
                        "/api/v1/donations",
                        "/api/v1/donations/**",
                        "/api/v1/requests/open",
                        "/api/v1/swap/discover",
                        "/api/v1/books/**").permitAll()
                .requestMatchers("/api/v1/auth/**", "/api/v1/books/preview").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            );

        return http.build();
    }

    /**
     * Web (Thymeleaf/HTML) arayüzü için güvenlik zinciri.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http, UserRepository users,
                                           LoginAttemptService attempts,
                                           SessionRegistry sessionRegistry) throws Exception {
        http
            .sessionManagement(session -> session
                .maximumSessions(-1)
                .sessionRegistry(sessionRegistry)
                .expiredUrl("/login?oturum-doldu")
            )
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
                        "/sifremi-unuttum", "/sifre-sifirla",
                        "/css/**", "/js/**", "/webjars/**", "/uploads/covers/**",
                        "/favicon.ico", "/error", "/saglik").permitAll()
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
