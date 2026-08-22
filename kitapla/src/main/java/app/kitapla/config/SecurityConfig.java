package app.kitapla.config;

import app.kitapla.repo.UserRepository;
import app.kitapla.security.FreshPrincipalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UserRepository users) throws Exception {
        http
            // Yetki kararı verilmeden önce kullanıcıyı tazele: yönetici işlemleri
            // (onay, yetki, askı) yeniden giriş beklemeden geçerli olsun.
            .addFilterBefore(new FreshPrincipalFilter(users), AuthorizationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // Herkese açık: tanıtım sayfaları, keşif ve kitap detayı, kimlik, statik dosyalar.
                // Öğrenci belgeleri BİLEREK dışarıda: yalnızca /admin ucundan erişilir.
                .requestMatchers("/", "/sss", "/kesfet/**", "/kesfet", "/kitap/**", "/istekler", "/register", "/login",
                        "/css/**", "/js/**", "/webjars/**", "/uploads/covers/**",
                        "/favicon.ico", "/error", "/h2/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .defaultSuccessUrl("/panom", true)
                .failureUrl("/login?error")
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
