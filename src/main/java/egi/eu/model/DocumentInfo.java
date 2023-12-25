package egi.eu.model;

import com.google.api.services.drive.model.File;
import com.fasterxml.jackson.annotation.JsonInclude;


/***
 * Details of a Google document
 */
public class DocumentInfo extends Document {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String id;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String url;

    /**
     * Construct from Google Drive file
     */
    public DocumentInfo(File file) {
        this.id = file.getId();
        this.name = file.getName();
        this.url = file.getWebViewLink();
    }
}
