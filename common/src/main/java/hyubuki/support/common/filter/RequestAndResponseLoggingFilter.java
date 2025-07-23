package hyubuki.support.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

public class RequestAndResponseLoggingFilter extends OncePerRequestFilter {

  private static final String GLOBAL_TRACE_ID = "global_traceId";
  private static final String LOCAL_TRACE_ID = "local_traceId";
  private static final String GLOBAL_TRACE_ID_HEADER = "X-Global-Trace-Id";
  private final Logger log = LoggerFactory.getLogger(RequestAndResponseLoggingFilter.class);

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
    ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
    ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

    String globalTraceId = request.getHeader(GLOBAL_TRACE_ID_HEADER);
    if (!StringUtils.hasText(globalTraceId)) {
      globalTraceId = UUID.randomUUID().toString().substring(0, 32);
    }
    MDC.put(GLOBAL_TRACE_ID, globalTraceId);

    final String localTraceId = UUID.randomUUID().toString().substring(0, 32);
    MDC.put(LOCAL_TRACE_ID, localTraceId);

    try {
      filterChain.doFilter(requestWrapper, responseWrapper);

      HttpLogMessage logMessage = new HttpLogMessage(
          requestWrapper.getMethod(),
          requestWrapper.getRequestURI(),
          HttpStatus.valueOf(responseWrapper.getStatus()),
          getRequestHeaders(requestWrapper),
          getRequestBody(requestWrapper),
          getResponseBody(responseWrapper)
      );

      log.info("\n{}", toPrettierLog(logMessage));

    } catch (Exception e) {
      handleException(e);
    } finally {
      try {
        // 기본적으로 Http ServletResponse는 body를 한번만 쓸 수 있기 때문에, 중간에서 응답 내용을 로깅하거나 가공하려면 직접 ContentCachingResponseWrapper로 복사본을 만들어야한다.
        responseWrapper.copyBodyToResponse();
      } catch (Exception copyException) {
        log.error("I/O Execption occurred while copying response body", copyException);
      }
      MDC.remove(GLOBAL_TRACE_ID);
      MDC.remove(LOCAL_TRACE_ID);
    }
  }

  private void handleException(final Exception e) {
    if (e instanceof IOException ioEx) {
      log.error("[HttpRequestAndResponseLoggingFilter] I/O exception occurred", ioEx);
      throw new RuntimeException("I/O error occurred while processing request/response", ioEx);
    } else if (e instanceof ServletException servletEx) {
      log.error("[HttpRequestAndResponseLoggingFilter] Servlet exception occurred", servletEx);
      throw new RuntimeException("Servlet error occurred while processing request/response", servletEx);
    } else {
      log.error("[HttpRequestAndResponseLoggingFilter] Unknown exception occurred", e);
      throw new RuntimeException("Unknown error occurred while processing request/response", e);
    }
  }

  private String toPrettierLog(final HttpLogMessage msg) {
    return """
        ==================================================
        [REQUEST] %s %s [RESPONSE - STATUS: %s]
        ==================================================
        >> HEADERS: %s \n
        >> REQUEST_BODY: %s
        >> RESPONSE_BODY: %s
        """.formatted(
        msg.httpMethod(), msg.url(), msg.httpStatus(),
        msg.headers(), msg.requestBody(), msg.responseBody()
    );
  }

  private String getRequestHeaders(final HttpServletRequest request) {
    return Collections.list(request.getHeaderNames()).stream()
        .map(name -> name + ": " + request.getHeader(name))
        .collect(Collectors.joining(";\n "));
  }

  private String getRequestBody(final ContentCachingRequestWrapper request) {
    byte[] buf = request.getContentAsByteArray();
    return (buf.length > 0) ? new String(buf, StandardCharsets.UTF_8) : "";
  }

  private String getResponseBody(final ContentCachingResponseWrapper response) {
    byte[] buf = response.getContentAsByteArray();
    return (buf.length > 0) ? new String(buf, StandardCharsets.UTF_8) : "";
  }
}
