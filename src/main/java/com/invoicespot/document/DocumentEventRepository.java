package com.invoicespot.document;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentEventRepository extends JpaRepository<DocumentEvent, Long> {

    @EntityGraph(attributePaths = "actor")
    List<DocumentEvent> findByDocumentPkidOrderByOccurredAtDescPkidDesc(Long documentPkid);
}
