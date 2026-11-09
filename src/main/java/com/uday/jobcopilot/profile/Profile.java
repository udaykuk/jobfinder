package com.uday.jobcopilot.profile;

import jakarta.persistence.*;

/** Single-row table holding your resume text (the agents' source of truth about you). */
@Entity
public class Profile {

    public static final long ID = 1L;

    @Id
    private Long id = ID;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String resumeText = "";

    public Long getId() { return id; }
    public String getResumeText() { return resumeText; }
    public void setResumeText(String resumeText) { this.resumeText = resumeText; }
}
