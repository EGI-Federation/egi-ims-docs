package egi.eu;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/***
 * The EGI Google Drive configuration
 */
@Schema(hidden=true)
@ConfigMapping(prefix = "egi.google")
public interface GoogleConfig {

    @WithName("id")
    String clientId();

    @WithName("email")
    String clientEmail();

    @WithName("credentials")
    String credentialsFile();

    @WithName("tokens")
    String tokensFolder();
}
