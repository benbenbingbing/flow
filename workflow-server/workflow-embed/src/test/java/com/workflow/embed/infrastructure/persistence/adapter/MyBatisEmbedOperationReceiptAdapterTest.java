package com.workflow.embed.infrastructure.persistence.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.workflow.embed.domain.EmbedOperationReceipt;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedOperationReceiptMapper;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class MyBatisEmbedOperationReceiptAdapterTest {

    @Test
    void uniqueIdempotencyReceiptConflictPropagatesToRollbackWorker() {
        EmbedOperationReceiptMapper mapper = mock(EmbedOperationReceiptMapper.class);
        when(mapper.insert(any())).thenThrow(
                new DuplicateKeyException("unique idempotency_record_id"));

        assertThrows(DuplicateKeyException.class,
                () -> new MyBatisEmbedOperationReceiptAdapter(mapper)
                        .insertInBusinessTransaction(receipt()));
    }

    @Test
    void insertRequiresExistingBusinessTransaction() throws Exception {
        Method method = MyBatisEmbedOperationReceiptAdapter.class.getMethod(
                "insertInBusinessTransaction", EmbedOperationReceipt.class);

        assertEquals(Propagation.MANDATORY,
                method.getAnnotation(Transactional.class).propagation());
    }

    private static EmbedOperationReceipt receipt() {
        return new EmbedOperationReceipt(
                "eor-1", "idem-1", "app-1", "EMBED_RECORD_CREATE",
                "actor-digest", "view-key", "RECORD", "record-1",
                "RECORD_CREATED", null,
                "{\"recordId\":\"record-1\",\"outcomeCode\":\"RECORD_CREATED\"}");
    }
}
