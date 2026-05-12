package global.govstack.regbb.engine.api;

import java.util.Collections;
import java.util.List;

/**
 * Total fee for an application plus per-fee breakdown. Returned by
 * {@link DeterminantEvaluator#computeFees}.
 */
public final class FeeResult {

    public static final class Line {
        public final String feeId;        // mm_fee.id
        public final String feeCode;      // mm_fee.code
        public final String label;
        public final double amount;
        public final String currency;
        public Line(String feeId, String feeCode, String label, double amount, String currency) {
            this.feeId = feeId;
            this.feeCode = feeCode;
            this.label = label;
            this.amount = amount;
            this.currency = currency;
        }
    }

    public final double total;
    public final String currency;
    public final List<Line> lines;

    public FeeResult(double total, String currency, List<Line> lines) {
        this.total = total;
        this.currency = currency;
        this.lines = lines == null ? Collections.emptyList() : Collections.unmodifiableList(lines);
    }
}
