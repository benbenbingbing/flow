package com.workflow.admin.identity.provisioning.application;

import com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper.IdentitySourceSnapshotMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.identity.error.IdentityWriteException;
import com.workflow.contracts.identity.model.IdentityObjectType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 原文读取不能复用后台来源身份；权限和对象范围拒绝时，大文本 Mapper 完全不执行。 */
class IdentitySourceSnapshotAccessTest {
    final IdentitySourceSnapshotMapper mapper = mock(IdentitySourceSnapshotMapper.class);
    final IdentityAccessService access = mock(IdentityAccessService.class);
    final IdentitySourceSnapshotService service = new IdentitySourceSnapshotService(mapper, new IdentityWriteProperties(), access, mock(SystemAuditPort.class));

    @AfterEach void clear() { UserContext.clear(); }

    @Test void backgroundSourceCannotReadRawData() {
        UserContext.clear();
        assertEquals("FORBIDDEN", assertThrows(IdentityWriteException.class, () -> service.read(IdentityObjectType.USER, "id")).code());
        verifyNoInteractions(access, mapper);
    }

    @ParameterizedTest @EnumSource(IdentityObjectType.class)
    void permissionsThenObjectScopeAreRequiredBeforeReading(IdentityObjectType target) {
        UserContext.setCurrentUser("actor", "admin");
        String prefix = target == IdentityObjectType.USER ? "system:user:" : "system:organization:";
        doThrow(new IdentityWriteException("FORBIDDEN", "permission", "denied"))
                .when(access).requirePermission("actor", prefix + "manage");
        assertThrows(IdentityWriteException.class, () -> service.read(target, "id"));
        verifyNoInteractions(mapper);
        reset(access);
        doThrow(new IdentityWriteException("FORBIDDEN", "id", "scope denied")).when(access).requireObject(target, "id");
        assertThrows(IdentityWriteException.class, () -> service.read(target, "id"));
        verifyNoInteractions(mapper);
        reset(access);
        assertEquals("UNAVAILABLE", service.read(target, "id").state());
        var order = inOrder(access, mapper);
        order.verify(access).requirePermission("actor", prefix + "view");
        order.verify(access).requirePermission("actor", prefix + "manage");
        order.verify(access).requireObject(target, "id");
        order.verify(mapper).read(target, "id");
    }
}
