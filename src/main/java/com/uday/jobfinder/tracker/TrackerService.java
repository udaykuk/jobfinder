package com.uday.jobfinder.tracker;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business logic for the application tracker, shared by the REST API and the agent tools. */
@Service
public class TrackerService {

    private static final List<JobApplication.Status> OPEN =
            List.of(JobApplication.Status.APPLIED, JobApplication.Status.INTERVIEW);

    private final JobApplicationRepository repo;
    private final int followUpDays;

    public TrackerService(JobApplicationRepository repo,
                          @Value("${jobfinder.follow-up-days:5}") int followUpDays) {
        this.repo = repo;
        this.followUpDays = followUpDays;
    }

    public List<JobApplication> list(JobApplication.Status status) {
        return status == null ? repo.findAllByOrderByUpdatedAtDesc()
                              : repo.findByStatusOrderByUpdatedAtDesc(status);
    }

    public List<JobApplication> followUpsDue() {
        return repo.findByStatusInAndFollowUpOnLessThanEqualOrderByFollowUpOn(OPEN, LocalDate.now());
    }

    @Transactional
    public JobApplication add(String company, String role, String link, String notes,
                              JobApplication.Status status) {
        JobApplication app = new JobApplication();
        app.setCompany(company.trim());
        app.setRole(role.trim());
        app.setLink(blankToNull(link));
        app.setNotes(blankToNull(notes));
        app.setStatus(status == null ? JobApplication.Status.APPLIED : status);
        if (app.getStatus() == JobApplication.Status.APPLIED) {
            app.setAppliedOn(LocalDate.now());
            app.setFollowUpOn(LocalDate.now().plusDays(followUpDays));
        }
        return repo.save(app);
    }

    @Transactional
    public JobApplication update(long id, JobApplication.Status status, String notes, LocalDate followUpOn) {
        JobApplication app = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No application with id " + id));
        if (status != null && status != app.getStatus()) {
            if (status == JobApplication.Status.APPLIED && app.getAppliedOn() == null) {
                app.setAppliedOn(LocalDate.now());
            }
            // Next step resets the follow-up clock; closed applications need no follow-up.
            app.setFollowUpOn(OPEN.contains(status) ? LocalDate.now().plusDays(followUpDays) : null);
            app.setStatus(status);
        }
        if (followUpOn != null) {
            app.setFollowUpOn(followUpOn);
        }
        if (notes != null && !notes.isBlank()) {
            app.setNotes(app.getNotes() == null ? notes.trim() : app.getNotes() + "\n" + notes.trim());
        }
        return repo.save(app);
    }

    @Transactional
    public void delete(long id) {
        repo.deleteById(id);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
