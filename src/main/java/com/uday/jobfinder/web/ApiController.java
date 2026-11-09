package com.uday.jobfinder.web;

import com.uday.jobfinder.agent.JobFinderAgentService;
import com.uday.jobfinder.profile.ProfileService;
import com.uday.jobfinder.tracker.JobApplication;
import com.uday.jobfinder.tracker.TrackerService;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@Validated
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final JobFinderAgentService agent;
    private final TrackerService tracker;
    private final ProfileService profile;

    public ApiController(JobFinderAgentService agent, TrackerService tracker, ProfileService profile) {
        this.agent = agent;
        this.tracker = tracker;
        this.profile = profile;
    }

    // ---- Chat with the agents ----

    public record ChatRequest(@NotBlank String sessionId, @NotBlank String message) {}

    @PostMapping("/chat")
    public JobFinderAgentService.ChatReply chat(@RequestBody @Validated ChatRequest req) {
        return agent.chat(req.sessionId(), req.message());
    }

    // ---- Tracker ----

    public record NewApplication(@NotBlank String company, @NotBlank String role, String link,
                                 String notes, JobApplication.Status status) {}

    public record ApplicationPatch(JobApplication.Status status, String note, LocalDate followUpOn) {}

    @GetMapping("/applications")
    public List<JobApplication> list(@RequestParam(required = false) JobApplication.Status status) {
        return tracker.list(status);
    }

    @GetMapping("/applications/follow-ups")
    public List<JobApplication> followUps() {
        return tracker.followUpsDue();
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public JobApplication add(@RequestBody @Validated NewApplication a) {
        return tracker.add(a.company(), a.role(), a.link(), a.notes(), a.status());
    }

    @PatchMapping("/applications/{id}")
    public JobApplication update(@PathVariable long id, @RequestBody ApplicationPatch p) {
        return tracker.update(id, p.status(), p.note(), p.followUpOn());
    }

    @DeleteMapping("/applications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        tracker.delete(id);
    }

    // ---- Resume ----

    @GetMapping("/resume")
    public Map<String, String> resume() {
        return Map.of("text", profile.resume());
    }

    @PutMapping("/resume")
    public Map<String, String> saveResume(@RequestBody Map<String, String> body) {
        profile.saveResume(body.get("text"));
        return Map.of("text", profile.resume());
    }

    @PostMapping("/resume/pdf")
    public Map<String, String> uploadPdf(@RequestParam("file") MultipartFile file) throws IOException {
        return Map.of("text", profile.importPdf(file.getBytes()));
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("model", agent.model(), "apiKeySet", agent.apiKeyConfigured());
    }

    // ---- Errors ----

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> error(Exception e) {
        log.error("Request failed", e);
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", msg));
    }
}
