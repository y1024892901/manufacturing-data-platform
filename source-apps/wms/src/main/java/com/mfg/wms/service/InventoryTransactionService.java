package com.mfg.wms.service;

import com.mfg.common.api.ErrorCode;import com.mfg.common.exception.BizException;import com.mfg.security.config.CurrentUser;import com.mfg.wms.dto.InventoryActionCommand;import lombok.RequiredArgsConstructor;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import java.math.BigDecimal;import java.util.*;

/** 所有P3库存数量和质量状态变化的唯一写入口。 */
@Service @RequiredArgsConstructor
public class InventoryTransactionService {
 private final JdbcTemplate db;
 @Transactional public Map<String,Object> action(String type,InventoryActionCommand c){
  assertType(type);validate(c);if(exists(c.idempotencyKey()))return replay(type,c);Map<String,Object> old=lock(c.materialCode(),c.warehouseCode(),c.locationCode(),c.batchNo());
  if(exists(c.idempotencyKey()))return replay(type,c);
  BigDecimal qty=c.quantity(),on=d(old,"on_hand_qty"),available=d(old,"available_qty"),frozen=d(old,"frozen_qty");String before=String.valueOf(old.get("quality_status")),after=before;BigDecimal onDelta=BigDecimal.ZERO,availableDelta=BigDecimal.ZERO,frozenDelta=BigDecimal.ZERO;
  switch(type){case"FREEZE"->{require(available,qty,"可用库存不足，不能冻结");availableDelta=qty.negate();frozenDelta=qty;after="FROZEN";}case"UNFREEZE"->{require(frozen,qty,"冻结库存不足，不能解冻");availableDelta=qty;frozenDelta=qty.negate();after="AVAILABLE";}case"QUARANTINE"->{require(available,qty,"可用库存不足，不能隔离");availableDelta=qty.negate();after="QUARANTINED";}case"RELEASE"->{if(!Set.of("QUARANTINED","PENDING_INSPECTION","REWORK").contains(before))throw state("只有待检、隔离或返工库存可以放行");availableDelta=qty;after="AVAILABLE";}case"SCRAP","SUPPLIER_RETURN","ISSUE"->{require(on,qty,"现有库存不足，禁止负库存");if(available.compareTo(qty)>=0)availableDelta=qty.negate();else if(frozen.compareTo(qty)>=0)frozenDelta=qty.negate();else throw state("可用或冻结库存不足");onDelta=qty.negate();after=type.equals("SCRAP")?"SCRAPPED":type.equals("SUPPLIER_RETURN")?"RETURNED":before;}case"RETURN_MATERIAL","RECEIPT","PUTAWAY","COUNT_GAIN"->{onDelta=qty;availableDelta=qty;after="AVAILABLE";}case"COUNT_LOSS"->{require(on,qty,"盘亏数量超过现有库存");require(available,qty,"盘亏数量超过可用库存");onDelta=qty.negate();availableDelta=qty.negate();}default->throw state("不支持的库存动作");}
  db.update("UPDATE src_wms.wms_inventory SET on_hand_qty=on_hand_qty+?,available_qty=available_qty+?,frozen_qty=frozen_qty+?,quality_status=?,updated_at=NOW(3) WHERE id=?",onDelta,availableDelta,frozenDelta,after,old.get("id"));
  db.update("INSERT INTO src_wms.wms_inventory_ledger(idempotency_key,action_type,source_system,source_no,material_code,warehouse_code,location_code,batch_no,quality_status_before,quality_status_after,on_hand_delta,available_delta,frozen_delta,operator_code) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",c.idempotencyKey(),type,blank(c.sourceSystem(),"WMS"),c.sourceNo(),c.materialCode(),c.warehouseCode(),c.locationCode(),c.batchNo(),before,after,onDelta,availableDelta,frozenDelta,CurrentUser.usernameOrSystem());
  return ledger(c.idempotencyKey());
 }
 @Transactional public Map<String,Object> completePutaway(long id){
  Map<String,Object> p=one("SELECT * FROM src_wms.wms_putaway WHERE id=? FOR UPDATE",id);
  if("COMPLETED".equals(p.get("status")))return p;
  if(!"PROCESSING".equals(p.get("status")))throw state("只有上架中的单据可以完成上架");
  String destination=(String)p.get("actual_location");
  if(destination==null||destination.isBlank())throw state("请先填写实际库位");
  Map<String,Object> receipt=one("SELECT * FROM src_wms.wms_receipt WHERE receipt_no=? FOR UPDATE",p.get("receipt_no"));
  if(!"AVAILABLE".equals(receipt.get("status")))throw state("收货批次尚未通过质量检验，不能上架");
  String warehouse=String.valueOf(receipt.get("warehouse_code"));
  Integer locationCount=db.queryForObject("SELECT COUNT(*) FROM src_wms.wms_location WHERE location_code=? AND warehouse_code=? AND is_available=1",Integer.class,destination,warehouse);
  if(locationCount==null||locationCount==0)throw state("实际库位不存在、已停用或不属于收货仓库");
  BigDecimal quantity=d(p,"quantity");
  String material=String.valueOf(p.get("material_code")),batch=(String)p.get("batch_no");
  db.update("INSERT INTO src_wms.wms_inventory(material_code,warehouse_code,location_code,batch_no,on_hand_qty,available_qty,unit_code,quality_status,received_date) VALUES(?,?,?, ?,0,0,'PCS','AVAILABLE',CURDATE()) ON DUPLICATE KEY UPDATE material_code=VALUES(material_code)",material,warehouse,destination,batch);
  String no=String.valueOf(p.get("putaway_no"));
  InventoryActionCommand out=new InventoryActionCommand(no+":OUT",material,warehouse,null,batch,quantity,"WMS",no,"上架移出待上架库存");
  InventoryActionCommand in=new InventoryActionCommand(no+":IN",material,warehouse,destination,batch,quantity,"WMS",no,"上架至目标库位");
  action("ISSUE",out);
  action("PUTAWAY",in);
  db.update("UPDATE src_wms.wms_putaway SET status='COMPLETED',completed_at=NOW(3) WHERE id=?",id);
  return one("SELECT * FROM src_wms.wms_putaway WHERE id=?",id);
 }

 public List<Map<String,Object>> countLines(long id){
  one("SELECT id FROM src_wms.wms_count_plan WHERE id=?",id);
  return db.queryForList("SELECT * FROM src_wms.wms_count_line WHERE count_id=? ORDER BY id",id);
 }

 @Transactional public Map<String,Object> addCountLine(long id,Map<String,Object> input){
  Map<String,Object> plan=countPlanForLineEdit(id,true);
  String material=required(input,"materialCode","物料编码"),location=optional(input.get("locationCode")),batch=optional(input.get("batchNo"));
  Integer duplicate=db.queryForObject("SELECT COUNT(*) FROM src_wms.wms_count_line WHERE count_id=? AND material_code=? AND COALESCE(location_code,'')=COALESCE(?,'') AND COALESCE(batch_no,'')=COALESCE(?,'')",Integer.class,id,material,location,batch);
  if(duplicate!=null&&duplicate>0)throw state("同一库存维度已在盘点明细中");
  BigDecimal book=db.queryForObject("SELECT COALESCE(SUM(on_hand_qty),0) FROM src_wms.wms_inventory WHERE material_code=? AND warehouse_code=? AND COALESCE(location_code,'')=COALESCE(?,'') AND COALESCE(batch_no,'')=COALESCE(?,'')",BigDecimal.class,material,plan.get("warehouse_code"),location,batch);
  db.update("INSERT INTO src_wms.wms_count_line(count_id,material_code,location_code,batch_no,book_qty,actual_qty,difference_qty,review_status) VALUES(?,?,?,?,?,NULL,NULL,'PENDING')",id,material,location,batch,book==null?BigDecimal.ZERO:book);
  Long lineId=Objects.requireNonNull(db.queryForObject("SELECT LAST_INSERT_ID()",Long.class));
  return one("SELECT * FROM src_wms.wms_count_line WHERE id=?",lineId);
 }

 @Transactional public Map<String,Object> updateCountLine(long id,long lineId,BigDecimal actual){
  Map<String,Object> plan=countPlanForLineEdit(id,false);
  if(!"COUNTING".equals(plan.get("status")))throw state("只有盘点中的单据可以录入实盘数量");
  if(actual==null||actual.signum()<0||actual.stripTrailingZeros().scale()>4)throw state("实盘数量不能为负数且最多四位小数");
  Map<String,Object> line=one("SELECT * FROM src_wms.wms_count_line WHERE id=? AND count_id=? FOR UPDATE",lineId,id);
  BigDecimal difference=actual.subtract(d(line,"book_qty"));
  db.update("UPDATE src_wms.wms_count_line SET actual_qty=?,difference_qty=?,review_status='PENDING' WHERE id=?",actual,difference,lineId);
  return one("SELECT * FROM src_wms.wms_count_line WHERE id=?",lineId);
 }

 @Transactional public Map<String,Object> approveCountLine(long id,long lineId){
  Map<String,Object> plan=countPlanForLineEdit(id,false);
  if(!"REVIEWING".equals(plan.get("status")))throw state("请先将盘点单提交复核");
  Map<String,Object> line=one("SELECT * FROM src_wms.wms_count_line WHERE id=? AND count_id=? FOR UPDATE",lineId,id);
  if(line.get("actual_qty")==null)throw state("请先录入实盘数量");
  BigDecimal difference=d(line,"actual_qty").subtract(d(line,"book_qty"));
  db.update("UPDATE src_wms.wms_count_line SET difference_qty=?,review_status='APPROVED' WHERE id=?",difference,lineId);
  return one("SELECT * FROM src_wms.wms_count_line WHERE id=?",lineId);
 }

 @Transactional public void deleteCountLine(long id,long lineId){
  countPlanForLineEdit(id,true);
  if(db.update("DELETE FROM src_wms.wms_count_line WHERE id=? AND count_id=?",lineId,id)==0)throw BizException.notFound("盘点明细",lineId);
 }

 private Map<String,Object> countPlanForLineEdit(long id,boolean allowReleased){
  Map<String,Object> plan=one("SELECT * FROM src_wms.wms_count_plan WHERE id=? FOR UPDATE",id);
  String status=String.valueOf(plan.get("status"));
  boolean allowed=allowReleased?Set.of("DRAFT","RELEASED","COUNTING").contains(status):Set.of("COUNTING","REVIEWING").contains(status);
  if(!allowed)throw state("当前盘点单状态不允许维护盘点明细");
  return plan;
 }

 private String required(Map<String,Object> x,String key,String label){Object value=x.get(key);if(value==null||String.valueOf(value).isBlank())throw state(label+"不能为空");return String.valueOf(value).trim();}
 private String optional(Object value){return value==null||String.valueOf(value).isBlank()?null:String.valueOf(value).trim();}
 private void validate(InventoryActionCommand c){
  if(c==null||c.idempotencyKey()==null||c.idempotencyKey().isBlank()||c.idempotencyKey().length()>80)throw state("必须提供不超过80字符的业务幂等号");
  if(c.materialCode()==null||c.materialCode().isBlank()||c.warehouseCode()==null||c.warehouseCode().isBlank())throw state("物料和仓库不能为空");
  if(c.quantity()==null||c.quantity().signum()<=0||c.quantity().stripTrailingZeros().scale()>4)throw state("数量必须大于零且最多四位小数");
 }
 private Map<String,Object> replay(String type,InventoryActionCommand c){
  Map<String,Object> r=ledger(c.idempotencyKey());
  BigDecimal recorded=d(r,"on_hand_delta").abs().max(d(r,"available_delta").abs()).max(d(r,"frozen_delta").abs());
  if(!Objects.equals(type,r.get("action_type"))||!Objects.equals(c.materialCode(),r.get("material_code"))||!Objects.equals(c.warehouseCode(),r.get("warehouse_code"))
    ||!Objects.equals(blank(c.locationCode(),""),blank((String)r.get("location_code"),""))||!Objects.equals(blank(c.batchNo(),""),blank((String)r.get("batch_no"),""))
    ||!Objects.equals(blank(c.sourceSystem(),"WMS"),r.get("source_system"))||!Objects.equals(c.sourceNo(),r.get("source_no"))||recorded.compareTo(c.quantity())!=0)
   throw state("业务幂等号已使用，重复请求的动作、库存维度、来源和数量必须一致");
  return r;
 }
 @Transactional public Map<String,Object> executeTransfer(long id,String key){Map<String,Object>t=one("SELECT * FROM src_wms.wms_transfer WHERE id=? FOR UPDATE",id);String status=String.valueOf(t.get("status"));if("COMPLETED".equals(status)){if(Objects.equals(t.get("idempotency_key"),key))return t;throw state("调拨单已完成");}if(!"CONFIRMED".equals(status))throw state("只有已确认调拨单可以执行");BigDecimal q=d(t,"quantity");action("ISSUE",cmd(key+":OUT",t,"from_warehouse","from_location",q));action("RETURN_MATERIAL",cmd(key+":IN",t,"to_warehouse","to_location",q));db.update("UPDATE src_wms.wms_transfer SET status='COMPLETED',idempotency_key=? WHERE id=?",key,id);return one("SELECT * FROM src_wms.wms_transfer WHERE id=?",id);}
 @Transactional public Map<String,Object> transition(String table,long id,String target,Map<String,Set<String>> allowed){Map<String,Object>r=one("SELECT * FROM src_wms."+table+" WHERE id=? FOR UPDATE",id);String current=String.valueOf(r.get("status"));if(current.equals(target))return r;if(!allowed.getOrDefault(current,Set.of()).contains(target))throw state("不允许从"+current+"变更为"+target);db.update("UPDATE src_wms."+table+" SET status=? WHERE id=?",target,id);return one("SELECT * FROM src_wms."+table+" WHERE id=?",id);}
 @Transactional public Map<String,Object> postCount(long id,String key){
  if(key==null||key.isBlank()||key.length()>50)throw state("盘点幂等号不能为空且不得超过50字符");
  Map<String,Object> p=one("SELECT * FROM src_wms.wms_count_plan WHERE id=? FOR UPDATE",id);
  if("COMPLETED".equals(p.get("status"))){
   if(Objects.equals(p.get("idempotency_key"),key))return p;
   throw state("盘点单已过账");
  }
  if(!"REVIEWING".equals(p.get("status")))throw state("只有差异复核中的盘点单可以过账");
  List<Map<String,Object>> lines=db.queryForList("SELECT * FROM src_wms.wms_count_line WHERE count_id=? ORDER BY material_code,location_code,batch_no,id FOR UPDATE",id);
  if(lines.isEmpty())throw state("盘点单没有明细，不能过账");
  Set<List<String>> dimensions=new HashSet<>();
  // 全部校验完成后才写账，不能跳过未复核的明细。
  for(Map<String,Object> l:lines){
   if(!"APPROVED".equals(l.get("review_status")))throw state("所有盘点明细必须完成复核后才能过账");
   if(l.get("actual_qty")==null||d(l,"actual_qty").signum()<0||d(l,"book_qty").signum()<0)throw state("盘点数量必须完整且不能为负数");
   if(d(l,"actual_qty").subtract(d(l,"book_qty")).compareTo(d(l,"difference_qty"))!=0)throw state("盘点差异与实盘数量不一致，请重新复核");
   String material=(String)l.get("material_code"),location=(String)l.get("location_code"),batch=(String)l.get("batch_no");
   if(!dimensions.add(Arrays.asList(material,blank(location,""),blank(batch,""))))throw state("盘点明细存在重复库存维度");
   Map<String,Object> balance=lock(material,(String)p.get("warehouse_code"),location,batch);
   if(d(balance,"on_hand_qty").compareTo(d(l,"book_qty"))!=0)throw state("盘点期间库存已变化，请重新盘点和复核");
  }
  for(Map<String,Object> l:lines){
   BigDecimal diff=d(l,"difference_qty");
   if(diff.signum()!=0){
    InventoryActionCommand c=new InventoryActionCommand(key+":"+l.get("id"),(String)l.get("material_code"),(String)p.get("warehouse_code"),(String)l.get("location_code"),(String)l.get("batch_no"),diff.abs(),"WMS",(String)p.get("count_no"),"盘点差异过账");
    action(diff.signum()>0?"COUNT_GAIN":"COUNT_LOSS",c);
   }
  }
  db.update("UPDATE src_wms.wms_count_plan SET status='COMPLETED',posted_at=NOW(3),idempotency_key=? WHERE id=?",key,id);
  return one("SELECT * FROM src_wms.wms_count_plan WHERE id=?",id);
 }
 private InventoryActionCommand cmd(String key,Map<String,Object>t,String wh,String loc,BigDecimal q){return new InventoryActionCommand(key,String.valueOf(t.get("material_code")),String.valueOf(t.get(wh)),(String)t.get(loc),(String)t.get("batch_no"),q,"WMS",String.valueOf(t.get("transfer_no")),"库存调拨");}
 private Map<String,Object>lock(String m,String w,String l,String b){var x=db.queryForList("SELECT * FROM src_wms.wms_inventory WHERE material_code=? AND warehouse_code=? AND COALESCE(location_code,'')=COALESCE(?,'') AND COALESCE(batch_no,'')=COALESCE(?,'') FOR UPDATE",m,w,l,b);if(x.isEmpty())throw BizException.notFound("库存余额",m+"/"+w);return x.get(0);}private boolean exists(String k){Integer n=db.queryForObject("SELECT COUNT(*) FROM src_wms.wms_inventory_ledger WHERE idempotency_key=?",Integer.class,k);return n!=null&&n>0;}private Map<String,Object>ledger(String k){return one("SELECT * FROM src_wms.wms_inventory_ledger WHERE idempotency_key=?",k);}private Map<String,Object>one(String q,Object...a){var x=db.queryForList(q,a);if(x.isEmpty())throw BizException.notFound("业务记录","未知");return x.get(0);}private BigDecimal d(Map<String,Object>r,String k){Object v=r.get(k);return v==null?BigDecimal.ZERO:v instanceof BigDecimal b?b:new BigDecimal(String.valueOf(v));}private void require(BigDecimal have,BigDecimal need,String msg){if(have.compareTo(need)<0)throw state(msg);}private BizException state(String m){return BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE,m);}private String blank(String v,String d){return v==null||v.isBlank()?d:v;}private void assertType(String t){if(!Set.of("FREEZE","UNFREEZE","QUARANTINE","RELEASE","SCRAP","SUPPLIER_RETURN","ISSUE","RETURN_MATERIAL","RECEIPT","PUTAWAY","COUNT_GAIN","COUNT_LOSS").contains(t))throw state("非法库存动作");}
}
