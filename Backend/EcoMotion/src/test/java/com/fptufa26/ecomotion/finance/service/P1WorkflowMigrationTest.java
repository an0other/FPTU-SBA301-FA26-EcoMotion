package com.fptufa26.ecomotion.finance.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class P1WorkflowMigrationTest {
    @Test void migrationCanRepeatAndRetainsExistingRefunds() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:migration-" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "")) {
            var statement = connection.createStatement();
            statement.execute("create table refund_request(id bigint primary key, version bigint)");
            statement.execute("insert into refund_request values(1,null)");
            statement.execute("create table dispute(id bigint primary key,category varchar(30),constraint ck_dispute_category check(category in ('CHARGE','SERVICE_QUALITY','DEPOSIT_REFUND','TRIP_NOT_CONFIRMED')))");
            var resource = new ClassPathResource("db/migrations/20261108_p1_workflow_closure.sql");
            ScriptUtils.executeSqlScript(connection, resource);
            ScriptUtils.executeSqlScript(connection, resource);
            statement.execute("insert into dispute values(1,'YEARLY_SETTLEMENT'),(2,'YEARLY_CANCELLATION')");
            try (var rows = statement.executeQuery("select version,gateway_submitted_at from refund_request where id=1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isZero();
                assertThat(rows.getObject(2)).isNull();
            }
            assertThatThrownBy(() -> statement.execute("insert into dispute values(3,'INVALID')")).isInstanceOf(java.sql.SQLException.class);
            statement.execute("insert into financial_review_record(target_type,target_id,request_id,actor_id,outcome,payload,previous_state,created_at) values('REFUND',1,'same',1,'HOLD','evidence','PROCESSING',current_timestamp)");
            assertThatThrownBy(() -> statement.execute("insert into financial_review_record(target_type,target_id,request_id,actor_id,outcome,payload,previous_state,created_at) values('REFUND',1,'same',1,'HOLD','evidence','PROCESSING',current_timestamp)"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
