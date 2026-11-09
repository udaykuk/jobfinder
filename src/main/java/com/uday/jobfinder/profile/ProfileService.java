package com.uday.jobfinder.profile;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final ProfileRepository repo;

    public ProfileService(ProfileRepository repo) {
        this.repo = repo;
    }

    public String resume() {
        return repo.findById(Profile.ID).map(Profile::getResumeText).orElse("");
    }

    @Transactional
    public void saveResume(String text) {
        Profile p = repo.findById(Profile.ID).orElseGet(Profile::new);
        p.setResumeText(text == null ? "" : text.strip());
        repo.save(p);
    }

    @Transactional
    public String importPdf(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            saveResume(text);
            return resume();
        }
    }
}
