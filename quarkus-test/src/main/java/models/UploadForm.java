package controllers;

import java.util.Map;

public class UploadForm {
    private Map<String, String> formFields;

    public UploadForm() {
    }

    public Map<String, String> getFormFields() {
        return formFields;
    }

    public void setFormFields(Map<String, String> formFields) {
        this.formFields = formFields;
    }
}
