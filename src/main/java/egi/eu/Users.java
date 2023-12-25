package egi.eu;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.logging.Logger;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import egi.checkin.CheckinConfig;
import egi.checkin.model.CheckinUser;
import egi.eu.model.*;


/***
 * Resource for user queries and operations.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class Users extends BaseResource {

    private static final Logger log = Logger.getLogger(Users.class);

    @Inject
    MeterRegistry registry;

    @Inject
    SecurityIdentity identity;

    @Inject
    CheckinConfig checkinConfig;

    @Inject
    IntegratedManagementSystemConfig imsConfig;

    // Parameter(s) to add to all endpoints
    @RestHeader(TEST_STUB)
    @Parameter(hidden = true)
    @Schema(defaultValue = "default")
    String stub;


    /***
     * Constructor
     */
    public Users() { super(log); }

    /**
     * Retrieve information about current user.
     * @param auth The access token needed to call the service.
     * @return API Response, wraps a {@link UserInfo} or an ActionError entity
     */
    @GET
    @Path("/user/info")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.IMS_USER)
    @Operation(operationId = "getUserInfo", summary = "Retrieve information about authenticated user")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = UserInfo.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> getUserInfo(@RestHeader(HttpHeaders.AUTHORIZATION) String auth)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("processName", imsConfig.group());

        log.info("Getting user info");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Get REST client for Check-in
                if (!checkin.init(this.checkinConfig, this.imsConfig, stub))
                    // Could not get REST client
                    return Uni.createFrom().failure(new ActionException("invalidConfig"));

                return Uni.createFrom().item(unused);
            })
            .chain(unused -> {
                // Get user info
                return this.checkin.getUserInfoAsync(auth);
            })
            .chain(checkinUser -> {
                // Got user info, success
                log.info("Got user info");

                var user = new UserInfo(checkinUser);
                return Uni.createFrom().item(Response.ok(user).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to get user info");
                return new ActionError(e, Tuple2.of("oidcInstance", this.checkinConfig.server())).toResponse();
            });

        return result;
    }
}
