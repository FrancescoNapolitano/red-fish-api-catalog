package it.fn.redfish.catalog.web.form;

import it.fn.redfish.catalog.domain.Contact;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ContactForm {

    @NotBlank(message = "{message.the.contact.name.is.required}")
    @Size(max = 160)
    private String name;

    @Email(message = "{message.invalid.email}")
    @Size(max = 200)
    private String email;

    @Size(max = 160)
    private String team;

    private String notes;

    public void applyTo(Contact contact) {
        contact.setName(name == null ? null : name.trim());
        contact.setEmail(blank(email) ? null : email.trim());
        contact.setTeam(blank(team) ? null : team.trim());
        contact.setNotes(blank(notes) ? null : notes.trim());
    }

    private boolean blank(String v) {
        return v == null || v.isBlank();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
