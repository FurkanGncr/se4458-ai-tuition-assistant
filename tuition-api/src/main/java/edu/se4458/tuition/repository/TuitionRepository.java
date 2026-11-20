package edu.se4458.tuition.repository;

import edu.se4458.tuition.domain.Tuition;
import edu.se4458.tuition.dto.UnpaidTuitionItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TuitionRepository extends JpaRepository<Tuition, Long> {

    List<Tuition> findByStudentStudentNo(String studentNo);

    Optional<Tuition> findByStudentStudentNoAndTerm(String studentNo, String term);

    /** Loads the tuition row with SELECT ... FOR UPDATE so concurrent payments/updates are serialized. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tuition t where t.student.studentNo = :studentNo and t.term = :term")
    Optional<Tuition> findForUpdate(@Param("studentNo") String studentNo, @Param("term") String term);

    /** Unpaid rows (balance > 0) of a term, ordered by student number. Uses the partial index. */
    @Query(value = """
            select new edu.se4458.tuition.dto.UnpaidTuitionItem(
                s.studentNo, s.fullName, t.amount, t.paidAmount, t.balance, t.currency)
            from Tuition t join t.student s
            where t.term = :term and t.balance > 0
            order by s.studentNo
            """,
            countQuery = "select count(t) from Tuition t where t.term = :term and t.balance > 0")
    Page<UnpaidTuitionItem> findUnpaidByTerm(@Param("term") String term, Pageable pageable);
}
