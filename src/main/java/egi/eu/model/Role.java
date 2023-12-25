package egi.eu.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;


/***
 * The roles that will govern access to the Messaging Service
 */
public class Role {

    // Abstract
    // Pseudo-roles that can be used in API endpoint annotations to define access,
    // but are not considered/returned by the API endpoints nor stored in Check-in
    public final static String IMS_USER = "ims"; // Marks membership in the VO

    @Schema(enumeration={ "Role" })
    public String kind = "Role";

    public String role; // One of the constants from above

    /***
     * Constructor
     */
    public Role() {}

    /***
     * Construct with a role
     */
    public Role(String role) {
        this.role = role;
    }
}
