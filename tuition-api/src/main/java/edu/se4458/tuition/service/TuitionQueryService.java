package edu.se4458.tuition.service;

import edu.se4458.tuition.domain.Money;
import edu.se4458.tuition.domain.Student;
import edu.se4458.tuition.domain.Terms;
import edu.se4458.tuition.domain.Tuition;
import edu.se4458.tuition.dto.TuitionQueryResponse;
import edu.se4458.tuition.dto.TuitionQueryResponse.CurrencyTotal;
import edu.se4458.tuition.dto.TuitionQueryResponse.TermTuition;
import edu.se4458.tuition.error.NotFoundException;
import edu.se4458.tuition.repository.StudentRepository;
import edu.se4458.tuition.repository.TuitionRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Query Tuition, shared by the mobile and banking endpoints. */
@Service
public class TuitionQueryService {

    private final StudentRepository studentRepository;
    private final TuitionRepository tuitionRepository;

    public TuitionQueryService(StudentRepository studentRepository, TuitionRepository tuitionRepository) {
        this.studentRepository = studentRepository;
        this.tuitionRepository = tuitionRepository;
    }

    /**
     * @param term a term such as {@code 2026-FALL}, or {@code null} for every term plus overall totals
     */
    @Transactional(readOnly = true)
    public TuitionQueryResponse query(String studentNo, String term) {
        InputValidator.studentNo(studentNo);
        if (term != null) {
            InputValidator.term(term);
        }

        Student student = studentRepository.findById(studentNo)
                .orElseThrow(() -> new NotFoundException("student-not-found",
                        "Student " + studentNo + " was not found"));

        List<Tuition> tuitions;
        if (term == null) {
            tuitions = tuitionRepository.findByStudentStudentNo(studentNo).stream()
                    .sorted(Comparator.comparing(Tuition::getTerm, Terms.CHRONOLOGICAL))
                    .toList();
        } else {
            Tuition tuition = tuitionRepository.findByStudentStudentNoAndTerm(studentNo, term)
                    .orElseThrow(() -> new NotFoundException("tuition-not-found",
                            "No tuition record for student " + studentNo + " in term " + term));
            tuitions = List.of(tuition);
        }

        List<TermTuition> terms = tuitions.stream()
                .map(t -> new TermTuition(t.getTerm(), t.getAmount(), t.getPaidAmount(), t.outstandingBalance(),
                        t.getCurrency()))
                .toList();

        return new TuitionQueryResponse(studentNo, student.getFullName(), term, terms, totalsPerCurrency(terms));
    }

    /** Sums per currency, so terms billed in different currencies are never added together. */
    private static List<CurrencyTotal> totalsPerCurrency(List<TermTuition> terms) {
        Map<String, BigDecimal[]> sums = new TreeMap<>();
        for (TermTuition t : terms) {
            BigDecimal[] s = sums.computeIfAbsent(t.currency(), c -> new BigDecimal[] {Money.ZERO, Money.ZERO});
            s[0] = s[0].add(t.amount());
            s[1] = s[1].add(t.paidAmount());
        }
        return sums.entrySet().stream()
                .map(e -> new CurrencyTotal(e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[0].subtract(e.getValue()[1])))
                .toList();
    }
}
