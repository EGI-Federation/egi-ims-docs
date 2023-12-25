package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

import egi.checkin.model.CheckinUser;


/***
 * The roles that will govern access to the features
 * in section Service Level Management
 */
public class RoleInfo extends Role {

    @Schema(description="Users that hold this role")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<User> users;


    /***
     * Constructor
     */
    public RoleInfo() {
        this.kind = "RoleInfo";
    }

    /***
     * Construct with a role
     */
    public RoleInfo(String role) {
        super(role);
        this.kind = "RoleInfo";
    }

    /***
     * Record that a user has this role assigned
     * @param user The user the role is assigned to
     */
    public RoleInfo addUser(User user) {
        if(null == this.users)
            this.users = new ArrayList<>();

        this.users.add(user);

        return this;
    }

    /***
     * Record that a user has this role assigned
     * @param user The user the role is assigned to
     */
    public RoleInfo addUser(CheckinUser user) {
        this.addUser(new User(user));
        return this;
    }
}
