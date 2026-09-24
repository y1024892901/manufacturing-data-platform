package com.mfg.security.scope;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScopedQueryService {
    private final EntityManager em;
    private final DataScopePolicy policy;
    @Transactional(readOnly=true)
    @SuppressWarnings("unchecked")
    public <T> Page<T> page(ScopedResource resource,Class<T> type,int page,int size,String keyword,String status) {
        var filter=policy.filter(resource,"t");
        String where=" WHERE ("+filter.sql()+")";
        List<Object> values=new ArrayList<>(filter.parameters());
        if(keyword!=null&&!keyword.isBlank()) {where+=" AND (t."+resource.code+" LIKE ? OR t."+resource.label+" LIKE ?)";values.add("%"+keyword.trim()+"%");values.add("%"+keyword.trim()+"%");}
        if(status!=null&&!status.isBlank()){where+=" AND t."+resource.status+"=?";values.add(status);}
        Number total=(Number)bind(em.createNativeQuery("SELECT COUNT(*) FROM "+resource.table+" t"+where),values).getSingleResult();
        int p=Math.max(0,page-1),z=Math.min(200,Math.max(1,size));
        List<T> rows=bind(em.createNativeQuery("SELECT t.* FROM "+resource.table+" t"+where+" ORDER BY t.id DESC",type),values).setFirstResult(p*z).setMaxResults(z).getResultList();
        return new PageImpl<>(rows,PageRequest.of(p,z),total.longValue());
    }
    @Transactional(readOnly=true)
    public void requireVisible(ScopedResource resource,Long id) {
        var filter=policy.filter(resource,"t");List<Object> values=new ArrayList<>(filter.parameters());values.add(id);
        Number total=(Number)bind(em.createNativeQuery("SELECT COUNT(*) FROM "+resource.table+" t WHERE ("+filter.sql()+") AND t.id=?"),values).getSingleResult();
        if(total.longValue()==0)throw BizException.of(ErrorCode.DATA_SCOPE_DENIED,"记录不存在或超出可访问的数据范围");
    }
    private Query bind(Query query,List<Object> args){for(int i=0;i<args.size();i++)query.setParameter(i+1,args.get(i));return query;}
}
