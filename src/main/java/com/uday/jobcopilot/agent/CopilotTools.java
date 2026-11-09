package com.uday.jobcopilot.agent;

import com.google.adk.tools.Annotations.Schema;
import com.uday.jobcopilot.profile.ProfileService;
import com.uday.jobcopilot.tracker.JobApplication;
import com.uday.jobcopilot.tracker.TrackerService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Java methods the agents are allowed to call. ADK reads the @Schema annotations to tell the
 * model what each tool does and which arguments it takes.
 */
@Component
public class CopilotTools {

    private final TrackerService tracker;
    private final ProfileService profile;

    public CopilotTools(TrackerService tracker, ProfileService profile) {
        this.tracker = tracker;
        this.profile = profile;
    }

    @Schema(name = "get_resume", description = "Returns the user's resume as plain text. "
            + "This is the only source of truth about the user's skills, projects and education.")
    public Map<String, Object> getResume() {
        String text = profile.resume();
        return text.isBlank()
                ? Map.of("status", "error", "message", "No resume saved yet. Ask the user to add it on the Resume tab.")
                : Map.of("status", "ok", "resume", text);
    }

    @Schema(name = "list_applications", description = "Lists tracked job applications, newest first. "
            + "Optionally filter by status: WISHLIST, APPLIED, INTERVIEW, OFFER, REJECTED or GHOSTED.")
    public Map<String, Object> listApplications(
            @Schema(name = "status", description = "Status filter, or empty for all", optional = true) String status) {
        List<JobApplication> apps;
        try {
            apps = tracker.list(parseStatus(status));
        } catch (IllegalArgumentException e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
        return Map.of("status", "ok", "today", LocalDate.now().toString(),
                "count", apps.size(), "applications", apps.stream().map(CopilotTools::toMap).toList());
    }

    @Schema(name = "add_application", description = "Saves a new job application to the tracker. "
            + "Use status APPLIED when the user says they applied, WISHLIST when they only want to apply later.")
    public Map<String, Object> addApplication(
            @Schema(name = "company", description = "Company name") String company,
            @Schema(name = "role", description = "Job title") String role,
            @Schema(name = "link", description = "Job posting URL", optional = true) String link,
            @Schema(name = "notes", description = "Any extra notes", optional = true) String notes,
            @Schema(name = "status", description = "APPLIED (default) or WISHLIST", optional = true) String status) {
        if (company == null || company.isBlank() || role == null || role.isBlank()) {
            return Map.of("status", "error", "message", "company and role are required");
        }
        try {
            JobApplication app = tracker.add(company, role, link, notes, parseStatus(status));
            return Map.of("status", "ok", "saved", toMap(app));
        } catch (IllegalArgumentException e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    @Schema(name = "update_application", description = "Updates an application's status and/or appends a note. "
            + "Call list_applications first to find the id.")
    public Map<String, Object> updateApplication(
            @Schema(name = "id", description = "Application id") String id,
            @Schema(name = "status", description = "New status: WISHLIST, APPLIED, INTERVIEW, OFFER, REJECTED or GHOSTED",
                    optional = true) String status,
            @Schema(name = "note", description = "Note to append, e.g. 'HR call on Friday'", optional = true) String note,
            @Schema(name = "follow_up_on", description = "Follow-up date as YYYY-MM-DD", optional = true) String followUpOn) {
        try {
            LocalDate date = followUpOn == null || followUpOn.isBlank() ? null : LocalDate.parse(followUpOn.trim());
            JobApplication app = tracker.update(Long.parseLong(id.trim()), parseStatus(status), note, date);
            return Map.of("status", "ok", "updated", toMap(app));
        } catch (RuntimeException e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    @Schema(name = "follow_ups_due", description = "Lists open applications whose follow-up date is today or earlier.")
    public Map<String, Object> followUpsDue() {
        List<JobApplication> due = tracker.followUpsDue();
        return Map.of("status", "ok", "today", LocalDate.now().toString(),
                "count", due.size(), "due", due.stream().map(CopilotTools::toMap).toList());
    }

    private static JobApplication.Status parseStatus(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return JobApplication.Status.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status '" + s + "'");
        }
    }

    private static Map<String, Object> toMap(JobApplication a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("company", a.getCompany());
        m.put("role", a.getRole());
        m.put("status", a.getStatus().name());
        m.put("link", a.getLink());
        m.put("applied_on", a.getAppliedOn() == null ? null : a.getAppliedOn().toString());
        m.put("follow_up_on", a.getFollowUpOn() == null ? null : a.getFollowUpOn().toString());
        m.put("notes", a.getNotes());
        return m;
    }
}
