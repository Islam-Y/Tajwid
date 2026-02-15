package ru.muslim.tajwid.web;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.muslim.tajwid.service.AdminExportService;
import ru.muslim.tajwid.web.dto.AdminExportSnapshotResponse;

@RestController
@RequestMapping("/api/admin/export")
@RequiredArgsConstructor
public class AdminExportController {

    private final AdminExportService exportService;

    @GetMapping("/snapshot")
    public AdminExportSnapshotResponse snapshot() {
        return exportService.exportSnapshot();
    }

    @GetMapping(value = "/users.csv", produces = "text/csv")
    public ResponseEntity<String> usersCsv() {
        return csvResponse("tajwid-users.csv", exportService.exportUsersCsv());
    }

    @GetMapping(value = "/flow-contexts.csv", produces = "text/csv")
    public ResponseEntity<String> flowContextsCsv() {
        return csvResponse("tajwid-flow-contexts.csv", exportService.exportFlowContextsCsv());
    }

    @GetMapping(value = "/user-tags.csv", produces = "text/csv")
    public ResponseEntity<String> userTagsCsv() {
        return csvResponse("tajwid-user-tags.csv", exportService.exportUserTagsCsv());
    }

    @GetMapping(value = "/referral-link-usage.csv", produces = "text/csv")
    public ResponseEntity<String> referralLinkUsageCsv() {
        return csvResponse("tajwid-referral-link-usage.csv", exportService.exportReferralLinkUsagesCsv());
    }

    private ResponseEntity<String> csvResponse(String filename, String body) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(body);
    }
}
