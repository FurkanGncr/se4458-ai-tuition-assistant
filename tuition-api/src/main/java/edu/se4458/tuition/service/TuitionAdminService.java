package edu.se4458.tuition.service;

import edu.se4458.tuition.domain.Money;
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
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin operations: Add Tuition (upsert) and Unpaid Tuition Status. */
@Service
public class TuitionAdminService {

    public static final int MAX_PAGE_SIZE = 100;

    private final StudentRepository studentRepository;
    private final TuitionRepository tuitionRepository;

    public TuitionAdminService(StudentRepository studentRepository, TuitionRepository tuitionRepository) {
        this.studentRepository = studentRepository;
        this.tuitionRepository = tuitionRepository;
    }

    /**
     * Creates the (student, term) record, or updates its amount and currency if it exists.
     * Rules: the new amount may not be lower than the amount already paid, and the currency may change only
     * while nothing has been paid. Unknown students are created without a name.
     */
    @Transactional
    public AddTuitionResponse upsert(AddTuitionRequest request) {
        String studentNo = InputValidator.studentNo(request.studentNo());
        String term = InputValidator.term(request.term());
        BigDecimal amount = Money.positive(request.amount(), "amount");
        String currency = InputValidator.currency(request.currency());

        studentRepository.insertIfAbsent(studentNo);

        Optional<Tuition> existing = tuitionRepository.findForUpdate(studentNo, term);
        if (existing.isPresent()) {
            Tuition tuition = existing.get();
            BigDecimal paid = tuition.getPaidAmount();
            if (amount.compareTo(paid) < 0) {
                throw new ConflictException("amount-below-paid",
                        "New amount " + amount.toPlainString() + " is lower than the amount already paid "
                                + paid.toPlainString() + " for student " + studentNo + " in term " + term);
            }
            if (!tuition.getCurrency().equals(currency) && paid.signum() > 0) {
                throw new ConflictException("currency-change-not-allowed",
                        "Currency cannot change from " + tuition.getCurrency() + " to " + currency
                                + " because payments exist for student " + studentNo + " in term " + term);
            }
            tuition.update(amount, currency);
            return toResponse(AddTuitionResponse.Status.UPDATED, studentNo, tuition);
        }

        Tuition tuition = new Tuition(studentRepository.getReferenceById(studentNo), term, amount, currency);
        try {
            tuitionRepository.saveAndFlush(tuition);
        } catch (DataIntegrityViolationException e) {
            // Another request created the same (student, term) record at the same time.
            throw new ConflictException("concurrent-update",
                    "Tuition for student " + studentNo + " in term " + term
                            + " was created by another request at the same time; retry the request");
        }
        return toResponse(AddTuitionResponse.Status.CREATED, studentNo, tuition);
    }

    /**
     * @param page 1-based page number
     */
    @Transactional(readOnly = true)
    public UnpaidTuitionPage listUnpaid(String term, int page, int pageSize) {
        InputValidator.term(term);
        if (page < 1) {
            throw new InvalidInputException("page must be 1 or greater");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new InvalidInputException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        Page<UnpaidTuitionItem> result = tuitionRepository.findUnpaidByTerm(term, PageRequest.of(page - 1, pageSize));
        return new UnpaidTuitionPage(term, page, pageSize, result.getTotalElements(), result.getTotalPages(),
                result.getContent());
    }

    private static AddTuitionResponse toResponse(AddTuitionResponse.Status status, String studentNo, Tuition t) {
        return new AddTuitionResponse(status, studentNo, t.getTerm(), t.getAmount(), t.getPaidAmount(),
                t.outstandingBalance(), t.getCurrency());
    }
}
