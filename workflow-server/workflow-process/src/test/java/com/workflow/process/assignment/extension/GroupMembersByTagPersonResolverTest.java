package com.workflow.process.assignment.extension;

import com.workflow.port.system.identity.model.GroupMemberFilter;
import com.workflow.port.system.SysUserGroupTagPort;
import com.workflow.contracts.process.assignment.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroupMembersByTagPersonResolverTest {
    SysUserGroupTagPort directory = mock(SysUserGroupTagPort.class);
    GroupMembersByTagPersonResolver resolver = new GroupMembersByTagPersonResolver(directory);

    @Test void keepsUnfilteredGroupsInUnionAndRejectsMalformedConditionsRatherThanTreatingAsWholeGroup() {
        assertEquals(List.of(new GroupMemberFilter("TECH",List.of("tag")),new GroupMemberFilter("FIN",List.of())),
                GroupMembersByTagPersonResolver.filters(Map.of("groups",List.of(Map.of("groupCode","TECH","tagIds",List.of("tag")),Map.of("groupCode","FIN","tagIds",List.of())))));
        for (Map<String,Object> params : List.<Map<String,Object>>of(Map.of(),Map.of("groups",List.of()),Map.of("groups",List.of(Map.of("groupCode","TECH"))),Map.of("groups",List.of(Map.of("groupCode","TECH","tagIds",List.of(2)))))) {
            assertThrows(IllegalArgumentException.class,()->GroupMembersByTagPersonResolver.filters(params));
        }
    }
    @Test void validatesCandidateModeOwnershipAndSupportsAllAssignmentUsages() {
        var params = Map.<String,Object>of("groups",List.of(Map.of("groupCode","TECH","tagIds",List.of("tag"))));
        resolver.validate(new PersonResolverConfigurationValidationRequest(PersonResolveUsage.ASSIGNEE,"CANDIDATE",false,params));
        verify(directory).validateTags(List.of(new GroupMemberFilter("TECH",List.of("tag"))));
        assertThrows(IllegalArgumentException.class,()->resolver.validate(new PersonResolverConfigurationValidationRequest(PersonResolveUsage.ASSIGNEE,"DIRECT",false,params)));
        assertEquals(Set.of(PersonResolveUsage.ASSIGNEE,PersonResolveUsage.CANDIDATE,PersonResolveUsage.MULTI_INSTANCE),resolver.descriptor().supportedUsages());
    }
}
