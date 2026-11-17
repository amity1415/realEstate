package ae.nest.crm.importexport;

import ae.nest.crm.importexport.ImportDtos.CommitResult;
import ae.nest.crm.importexport.ImportDtos.PreviewResult;
import ae.nest.crm.security.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ImportExportController {

    private final ImportService importService;
    private final ExportService exportService;
    private final CurrentUser currentUser;

    public ImportExportController(
            ImportService importService, ExportService exportService, CurrentUser currentUser) {
        this.importService = importService;
        this.exportService = exportService;
        this.currentUser = currentUser;
    }

    @PostMapping("/imports/leads/preview")
    public PreviewResult preview(@RequestBody JsonNode payload) {
        return importService.preview(currentUser.workspaceId(), currentUser.userId(), payload);
    }

    @PostMapping("/imports/leads/commit")
    public CommitResult commit(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody JsonNode payload) {
        return importService.commit(
                currentUser.workspaceId(), currentUser.userId(), idempotencyKey, payload);
    }

    @GetMapping("/exports/leads")
    public ResponseEntity<?> export(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        if ("json".equalsIgnoreCase(format)) {
            Map<String, Object> backup =
                    exportService.exportJson(currentUser.workspaceId(), includeArchived);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename("leads-backup.json").build()
                                    .toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(backup);
        }
        String csv = exportService.exportCsv(currentUser.workspaceId(), includeArchived);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("leads.csv").build().toString())
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
