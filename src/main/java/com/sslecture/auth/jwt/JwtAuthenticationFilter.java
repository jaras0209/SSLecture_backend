package com.sslecture.auth.jwt;

import com.sslecture.auth.session.RefreshToken;
import com.sslecture.auth.session.SessionService;
import com.sslecture.common.api.ErrorCode;
import com.sslecture.common.api.JsonErrorWriter;
import com.sslecture.user.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 驗證 {@code Authorization: Bearer <access_token>}。
 *
 * <p>每次請求都讀取工作階段與帳號（backend_api_spec.md「帳號變更時結束既有的登入」）：
 *
 * <ul>
 *   <li>帳號已停用 → {@code 403 ACCOUNT_DISABLED}
 *   <li>Token 過期、已登出、角色變更或密碼重設後的舊 Token → {@code 401 TOKEN_EXPIRED}，前端會呼叫 /auth/refresh
 *   <li>須先修改密碼 → 除了 {@code passwordChangeAllowed} 以外都回 {@code 403 PASSWORD_CHANGE_REQUIRED}
 * </ul>
 *
 * <p>沒帶 Token 時不處理，交給 Spring Security 對需登入的路徑回 401 UNAUTHORIZED。公開路徑不檢查 Token，避免前端帶著過期的 Token
 * 呼叫登入或換發時被擋下。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;
  private final SessionService sessionService;
  private final JsonErrorWriter errorWriter;
  private final RequestMatcher publicEndpoints;
  private final RequestMatcher passwordChangeAllowed;

  public JwtAuthenticationFilter(
      JwtService jwtService,
      SessionService sessionService,
      JsonErrorWriter errorWriter,
      RequestMatcher publicEndpoints,
      RequestMatcher passwordChangeAllowed) {
    this.jwtService = jwtService;
    this.sessionService = sessionService;
    this.errorWriter = errorWriter;
    this.publicEndpoints = publicEndpoints;
    this.passwordChangeAllowed = passwordChangeAllowed;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return publicEndpoints.matches(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      chain.doFilter(request, response);
      return;
    }

    JwtService.AccessClaims claims;
    try {
      claims = jwtService.parse(header.substring(BEARER_PREFIX.length()).trim());
    } catch (ExpiredJwtException e) {
      errorWriter.write(response, ErrorCode.TOKEN_EXPIRED);
      return;
    } catch (JwtException | IllegalArgumentException e) {
      errorWriter.write(response, ErrorCode.UNAUTHORIZED);
      return;
    }

    Optional<RefreshToken> found = sessionService.find(claims.sessionId());
    if (found.isEmpty() || !found.get().getUser().getId().equals(claims.userId())) {
      errorWriter.write(response, ErrorCode.UNAUTHORIZED);
      return;
    }
    RefreshToken session = found.get();
    User user = session.getUser();

    if (!user.isActive()) {
      errorWriter.write(response, ErrorCode.ACCOUNT_DISABLED);
      return;
    }
    // 閒置期限由 Refresh Token 把關；這裡只看是否已登出或帳號要求重新登入
    if (session.isRevoked()
        || session.getTokenVersion() != user.getTokenVersion()
        || claims.tokenVersion() != user.getTokenVersion()) {
      errorWriter.write(response, ErrorCode.TOKEN_EXPIRED);
      return;
    }
    if (user.isMustChangePassword() && !passwordChangeAllowed.matches(request)) {
      errorWriter.write(response, ErrorCode.PASSWORD_CHANGE_REQUIRED);
      return;
    }

    AuthenticatedUser principal =
        new AuthenticatedUser(user.getId(), session.getId(), user.getUsername(), user.getRole());
    var authentication =
        new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    chain.doFilter(request, response);
  }
}
