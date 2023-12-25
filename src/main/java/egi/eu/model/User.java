package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import egi.checkin.model.CheckinUser;


/**
 * Details of some user
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class User {

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public String checkinUserId = null;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String fullName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String email;


    /***
     * Constructor
     */
    public User() {}

    /***
     * Constructor
     */
    public User(String checkinUserId, String fullName, String email) {
        this.checkinUserId = checkinUserId;
        this.fullName = fullName;
        this.email = email;
    }

    /***
     * Copy constructor
     */
    public User(CheckinUser u) {
        super();

        if(null != u) {
            this.checkinUserId = u.checkinUserId;
            this.fullName = u.fullName;
            this.email = u.email;
        }
    }
}
