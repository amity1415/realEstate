package ae.nest.crm.repo;

import ae.nest.crm.domain.CoachingFeedback;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachingFeedbackRepository extends JpaRepository<CoachingFeedback, UUID> {}
