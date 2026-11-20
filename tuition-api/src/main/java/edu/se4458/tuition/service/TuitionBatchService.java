package edu.se4458.tuition.service;

import edu.se4458.tuition.dto.AddTuitionRequest;
import edu.se4458.tuition.dto.BatchResult;
import edu.se4458.tuition.error.ApiException;
import edu.se4458.tuition.error.InvalidInputException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Add Tuition – Batch. Not transactional on purpose: every row goes through {@link TuitionAdminService#upsert}
 * in its own transaction, so a failing row is rolled back alone and the other rows are still saved.
 */
@Service
public class TuitionBatchService {

    private static final Logger log = LoggerFactory.getLogger(TuitionBatchService.class);

    private final TuitionAdminService adminService;

    public TuitionBatchService(TuitionAdminService adminService) {
        this.adminService = adminService;
    }

    public BatchResult importCsv(InputStream input) throws IOException {
        List<CsvTuitionParser.Line> lines = CsvTuitionParser.parse(input);

        int success = 0;
        List<BatchResult.RowError> errors = new ArrayList<>();
        for (CsvTuitionParser.Line line : lines) {
            try {
                adminService.upsert(toRequest(line));
                success++;
            } catch (ApiException e) {
                errors.add(new BatchResult.RowError(line.lineNumber(), e.getMessage()));
            } catch (DataAccessException e) {
                log.warn("Database error on CSV line {}", line.lineNumber(), e);
                errors.add(new BatchResult.RowError(line.lineNumber(), "database error while saving the row"));
            }
        }

        BatchResult.Status status = errors.isEmpty() ? BatchResult.Status.COMPLETED
                : success == 0 ? BatchResult.Status.FAILED
                : BatchResult.Status.COMPLETED_WITH_ERRORS;
        return new BatchResult(status, lines.size(), success, errors.size(), errors);
    }

    private static AddTuitionRequest toRequest(CsvTuitionParser.Line line) {
        List<String> f = line.fields();
        if (f.size() != CsvTuitionParser.HEADER.size()) {
            throw new InvalidInputException("expected 4 columns (studentNo,term,amount,currency) but found "
                    + f.size());
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(f.get(2));
        } catch (NumberFormatException e) {
            throw new InvalidInputException("amount '" + f.get(2) + "' is not a valid number");
        }
        return new AddTuitionRequest(f.get(0), f.get(1), amount, f.get(3));
    }
}
