package ae.nest.crm.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

    @Test
    void neutralisesFormulaInjection() {
        String csv = new CsvWriter().row(List.of("=cmd()", "+1", "-2", "@x", "safe")).build();
        assertThat(csv).startsWith("'=cmd(),'+1,'-2,'@x,safe");
    }

    @Test
    void quotesCommasQuotesAndNewlines() {
        String csv = new CsvWriter().row(List.of("a,b", "he said \"hi\"", "line1\nline2")).build();
        assertThat(csv).contains("\"a,b\"");
        assertThat(csv).contains("\"he said \"\"hi\"\"\"");
        assertThat(csv).contains("\"line1\nline2\"");
    }

    @Test
    void handlesNullAndEmpty() {
        String csv = new CsvWriter().row(java.util.Arrays.asList(null, "")).build();
        assertThat(csv).isEqualTo(",\r\n");
    }
}
