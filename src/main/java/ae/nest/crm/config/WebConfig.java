package ae.nest.crm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration. The frontend is served same-origin from {@code src/main/resources/static},
 * so no CORS is enabled by default; cross-origin access would have to be added explicitly and
 * scoped to an exact origin.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    // Same-origin by design. Intentionally no global CORS mapping.
}
