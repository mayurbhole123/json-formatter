package com.jsontools;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Lets the produced WAR be deployed to a standalone Tomcat as well as
 * being run directly with {@code mvn spring-boot:run}.
 */
public class ServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(JsonFormatterApplication.class);
    }
}
