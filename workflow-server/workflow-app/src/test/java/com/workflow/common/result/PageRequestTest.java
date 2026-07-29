package com.workflow.common.result;

import com.workflow.core.result.PageRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageRequestTest {

    @Test
    void normalizesInvalidInputAndCapsPageSize() {
        PageRequest page = PageRequest.normalize(-10, Integer.MAX_VALUE, 10, 100);

        assertEquals(1, page.pageNumber());
        assertEquals(100, page.pageSize());
        assertEquals(0, page.offset());
    }

    @Test
    void handlesAnOffsetPastTheAvailableRecords() {
        PageRequest page = PageRequest.normalize(Integer.MAX_VALUE, 100, 10, 100);

        assertEquals(12, page.startIndex(12));
    }
}
