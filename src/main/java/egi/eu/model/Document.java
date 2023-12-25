package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonInclude;


/***
 * Document details necessary for creation
 */
public class Document {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String name;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String parentFolder;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String content;
}
