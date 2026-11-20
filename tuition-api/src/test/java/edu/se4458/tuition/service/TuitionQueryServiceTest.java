package edu.se4458.tuition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.se4458.tuition.domain.Student;
import edu.se4458.tuition.domain.Tuition;
import edu.se4458.tuition.dto.TuitionQueryResponse;
import edu.se4458.tuition.dto.TuitionQueryResponse.CurrencyTotal;
import edu.se4458.tuition.error.InvalidInputException;
import edu.se4458.tuition.error.NotFoundException;
import edu.se4458.tuition.repository.StudentRepository;
import edu.se4458.tuition.repository.TuitionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TuitionQueryServiceTest {

    private static final Student STUDENT = new Student("20230004", "Can Ozturk");

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private TuitionRepository tuitionRepository;

    @InjectMocks
    private TuitionQueryService service;

    @Test
    void overallQuery_sortsTermsChronologically_andTotalsPerCurrency() {
        when(studentRepository.findById("20230004")).thenReturn(Optional.of(STUDENT));
        when(tuitionRepository.findByStudentStudentNo("20230004")).thenReturn(List.of(
                tuition("2027-SPRING", "3200.00", "0.00", "EUR"),
                tuition("2026-FALL", "3000.00", "1000.00", "USD"),
                tuition("2026-SPRING", "2000.00", "2000.00", "USD")));

        TuitionQueryResponse r = service.query("20230004", null);

        assertThat(r.term()).isNull();
        assertThat(r.fullName()).isEqualTo("Can Ozturk");
        assertThat(r.terms()).extracting(TuitionQueryResponse.TermTuition::term)
                .containsExactly("2026-SPRING", "2026-FALL", "2027-SPRING");
        assertThat(r.terms().get(1).balance()).isEqualByComparingTo("2000.00");
        assertThat(r.totals()).containsExactly(
                new CurrencyTotal("EUR", bd("3200.00"), bd("0.00"), bd("3200.00")),
                new CurrencyTotal("USD", bd("5000.00"), bd("3000.00"), bd("2000.00")));
    }

    @Test
    void termQuery_returnsOnlyThatTerm() {
        when(studentRepository.findById("20230004")).thenReturn(Optional.of(STUDENT));
        when(tuitionRepository.findByStudentStudentNoAndTerm("20230004", "2026-FALL"))
                .thenReturn(Optional.of(tuition("2026-FALL", "3000.00", "1000.00", "USD")));

        TuitionQueryResponse r = service.query("20230004", "2026-FALL");

        assertThat(r.term()).isEqualTo("2026-FALL");
        assertThat(r.terms()).hasSize(1);
        assertThat(r.totals()).containsExactly(new CurrencyTotal("USD", bd("3000.00"), bd("1000.00"), bd("2000.00")));
    }

    @Test
    void studentWithoutTuition_returnsEmptyLists() {
        when(studentRepository.findById("20230004")).thenReturn(Optional.of(STUDENT));
        when(tuitionRepository.findByStudentStudentNo("20230004")).thenReturn(List.of());

        TuitionQueryResponse r = service.query("20230004", null);

        assertThat(r.terms()).isEmpty();
        assertThat(r.totals()).isEmpty();
    }

    @Test
    void unknownStudent_throwsNotFound() {
        when(studentRepository.findById("99999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.query("99999999", null))
                .isInstanceOf(NotFoundException.class)
                .extracting("problemType").isEqualTo("student-not-found");
    }

    @Test
    void missingTerm_throwsNotFound() {
        when(studentRepository.findById("20230004")).thenReturn(Optional.of(STUDENT));
        when(tuitionRepository.findByStudentStudentNoAndTerm("20230004", "2028-FALL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.query("20230004", "2028-FALL"))
                .isInstanceOf(NotFoundException.class)
                .extracting("problemType").isEqualTo("tuition-not-found");
    }

    @Test
    void invalidTerm_throwsInvalidInput_withoutTouchingTheDatabase() {
        assertThatThrownBy(() -> service.query("20230004", "2026-SUMMER"))
                .isInstanceOf(InvalidInputException.class);
        verifyNoInteractions(studentRepository, tuitionRepository);
    }

    static Tuition tuition(String term, String amount, String paid, String currency) {
        Tuition t = new Tuition(STUDENT, term, bd(amount), currency);
        if (new BigDecimal(paid).signum() > 0) {
            t.recordPayment(bd(paid));
        }
        return t;
    }

    static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
