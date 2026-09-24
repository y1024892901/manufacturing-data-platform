package com.mfg.security.scope;

import com.mfg.common.api.DataVisibility;
import com.mfg.security.config.CurrentUser;
import com.mfg.security.principal.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DataScopePolicy implements DataVisibility {
    private final JdbcTemplate db;
    public Filter filter(String table,String alias) {
        for (ScopedResource r:ScopedResource.values()) if(r.table.equals(table)) return filter(r,alias);
        return new Filter("1=1",List.of());
    }
    public Filter filter(ScopedResource resource,String alias) {
        return filter(resource,alias,CurrentUser.find().orElse(null));
    }
    Filter filter(ScopedResource resource,String alias,LoginUser user) {
        if(alias!=null&&!alias.isEmpty()&&!alias.matches("[a-zA-Z][a-zA-Z0-9_]*")) throw new IllegalArgumentException("Invalid SQL alias");
        String prefix=alias==null||alias.isEmpty()?resource.table+".":alias+".";
        if(user==null||user.isAdmin()) return all(); // Authenticated API boundary is enforced separately.
        String override=user.getDataScopeType();
        if(override!=null&&!override.equals("ROLE")) return forType(override,resource,prefix,user,values(user));
        if(user.getRoleCodes().isEmpty()) return none();
        List<Object> args=new ArrayList<>(new TreeSet<>(user.getRoleCodes()));args.add(resource.name());
        String placeholders=String.join(",",Collections.nCopies(user.getRoleCodes().size(),"?"));
        var rows=db.queryForList("SELECT s.scope_type FROM mfg_auth.sys_data_scope s JOIN mfg_auth.sys_role r ON r.id=s.role_id WHERE r.role_code IN ("+placeholders+") AND s.resource_code=?",args.toArray());
        if(rows.isEmpty())return none(); // Missing rules never silently expose all records.
        List<String> clauses=new ArrayList<>();List<Object> params=new ArrayList<>();
        for(var row:rows){
            Filter f=forType(String.valueOf(row.get("scope_type")),resource,prefix,user,List.of());
            if(f.sql().equals("1=1")) return f;
            clauses.add("("+f.sql()+")");params.addAll(f.parameters());
        }
        return new Filter(String.join(" OR ",clauses),params);
    }
    private Filter forType(String type,ScopedResource r,String p,LoginUser user,List<String> values) {
        return switch(type){
            case "ALL" -> all();
            case "SELF" -> r.owner==null?none():new Filter(p+r.owner+"=?",List.of(user.getUsername()));
            case "DEPT" -> {
                List<String> departments=values.isEmpty()?(user.getDeptCode()==null?List.of():List.of(user.getDeptCode())):values;
                if(departments.isEmpty())yield none();
                String in=" IN ("+String.join(",",Collections.nCopies(departments.size(),"?"))+")";
                if(r.department!=null)yield new Filter(p+r.department+in,new ArrayList<>(departments));
                if(r.owner==null)yield none();
                yield new Filter("EXISTS (SELECT 1 FROM mfg_auth.sys_user scope_owner WHERE scope_owner.username="+p+r.owner+" AND scope_owner.dept_code"+in+")",new ArrayList<>(departments));
            }
            case "CUSTOM" -> {
                List<String> codes=values.stream().filter(v->v.startsWith(r.name()+":")).map(v->v.substring(r.name().length()+1)).filter(v->!v.isBlank()).toList();
                yield codes.isEmpty()?none():new Filter(p+r.code+" IN ("+String.join(",",Collections.nCopies(codes.size(),"?"))+")",new ArrayList<>(codes));
            }
            default -> none();
        };
    }
    private List<String> values(LoginUser user){return user.getDataScopeValue()==null?List.of():Arrays.stream(user.getDataScopeValue().split(",")).map(String::trim).filter(v->!v.isEmpty()).distinct().toList();}
    private Filter all(){return new Filter("1=1",List.of());}
    private Filter none(){return new Filter("1=0",List.of());}
}
