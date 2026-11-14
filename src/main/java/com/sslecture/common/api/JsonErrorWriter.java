package com.sslecture.common.api;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 在 Spring MVC 之外（例如 Security 過濾器）以標準格式寫出錯誤回應。 */
@Component
public class JsonErrorWriter {

  private final ObjectMapper objectMapper;

  public JsonErrorWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void write(HttpServletResponse response, ErrorCode code) throws IOException {
    response.setStatus(code.status().value());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail(code));
  }
}
