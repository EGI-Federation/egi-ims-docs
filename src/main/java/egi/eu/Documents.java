package egi.eu;

import com.google.api.services.drive.model.PermissionList;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.logging.Logger;
import io.smallrye.mutiny.Uni;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.security.identity.SecurityIdentity;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import static jakarta.ws.rs.core.MediaType.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import egi.checkin.model.CheckinUser;
import egi.eu.model.*;


/***
 * Resource for image queries and operations.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class Documents extends BaseResource {

    private static final Logger log = Logger.getLogger(Documents.class);
    private static final String APPLICATION_NAME = "EGI Document Service API";
    private static final String MEDIA_TYPE_GDOC = "application/vnd.google-apps.document";

    @Inject
    MeterRegistry registry;

    @Inject
    SecurityIdentity identity;

    @Inject
    IntegratedManagementSystemConfig imsConfig;

    @Inject
    GoogleConfig googleConfig;

    private static final JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
    private static final List<String> driveScopes = Collections.unmodifiableList(List.of(
                                DriveScopes.DRIVE_FILE,
                                DriveScopes.DRIVE_READONLY));

    // Parameter(s) to add to all endpoints
    @RestHeader(TEST_STUB)
    @Parameter(hidden = true)
    @Schema(defaultValue = "default")
    String stub;


    /***
     * Constructor
     */
    public Documents() { super(log); }

    /**
     * Creates a Google Drive service authorized with the configured service account.
     * @return Authorized {@link Drive} service object
     */
    private Uni<Drive> getDriveClient(NetHttpTransport transport) {

        Uni<Drive> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Open credentials file
                InputStream in = null;
                try {
                    in = new FileInputStream(new java.io.File(googleConfig.credentialsFile()));
                } catch(FileNotFoundException e) {
                    addToDC("credentialsFile", googleConfig.credentialsFile());
                    log.error("Credentials file not found");
                    return Uni.createFrom().failure(new ActionException(e, "invalidConfig"));
                }

                return Uni.createFrom().item(in);
            })
            .chain(stream -> {
                // Load service account secrets
                GoogleCredentials credentials = null;
                try {
                    credentials = ServiceAccountCredentials.fromStream(stream).createScoped(driveScopes);

                } catch(IOException e) {
                    addToDC("credentialsFile", googleConfig.credentialsFile());
                    log.error("Error reading from credentials file");
                    return Uni.createFrom().failure(new ActionException(e, "invalidConfig"));
                }

                return Uni.createFrom().item(credentials);
            })
            .chain(credentials -> {
                // Create authorized Google Drive API client
                var requestInitializer = new HttpCredentialsAdapter(credentials);
                Drive service = new Drive.Builder(transport, jsonFactory, requestInitializer)
                                            .setApplicationName(APPLICATION_NAME)
                                            .build();

                // Success
                return Uni.createFrom().item(service);
            })
            .onFailure().recoverWithUni(e -> {
                log.error("Error creating Google Drive client");
                return Uni.createFrom().failure(e);
            });

        return result;
    }

    /**
     * Create a Google document from HTML content.
     * @param auth The access token needed to call the service.
     * @return API Response, wraps an {@link DocumentInfo} or an ActionError entity
     */
    @POST
    @Path("/docs")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.IMS_USER)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "create", summary = "Create new Google document")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = DocumentInfo.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> create(@RestHeader(HttpHeaders.AUTHORIZATION) String auth, Document doc)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("processName", imsConfig.group());
        addToDC("doc", doc);

        log.info("Creating Google document");

        if(null == doc || null == doc.name || doc.name.isBlank()) {
            var ae = new ActionError("badRequest", "Document name is required");
            return Uni.createFrom().item(ae.toResponse());
        }

        if(null == doc.content || doc.content.isBlank()) {
            var ae = new ActionError("badRequest", "Document content is required");
            return Uni.createFrom().item(ae.toResponse());
        }

        if(null == doc.parentFolder || doc.parentFolder.isBlank()) {
            var ae = new ActionError("badRequest", "Document parent folder is required");
            return Uni.createFrom().item(ae.toResponse());
        }

        NetHttpTransport httpTransport = null;
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        } catch(IOException | GeneralSecurityException e) {
            log.error("Error creating HTTP transport");
            return Uni.createFrom().failure(new ActionException(e, "invalidConfig"));
        }

        var driveClient = new ArrayList<Drive>();
        Uni<Response> result = getDriveClient(httpTransport)

            .chain(drive -> {
                driveClient.add(drive);

                // Check that destination folder exists
                File folder = null;
                try {
                    folder = drive.files().get(doc.parentFolder)
                            .setSupportsTeamDrives(true)
                            .setFields("id,capabilities,owners")
                            .execute();
                } catch(IOException e) {
                    log.error("Cannot find folder");
                    return Uni.createFrom().failure(new ActionException(e, "notFound"));
                }

                // Check that we can create files there
                var caps = folder.getCapabilities();
                if(!caps.getCanAddChildren()) {
                    log.error("Cannot create files in folder");
                    return Uni.createFrom().failure(new ActionException("invalidConfig", "Cannot create files in folder"));
                }

                if(!caps.getCanModifyContent() || !caps.getCanEdit()) {
                    log.error("Cannot modify content");
                    return Uni.createFrom().failure(new ActionException("invalidConfig", "Cannot modify folder content"));
                }

                return Uni.createFrom().item(folder);
            })
            .chain(folder -> {
                var drive = driveClient.get(0);

                // Create new file
                File gdoc = null;
                File file = new File();
                file.setName(doc.name);
                file.setMimeType(MEDIA_TYPE_GDOC);
                file.setParents(Collections.singletonList("root"));

                ByteArrayInputStream bis = new ByteArrayInputStream(doc.content.getBytes(StandardCharsets.UTF_8));
                InputStreamContent content = new InputStreamContent(TEXT_HTML, bis);

                try {
                    gdoc = drive.files().create(file, content).execute();
                } catch(IOException e) {
                    log.error("Cannot create document");
                    return Uni.createFrom().failure(new ActionException(e, "cannotCreateDocument"));
                }

                return Uni.createFrom().item(gdoc);
            })
            .chain(file -> {
                var drive = driveClient.get(0);

                // Move file to specified destination
                // This will inherit sharing from the destination folder
                File gdoc = null;
                File update = new File();
                update.setName(doc.name);
                update.setParents(Collections.singletonList(doc.parentFolder));

                try {
                    gdoc = drive.files().copy(file.getId(), update)
                            .setSupportsTeamDrives(true)
                            .setFields("id,name,webViewLink")
                            .execute();
                } catch(IOException e) {
                    log.error("Cannot move document to destination folder");
                    return Uni.createFrom().failure(new ActionException(e, "cannotMoveToDestination"));
                }

                return Uni.createFrom().item(gdoc);
            })
            .chain(gdoc -> {
                // Google document created, success
                log.info("Document created");
                var docInfo = new DocumentInfo(gdoc);
                return Uni.createFrom().item(Response.ok(docInfo).status(Response.Status.CREATED).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Document creation failed");
                return new ActionError(e).toResponse();
            });

        return result;
    }
}
