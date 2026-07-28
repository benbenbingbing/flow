package com.workflow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class HealthControllerTest {

    @Test
    void livenessDoesNotDependOnDatabase() {
        HealthController controller =
                new HealthController(mock(DataSource.class));

        assertEquals(Map.of("status", "UP"), controller.live());
    }

    @Test
    void readinessWaitsForAllBootstrapJobs()
            throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement =
                mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        when(connection.prepareStatement(
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(2);
        HealthController controller =
                new HealthController(dataSource);
        ResponseEntity<Map<String, String>> response =
                controller.health();

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                response.getStatusCode());
        assertEquals(
                "BOOTSTRAPPING",
                response.getBody().get("status"));
    }
}
