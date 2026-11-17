package ae.nest.crm.common;

import java.util.List;

/** Minimal RFC-4180 CSV writer with CSV formula-injection neutralisation. */
public final class CsvWriter {

    private final StringBuilder sb = new StringBuilder();

    public CsvWriter row(List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i)));
        }
        sb.append("\r\n");
        return this;
    }

    public String build() {
        return sb.toString();
    }

    private static String escape(String raw) {
        String value = raw == null ? "" : raw;
        // Neutralise formula injection: a leading formula trigger is prefixed with a single quote.
        if (!value.isEmpty()) {
            char first = value.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@'
                    || first == '\t' || first == '\r') {
                value = "'" + value;
            }
        }
        boolean mustQuote = value.contains(",") || value.contains("\"") || value.contains("\n")
                || value.contains("\r");
        if (mustQuote) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
