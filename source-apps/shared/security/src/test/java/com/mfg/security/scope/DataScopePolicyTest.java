package com.mfg.security.scope;
import com.mfg.security.principal.LoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class DataScopePolicyTest {
    JdbcTemplate db=mock(JdbcTemplate.class);
    DataScopePolicy policy=new DataScopePolicy(db);
    LoginUser user(String type,String value){return new LoginUser(1L,"alice","Alice","SALES","Sales",type,value,Set.of("SALES_REP"),Set.of(),Set.of("crm"));}
    @Test void selfUsesBoundOwnerAndDoesNotExposeOtherRows(){var f=policy.filter(ScopedResource.OPPORTUNITY,"t",user("SELF",null));assertEquals("t.owner_user=?",f.sql());assertEquals(List.of("alice"),f.parameters());}
    @Test void absentRoleRulesDeny(){assertEquals("1=0",policy.filter(ScopedResource.SALES_ORDER,"t",user("ROLE",null)).sql());}
    @Test void multipleRolesUseUnion(){when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("scope_type","SELF"),Map.of("scope_type","DEPT")));var f=policy.filter(ScopedResource.SALES_ORDER,"t",user("ROLE",null));assertTrue(f.sql().contains(" OR "));assertEquals(List.of("alice","SALES"),f.parameters());}
    @Test void configuredDepartmentListIsBound(){var f=policy.filter(ScopedResource.EQUIPMENT,"t",user("DEPT","WS01,WS02"));assertEquals("t.workshop_code IN (?,?)",f.sql());assertEquals(List.of("WS01","WS02"),f.parameters());}
    @Test void customerDepartmentUsesCreatorDepartment(){var f=policy.filter(ScopedResource.CUSTOMER,"",user("DEPT",null));assertTrue(f.sql().contains("scope_owner.username=src_mdm.md_customer.created_by"));assertEquals(List.of("SALES"),f.parameters());}
    @Test void customCodesDoNotCrossResourcesOrBecomeSql(){var f=policy.filter(ScopedResource.CUSTOMER,"t",user("CUSTOM","CUSTOMER:C001' OR 1=1--,SALES_ORDER:S001"));assertEquals("t.customer_code IN (?)",f.sql());assertEquals(List.of("C001' OR 1=1--"),f.parameters());assertEquals("1=0",policy.filter(ScopedResource.INSPECTION,"t",user("CUSTOM","CUSTOMER:C001")).sql());}
    @Test void adminIsUnrestricted(){var u=new LoginUser(1L,"admin","Admin",null,null,"NONE",null,Set.of("ADMIN"),Set.of(),Set.of());assertEquals("1=1",policy.filter(ScopedResource.CUSTOMER,"t",u).sql());}
    @Test void absentOwnerNeverTurnsSelfIntoAll(){assertEquals("1=0",policy.filter(ScopedResource.EQUIPMENT,"t",user("SELF",null)).sql());}
}
