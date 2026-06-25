package org.otp.dcas_banking_system.repository;

import org.otp.dcas_banking_system.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** Henuz yayinlanmamis event'leri eklenme sirasiyla getirir (sira korunmali) */
    List<OutboxEvent> findTop50ByPublishedAtIsNullOrderByIdAsc();
}
