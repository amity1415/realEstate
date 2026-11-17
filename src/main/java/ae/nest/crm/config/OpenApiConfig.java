package ae.nest.crm.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI nestOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NEST CRM API")
                        .version("v1")
                        .description(
                                "Private advisor CRM API (Milestone 1). Cookie-based session auth. "
                                        + "All lead/property access is workspace-scoped and enforced "
                                        + "server-side.")
                        .license(new License().name("Proprietary")));
    }
}
