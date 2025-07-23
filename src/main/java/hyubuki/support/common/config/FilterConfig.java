package hyubuki.support.common.config;

import hyubuki.support.common.filter.RequestAndResponseLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfig {

  @Bean
  public FilterRegistrationBean<RequestAndResponseLoggingFilter> loggingFilter() {
    FilterRegistrationBean<RequestAndResponseLoggingFilter> registrationBean = new FilterRegistrationBean<>();

    registrationBean.setFilter(new RequestAndResponseLoggingFilter());
    registrationBean.addUrlPatterns("/*");
    // 요청/응답의 전체 과정을 빠짐없이 기록하기 위해, 필터 체인에서 가장 높은 우선순위를 부여합니다.
    registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);

    return registrationBean;
  }
}