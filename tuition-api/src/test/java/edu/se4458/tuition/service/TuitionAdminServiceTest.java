package edu.se4458.tuition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.se4458.tuition.domain.Student;
import edu.se4458.tuition.domain.Tuition;
import edu.se4458.tuition.dto.AddTuitionRequest;
import edu.se4458.tuition.dto.AddTuitionResponse;
import edu.se4458.tuition.dto.UnpaidTuitionItem;
import edu.se4458.tuition.dto.UnpaidTuitionPage;
import edu.se4458.tuition.error.ConflictException;
import edu.se4458.tuition.error.InvalidInputException;
import edu.se4458.tuition.repository.StudentRepository;
import edu.se4458.tuition.repository.TuitionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class TuitionAdminServiceTest {

    private static final Student STUDENT = new Student("20230005", null);

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private TuitionRepository tuitionRepository;

    @InjectMocks
    private TuitionAdminService service;

    // ---- Add Tuition (upsert) ----

    @Test
    void newRecord_isCreated_andUnknownStudentIsAutoCreated() {
        when(tuitionRepository.findForUpdate("20230005", "2026-FALL")).thenReturn(Optional.empty());
        when(studentRepository.getReferenceById("20230005")).thenReturn(STUDENT);

        AddTuitionResponse r = service.upsert(request("5000", "USD"));

        assertThat(r.status()).isEqualTo(AddTuitionResponse.Status.CREATED);
        assertThat(r.amount()).isEqualByComparingTo("5000.00");
        assertThat(r.paidAmount()).isEqualByComparingTo("0.00");
        assertThat(r.balance()).isEqualByComparingTo("5000.00");
        verify(studentRepository).insertIfAbsent("20230005");
        ArgumentCaptor<Tuition> saved = ArgumentCaptor.forClass(Tuition.class);
        verify(tuitionRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getTerm()).isEqualTo("2026-FALL");
        assertThat(saved.getValue().getCurrency()).isEqualTo("USD");
    }

    @Test
    void amountIsRoundedHalfUpToTwoDecimals() {
        when(tuitionRepository.findForUpdate("20230005", "2026-FALL")).thenReturn(Optional.empty());
        when(studentRepository.getReferenceById("20230005")).thenReturn(STUDENT);

        AddTuitionResponse r = service.upsert(request("100.005", "USD"));

        assertThat(r.amount()).isEqualByComparingTo("100.01");
        assertThat(r.amount().scale()).isEqualTo(2);
    }

    @Test
    void existingRecord_isUpdated_andBalanceRecalculated() {
        Tuition existing = existing("5000.00", "2000.00", "USD");
        when(tuitionRepository.findForUpdate("20230005", "2026-FALL")).thenReturn(Optional.of(existing));

        AddTuitionResponse r = service.upsert(request("6000.00", "USD"));

        assertThat(r.status()).isEqualTo(AddTuitionResponse.Status.UPDATED);
        assertThat(r.amount()).isEqualByComparingTo("6000.00");
        assertThat(r.balance()).isEqualByComparingTo("4000.00");
        verify(tuitionRepository, never()).saveAndFlush(any());
    }

    @Test
    void amountBelowPaid_isRejectedWith409_andRecordUnchanged() {
        Tuition existing = existing("5000.00", "2000.00", "USD");
        when(tuitionRepository.findForUpdate("20230005", "2026-FALL")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.upsert(request("1999.99", "USD")))
                .isInstanceOf(ConflictException.class)
                .extracting("problemType").isEqualTo("amount-below-paid");
        assertThat(existing.getAmount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void currencyChange_isRejectedWith409_whenPaymentsExist() {
        when(tuitionRepository.findForUpdate("20230005", "2026-FALL"))
                .thenReturn(Optional.of(existing("5000.00", "2000.00", "USD")));

        assertThatThrownBy(() -> service.upsert(request("5000.00", "EUR")))
                .isInstanceOf(ConflictException.class)
                .extracting("problemType").isEqualTo("currency-change-not-allowed");
    }

    @Test
    void currencyChange_isAllowed_whenNothingPaid() {
        when(tuitionRepository.findForUpdate("20230005", "2026-FALL"))
                .thenReturn(Optional.of(existing("5000.00", "0.00", "USD")));

        AddTuitionResponse r = service.upsert(request("4500.00", "EUR"));

        assertThat(r.status()).isEqualTo(AddTuitionResponse.Status.UPDATED);
        assertThat(r.currency()).isEqualTo("EUR");
    }

    @Test
    void unknownCurrencyCode_isRejectedWith400() {
        assertThatThrownBy(() -> service.upsert(request("5000.00", "ABC")))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("ISO 4217");
        verifyNoInteractions(studentRepository, tuitionRepository);
    }

    @Test
    void concurrentCreate_isRejectedWith409() {
        when(tuitionRepository.findForUpdate("20230005", "2026-FALL")).thenReturn(Optional.empty());
        when(studentRepository.getReferenceById("20230005")).thenReturn(STUDENT);
        when(tuitionRepository.saveAndFlush(any(Tuition.class)))
                .thenThrow(new DataIntegrityViolationException("uk_tuitions_student_term"));

        assertThatThrownBy(() -> service.upsert(request("5000.00", "USD")))
                .isInstanceOf(ConflictException.class)
                .extracting("problemType").isEqualTo("concurrent-update");
    }

    // ---- Unpaid Tuition Status ----

    @Test
    void listUnpaid_mapsOneBasedPageToRepositoryPage() {
        List<UnpaidTuitionItem> items = List.of(new UnpaidTuitionItem("20230011", null, new BigDecimal("100.00"),
                new BigDecimal("0.00"), new BigDecimal("100.00"), "USD"));
        when(tuitionRepository.findUnpaidByTerm(eq("2026-FALL"), eq(PageRequest.of(1, 10))))
                .thenReturn(new PageImpl<>(items, PageRequest.of(1, 10), 11));

        UnpaidTuitionPage page = service.listUnpaid("2026-FALL", 2, 10);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.pageSize()).isEqualTo(10);
        assertThat(page.totalItems()).isEqualTo(11);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.items()).isEqualTo(items);
    }

    @Test
    void listUnpaid_rejectsInvalidPaging() {
        assertThatThrownBy(() -> service.listUnpaid("2026-FALL", 0, 20)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> service.listUnpaid("2026-FALL", 1, 101)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> service.listUnpaid("FALL-2026", 1, 20)).isInstanceOf(InvalidInputException.class);
        verifyNoInteractions(tuitionRepository);
    }

    private static Tuition existing(String amount, String paid, String currency) {
        Tuition t = new Tuition(STUDENT, "2026-FALL", new BigDecimal(amount), currency);
        if (new BigDecimal(paid).signum() > 0) {
            t.recordPayment(new BigDecimal(paid));
        }
        return t;
    }

    private static AddTuitionRequest request(String amount, String currency) {
        return new AddTuitionRequest("20230005", "2026-FALL", new BigDecimal(amount), currency);
    }
}
