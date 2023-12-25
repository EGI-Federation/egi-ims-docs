package egi.eu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import egi.eu.model.Role;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.oidc.runtime.AbstractJsonObjectResponse;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import egi.checkin.model.CheckinUser;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;


/***
 * Class to customize role identification from the user information
 * See also https://quarkus.io/guides/security-customization#security-identity-customization
 */
@ApplicationScoped
public class DocRoleCustomization implements SecurityIdentityAugmentor {

    private static final Logger log = Logger.getLogger(DocRoleCustomization.class);

    @Inject
    protected IntegratedManagementSystemConfig config;

    public void setConfig(IntegratedManagementSystemConfig config) {
        this.config = config;
    }

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        // NOTE: In case role parsing is a blocking operation, replace with the line below
        // return context.runBlocking(this.build(identity));
        return Uni.createFrom().item(this.build(identity));
    }

    private Supplier<SecurityIdentity> build(SecurityIdentity identity) {
        if(identity.isAnonymous()) {
            return () -> identity;
        } else {
            // Create a new builder and copy principal, attributes, credentials and roles from the original identity
            QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);

            log.debug("Building security identity");

            // Extract the OIDC user information, loaded due to the setting quarkus.roles.source=userinfo
            var ui = identity.getAttribute("userinfo");
            var isAJO = ui instanceof AbstractJsonObjectResponse;
            if(null != ui && (isAJO || ui instanceof String)) {
                // Construct Check-in UserInfo from the user info fetched by OIDC
                CheckinUser userInfo = null;
                String json = null;
                try {
                    var mapper = new ObjectMapper();
                    json = isAJO ? ((AbstractJsonObjectResponse)ui).getJsonObject().toString() : ui.toString();
                    userInfo = mapper.readValue(json, CheckinUser.class);

                    if(null != userInfo.checkinUserId)
                        builder.addAttribute(CheckinUser.ATTR_USERID, userInfo.checkinUserId);

                    if(null != userInfo.userName)
                        builder.addAttribute(CheckinUser.ATTR_USERNAME, userInfo.userName);

                    if(null != userInfo.firstName)
                        builder.addAttribute(CheckinUser.ATTR_FIRSTNAME, userInfo.firstName);

                    if(null != userInfo.lastName)
                        builder.addAttribute(CheckinUser.ATTR_LASTNAME, userInfo.lastName);

                    if(null != userInfo.fullName || null != userInfo.firstName || null != userInfo.lastName)
                        builder.addAttribute(CheckinUser.ATTR_FULLNAME, userInfo.getFullName());

                    if(null != userInfo.email)
                        builder.addAttribute(CheckinUser.ATTR_EMAIL, userInfo.email);

                    builder.addAttribute(CheckinUser.ATTR_EMAILCHECKED, userInfo.emailIsVerified);

                    if(null != userInfo.assurances) {
                        Pattern assuranceRex = Pattern.compile("^https?\\://(aai[^\\.]*.egi.eu)/LoA#([^\\:#/]+)");
                        for(var a : userInfo.assurances) {
                            var matcher = assuranceRex.matcher(a);
                            if(matcher.matches()) {
                                // Got an EGI Check-in backed assurance level
                                var assurance = matcher.group(2);
                                builder.addAttribute(CheckinUser.ATTR_ASSURANCE, assurance.toLowerCase());
                                break;
                            }
                        }
                    }
                }
                catch (JsonProcessingException e) {
                    // Error deserializing JSON info UserInfo instance
                    MDC.put("OIDC.userinfo", null != json ? json : "null");
                    log.warn("Cannot deserialize OIDC userinfo");
                }

                if(null != userInfo) {
                    // Got the Check-in user information, map roles
                    final String voPrefix = "urn:mace:egi.eu:group:" + config.vo().toLowerCase() + ":";
                    final String suffix = "#aai.egi.eu";

                    // Only continue checking the roles for members of the configured VO
                    if(userInfo.entitlements.contains(voPrefix + "role=member" + suffix)) {
                        // This user is member of the VO, access to ISM messaging is allowed
                        builder.addRole(Role.IMS_USER);
                    }
                }
            }

            return builder::build;
        }
    }
}
