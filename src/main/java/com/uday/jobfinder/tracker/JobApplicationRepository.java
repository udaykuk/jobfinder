package com.uday.jobfinder.tracker;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findAllByOrderByUpdatedAtDesc();

    List<JobApplication> findByStatusOrderByUpdatedAtDesc(JobApplication.Status status);

    List<JobApplication> findByStatusInAndFollowUpOnLessThanEqualOrderByFollowUpOn(
            List<JobApplication.Status> statuses, LocalDate date);
}
