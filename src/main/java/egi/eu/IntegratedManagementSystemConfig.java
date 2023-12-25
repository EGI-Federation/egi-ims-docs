package egi.eu;

import io.smallrye.config.ConfigMapping;
import jakarta.enterprise.context.ApplicationScoped;


/***
 * The configuration of the IMS
 */
@ConfigMapping(prefix = "egi.ims")
@ApplicationScoped
public interface IntegratedManagementSystemConfig {

    // Users must be members of this VO to use the IMS tools
    String vo();

    // Unused VO group
    String group();
}
