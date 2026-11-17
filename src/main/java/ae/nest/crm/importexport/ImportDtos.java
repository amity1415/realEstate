package ae.nest.crm.importexport;

import java.util.List;

public final class ImportDtos {

    private ImportDtos() {}

    public record RowError(int index, String reason) {}

    public record DuplicateRow(int index, String email, String matchedLeadId) {}

    public record PreviewResult(
            int total,
            int valid,
            int toImport,
            List<RowError> invalid,
            List<DuplicateRow> duplicatesExisting,
            List<DuplicateRow> duplicatesInBatch,
            List<String> sampleNames) {}

    public record CommitResult(
            String importJobId,
            boolean idempotentReplay,
            int imported,
            int skippedDuplicates,
            int invalid,
            int total) {}
}
