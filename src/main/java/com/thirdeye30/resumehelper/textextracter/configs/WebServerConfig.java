package com.thirdeye30.resumehelper.textextracter.configs;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebServerConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> servletContainer() {
        return factory -> {
            // Add HTTP connector if needed
            factory.addAdditionalTomcatConnectors(HttpConnectorConfig.createHttpConnector());
        };
    }
}

