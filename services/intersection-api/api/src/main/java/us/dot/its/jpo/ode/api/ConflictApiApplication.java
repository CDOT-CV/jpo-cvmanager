package us.dot.its.jpo.ode.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableWebMvc
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = { "us.dot.its.jpo.ode.api", "us.dot.its.jpo.geojsonconverter.validator" })
public class ConflictApiApplication extends SpringBootServletInitializer {

    private static final Logger logger = LoggerFactory.getLogger(ConflictApiApplication.class);

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(ConflictApiApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(ConflictApiApplication.class, args);
        logger.info("Started Conflict Monitor API");
        logger.info("Conflict Monitor API docs page found here: http://localhost:8089/swagger-ui/index.html");
        logger.info("Startup Complete");
    }
}
