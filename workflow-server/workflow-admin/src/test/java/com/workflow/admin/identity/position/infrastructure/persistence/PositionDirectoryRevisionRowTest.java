package com.workflow.admin.identity.position.infrastructure.persistence;

import com.workflow.admin.identity.position.infrastructure.persistence.record.PositionDirectoryRevisionRow;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PositionDirectoryRevisionRowTest {
    @Test void keepsSixFractionalDigitsAndNoAssignmentSentinel() {
        var row = new PositionDirectoryRevisionRow();
        row.setPositionRevision(2L);
        row.setOrganizationUpdatedAt(LocalDateTime.of(2026, 9, 22, 0, 3, 4, 123000000));
        row.setAssignmentCount(0L); row.setAssignmentRevision(0L);
        assertEquals("2:20260922000304.123000:0:0:0", row.toRevisionToken());
        row.setAssignmentCount(4L); row.setAssignmentRevision(7L);
        row.setAssignmentUpdatedAt(LocalDateTime.of(2026, 9, 22, 1, 2, 3, 123456789));
        assertEquals("2:20260922000304.123000:4:7:20260922010203.123456", row.toRevisionToken());
    }

    @Test void missingRequiredFactsDoNotProduceAValidRevision() {
        var row = new PositionDirectoryRevisionRow();
        assertNull(row.toRevisionToken());
        row.setPositionRevision(1L);
        assertNull(row.toRevisionToken());
        row.setPositionRevision(null); row.setOrganizationUpdatedAt(LocalDateTime.now());
        assertNull(row.toRevisionToken());
    }
}
