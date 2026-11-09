package com.uday.jobfinder.tracker;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/** One job you applied to (or plan to apply to). */
@Entity
@Table(name = "job_application")
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String role;

    @Column(length = 1000)
    private String link;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.APPLIED;

    private LocalDate appliedOn;

    /** When to follow up if nothing has happened. */
    private LocalDate followUpOn;

    @Column(length = 4000)
    private String notes;

    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public enum Status { WISHLIST, APPLIED, INTERVIEW, OFFER, REJECTED, GHOSTED }

    public Long getId() { return id; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDate getAppliedOn() { return appliedOn; }
    public void setAppliedOn(LocalDate appliedOn) { this.appliedOn = appliedOn; }
    public LocalDate getFollowUpOn() { return followUpOn; }
    public void setFollowUpOn(LocalDate followUpOn) { this.followUpOn = followUpOn; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getUpdatedAt() { return updatedAt; }
}
