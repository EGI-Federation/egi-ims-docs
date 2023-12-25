package egi.eu;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import egi.eu.model.Role;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import egi.checkin.model.CheckinUser;


@QuarkusTest
public class DocRoleParsingTest {

    @Inject
    IntegratedManagementSystemConfig imsConfig;

    private static final Logger log = Logger.getLogger(DocRoleParsingTest.class);
    private String prefix;
    private final String postfix = "#aai.egi.eu";
    private CheckinUser userInfo;
    private QuarkusSecurityIdentity.Builder builder;
    private DocRoleCustomization roleCustomization;
    private ObjectMapper mapper = new ObjectMapper();


    @BeforeEach
    public void setup() {
        prefix = "urn:mace:egi.eu:group:" + imsConfig.vo() + ":";

        roleCustomization = new DocRoleCustomization();
        roleCustomization.setConfig(imsConfig);

        userInfo = new CheckinUser("e9c37aa0d1cf14c56e560f9f9915da6761f54383badb501a2867bc43581b835c@egi.eu");
        userInfo.addEntitlement("urn:mace:egi.eu:group:vo.access.egi.eu:role=member#aai.egi.eu");
        userInfo.addEntitlement("urn:mace:egi.eu:group:vo.access.egi.eu:role=vm_operator#aai.egi.eu");

        builder = QuarkusSecurityIdentity.builder();
        var principal = new QuarkusPrincipal("test");
        builder.setPrincipal(principal);
    }

    @Test
    @DisplayName("IMS_USER when VO member")
    public void testVoMembership() {
        // Setup entitlements
        userInfo.addEntitlement(prefix + "role=member" + postfix);

        try {
            builder.addAttribute("userinfo", mapper.writeValueAsString(userInfo));
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }

        // Parse roles from entitlements
        UniAssertSubscriber<Boolean> subscriber = this.roleCustomization.augment(this.builder.build(), null)
            .onItem().transform(id -> id.getRoles())
            .onItem().transform(roles -> {
                // Check that it has the correct role
                return roles.contains(Role.IMS_USER);
            })
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());

        subscriber
            .awaitItem()
            .assertItem(true);
    }

}
