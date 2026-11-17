package ae.nest.crm;

import ae.nest.crm.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * NEST UAE advisor CRM.
 *
 * <p>A Spring Boot modular monolith serving an authenticated advisor UI and a versioned REST API,
 * persisting leads, interactions, tasks, authorised property inventory, coaching runs and
 * integrations (notifications, outbox, inbound sources) in PostgreSQL.
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class NestCrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(NestCrmApplication.class, args);
    }
}
