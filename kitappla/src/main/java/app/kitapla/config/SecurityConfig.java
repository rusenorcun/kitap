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
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import app.kitapla.security.AppUserDetailsService;
import app.kitapla.security.KitapplaRememberMeServices;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import javax.sql.DataSource;

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

    @Value("${kitapla.security.remember-me.key:kitappla-super-secret-remember-me-key-change-in-prod}")
    private String rememberMeKey;

    @Value("${kitapla.security.remember-me.validity-days:30}")
    private int rememberMeValidityDays;

    @Value("${kitapla.security.remember-me.secure:false}")
    private boolean rememberMeSecure;

    @Bean
    public PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl repo = new JdbcTokenRepositoryImpl();
        repo.setDataSource(dataSource);
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS persistent_logins (" +
                    "username VARCHAR(100) NOT NULL, " +
                    "series VARCHAR(64) PRIMARY KEY, " +
                    "token VARCHAR(64) NOT NULL, " +
                    "last_used TIMESTAMP NOT NULL)");
        } catch (Exception ignored) {
            // Şema Flyway tarafından yönetiliyorsa veya tablo zaten varsa yok sayılır
        }
        return repo;
    }

    @Bean
    public KitapplaRememberMeServices rememberMeServices(AppUserDetailsService userDetailsService,
                                                         PersistentTokenRepository tokenRepository) {
        KitapplaRememberMeServices services = new KitapplaRememberMeServices(
                rememberMeKey, userDetailsService, tokenRepository);
        services.setTokenValiditySeconds(rememberMeValidityDays * 24 * 60 * 60);
        services.setUseSecureCookie(rememberMeSecure);
        return services;
    }

    @Bean
    public CsrfTokenRepository apiCsrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http, UserRepository users,
                                             SessionRegistry sessionRegistry,
                                             CsrfTokenRepository apiCsrfTokenRepository) throws Exception {
        http
            .securityMatcher("/api/v1/**")
            .csrf(csrf -> csrf.csrfTokenRepository(apiCsrfTokenRepository))
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
                .requestMatchers("/api/v1/auth/**").permitAll()
                // POST /api/v1/books/preview sunucuya dış adres indirttirir (OpenGraph).
                // SSRF doğrulayıcısı hedefi sınırlar ama kimliksiz bırakılırsa uç,
                // herkese açık bir "sunucudan getir" servisi olur. Giriş şartı aranır;
                // istemcide de yalnızca bağış oluştururken (oturum açıkken) çağrılıyor.
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
                                           SessionRegistry sessionRegistry,
                                           KitapplaRememberMeServices rememberMeServices) throws Exception {
        var sayfaIstekleri = LoginAttemptHandlers.sayfaIstekleri();
        http
            .requestCache(cache -> cache.requestCache(sayfaIstekleri))
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
                        "/favicon.ico", "/favicon.svg", "/error", "/saglik").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated();
            })
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(LoginAttemptHandlers.success(attempts, sayfaIstekleri))
                .failureHandler(LoginAttemptHandlers.failure(attempts))
                .permitAll()
            )
            .rememberMe(remember -> remember
                .rememberMeServices(rememberMeServices)
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?cikis")
                .deleteCookies("KITAPLA_SESSION", KitapplaRememberMeServices.REMEMBER_ME_COOKIE_NAME)
                .permitAll()
            )
            // H2 konsolu için (yalnızca dev)
            .csrf(csrf -> csrf.ignoringRequestMatchers(new AntPathRequestMatcher("/h2/**")))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}
