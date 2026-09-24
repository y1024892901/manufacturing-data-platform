import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfg.common.integration.*;
import com.mfg.qms.integration.ReceiptInspectionConsumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** 用真实表验证消费与幂等；全部业务写入在外层事务结束时回滚。 */
public class BusinessEventDatabaseSmoke {
    public static void main(String[] args) {
        var env=System.getenv();
        String url="jdbc:mysql://"+env.getOrDefault("MYSQL_HOST","127.0.0.1")+":"+env.getOrDefault("MYSQL_PORT","3306")+"/mfg_auth?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        var ds=new DriverManagerDataSource(url,env.getOrDefault("MYSQL_USER","root"),env.get("MYSQL_PASSWORD"));
        var db=new JdbcTemplate(ds);
        var manager=new DataSourceTransactionManager(ds);
        var json=new ObjectMapper();
        var bus=new BusinessEventService(db,json);
        var consumer=new ReceiptInspectionConsumer(db);
        var scheduler=new BusinessEventConsumerScheduler(db,json,List.of(consumer),manager);
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,16);
        String receipt="TEST-R-"+suffix, inspection="TEST-I-"+suffix;
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            try {
                db.update("INSERT INTO src_wms.wms_receipt(receipt_no,material_code,supplier_code,received_qty,warehouse_code,inspection_no) VALUES(?,'TEST-M','TEST-S',10,'TEST-WH',?)",receipt,inspection);
                var payload=Map.of("receiptNo",receipt,"inspectionNo",inspection);
                String first=bus.publish("WMS.RECEIPT.PENDING_INSPECTION","WMS","QMS","RECEIPT",receipt,payload);
                bus.dispatch(); scheduler.drain();
                require("CONSUMED".equals(db.queryForObject("SELECT consume_status FROM mfg_ops.biz_inbox WHERE event_id=?",String.class,first)),"first event consumed");
                require("qms".equals(db.queryForObject("SELECT target_system FROM mfg_ops.biz_outbox WHERE event_id=?",String.class,first)),"system code normalized");
                String second=bus.publish("WMS.RECEIPT.PENDING_INSPECTION","wms","qms","RECEIPT",receipt,payload);
                bus.dispatch(); scheduler.drain();
                require("CONSUMED".equals(db.queryForObject("SELECT consume_status FROM mfg_ops.biz_inbox WHERE event_id=?",String.class,second)),"duplicate business event consumed");
                require(db.queryForObject("SELECT COUNT(*) FROM src_qms.qms_inspection WHERE inspection_no=?",Integer.class,inspection)==1,"one inspection for two events");
                scheduler.drain();
                require(db.queryForObject("SELECT COUNT(*) FROM src_qms.qms_inspection WHERE inspection_no=?",Integer.class,inspection)==1,"drain again remains idempotent");
                System.out.println("PASS: real inbox CONSUMED, target normalization, duplicate business event, repeated drain");
            } finally { status.setRollbackOnly(); }
        });
        require(db.queryForObject("SELECT COUNT(*) FROM src_wms.wms_receipt WHERE receipt_no=?",Integer.class,receipt)==0,"fixture rollback");
        require(db.queryForObject("SELECT COUNT(*) FROM src_qms.qms_inspection WHERE inspection_no=?",Integer.class,inspection)==0,"inspection rollback");
        System.out.println("PASS: smoke fixtures rolled back");
    }
    static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
