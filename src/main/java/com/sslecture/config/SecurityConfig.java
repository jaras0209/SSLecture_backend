package com.sslecture.config;

import com.sslecture.common.api.ErrorCode;
import com.sslecture.common.api.JsonErrorWriter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 安全性設定：無狀態（JWT 以 Authorization 標頭傳送，不用 Cookie 與 Session）。
 *
 * <p>JWT 驗證過濾器與社群登入（oauth2Login）在實作認證功能時加入，見前端 docs/oauth2_social_login_spec.md 第 5 節。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /** 不需登入即可呼叫的 API（見 backend_api_spec.md 第 2 節）。 */
  private static final String[] PUBLIC_ENDPOINTS = {
    "/auth/login",
    "/auth/register",
    "/auth/refresh",
    "/auth/exchange",
    "/auth/password/forgot",
    "/auth/password/reset",
    "/login/oauth2/**",
    "/oauth2/**",
    "/actuator/health/**",
    "/actuator/info",
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/error"
  };

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      CorsConfigurationSource corsConfigurationSource,
      JsonErrorWriter errorWriter,
      @Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .exceptionHandling(e ->
            e.authenticationEntryPoint(
                    (request, response, ex) -> errorWriter.write(response, ErrorCode.UNAUTHORIZED))
                .accessDeniedHandler(
                    (request, response, ex) -> errorWriter.write(response, ErrorCode.FORBIDDEN)))
        .authorizeHttpRequests(auth -> {
          auth.requestMatchers(PUBLIC_ENDPOINTS).permitAll();
          if (h2ConsoleEnabled) {
            auth.requestMatchers("/h2-console/**").permitAll();
          }
          auth.anyRequest().authenticated();
        });

    if (h2ConsoleEnabled) {
      // H2 主控台以 iframe 呈現，只在本地開發開放同源嵌入
      http.headers(h -> h.frameOptions(f -> f.sameOrigin()));
    }
    return http.build();
  }

  /** CORS：只允許設定的前端來源，並讓前端讀得到 Retry-After（見 backend_api_spec.md「跨網域」）。 */
  @Bean
  CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(properties.cors().allowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setExposedHeaders(List.of("Retry-After"));
    config.setAllowCredentials(false);
    config.setMaxAge(600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }
}
