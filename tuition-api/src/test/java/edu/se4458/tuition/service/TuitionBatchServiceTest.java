package edu.se4458.tuition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.se4458.tuition.dto.AddTuitionRequest;
import edu.se4458.tuition.dto.BatchResult;
import edu.se4458.tuition.error.ConflictException;
import edu.se4458.tuition.repository.StudentRepository;
import edu.se4458.tuition.repository.TuitionRepository;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Uses the real {@link TuitionAdminService} (with mocked repositories), so each row goes through the same
 * validation and business rules as the single Add Tuition endpoint.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TuitionBatchServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private TuitionRepository tuitionRepository;

    private TuitionAdminService adminService;
    private TuitionBatchService batchService;

    @BeforeEach
    void setUp() {
        when(tuitionRepository.findForUpdate(any(), any())).thenReturn(Optional.empty());
        adminService = new TuitionAdminService(studentRepository, tuitionRepository);
        batchService = new TuitionBatchService(adminService);
    }

    @Test
    void invalidRows_areReportedWithLineNumbers_andDoNotStopTheBatch() throws Exception {
        String csv = """
                studentNo,term,amount,currency
                20230010,2026-FALL,5000.00,USD
                20230011,2026-SUMMER,5000.00,USD
                20230012,2026-FALL,abc,USD
                20230013,2026-FALL,5000.00
                20230014,2027-SPRING,0,USD
                20230015,2027-SPRING,4200,EUR
                """;

        BatchResult r = batchService.importCsv(stream(csv));

        assertThat(r.status()).isEqualTo(BatchResult.Status.COMPLETED_WITH_ERRORS);
        assertThat(r.totalRows()).isEqualTo(6);
        assertThat(r.successCount()).isEqualTo(2);
        assertThat(r.failureCount()).isEqualTo(4);
        assertThat(r.errors()).extracting(BatchResult.RowError::row).containsExactly(3, 4, 5, 6);
        assertThat(r.errors().get(0).reason()).contains("term");
        assertThat(r.errors().get(1).reason()).contains("not a valid number");
        assertThat(r.errors().get(2).reason()).contains("expected 4 columns");
        assertThat(r.errors().get(3).reason()).contains("greater than 0");
        verify(studentRepository, times(2)).insertIfAbsent(any());
    }

    @Test
    void allValidRows_giveStatusCompleted() throws Exception {
        BatchResult r = batchService.importCsv(stream("""
                studentNo,term,amount,currency
                20230010,2026-FALL,5000.00,USD
                20230011,2026-FALL,5000.00,USD
                """));

        assertThat(r.status()).isEqualTo(BatchResult.Status.COMPLETED);
        assertThat(r.successCount()).isEqualTo(2);
        assertThat(r.errors()).isEmpty();
    }

    @Test
    void allRowsFailing_giveStatusFailed() throws Exception {
        TuitionAdminService failing = org.mockito.Mockito.mock(TuitionAdminService.class);
        when(failing.upsert(any(AddTuitionRequest.class)))
                .thenThrow(new ConflictException("amount-below-paid", "New amount is lower than the amount paid"));

        BatchResult r = new TuitionBatchService(failing).importCsv(stream("""
                studentNo,term,amount,currency
                20230001,2026-FALL,1.00,USD
                """));

        assertThat(r.status()).isEqualTo(BatchResult.Status.FAILED);
        assertThat(r.failureCount()).isEqualTo(1);
        assertThat(r.errors().get(0).row()).isEqualTo(2);
        assertThat(r.errors().get(0).reason()).contains("lower than the amount paid");
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
