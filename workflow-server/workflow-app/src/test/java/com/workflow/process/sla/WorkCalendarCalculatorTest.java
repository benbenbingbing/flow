package com.workflow.process.sla;

import com.workflow.process.sla.calendar.application.WorkCalendarCalculator;
import com.workflow.process.sla.calendar.application.WorkCalendarAdapter;
import com.workflow.process.sla.calendar.application.WorkCalendarService;
import com.workflow.process.sla.calendar.application.model.WorkCalendarSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkCalendarCalculatorTest {

    private final WorkCalendarCalculator calculator =
            new WorkCalendarCalculator();

    @Test
    void calendarPortUsesPublishedVersionWithoutWebIdentity() {
        var service = mock(WorkCalendarService.class);
        var port = new WorkCalendarAdapter(service, calculator);
        var calendar = standardCalendar(Map.of());
        var start = Instant.parse("2026-07-27T03:00:00Z");
        when(service.findPublishedSnapshot("CN_STANDARD")).thenReturn(Optional.of(calendar));
        when(service.findPublishedSnapshotByCode("CN_STANDARD")).thenReturn(calendar);
        assertTrue(port.findPublishedByCode("draft").isEmpty());
        var published = port.findPublishedByCode(" CN_STANDARD ").orElseThrow();
        assertEquals("Asia/Shanghai", published.timezoneId());
        assertEquals(2, published.weeklyPeriods().get(1).size());
        assertEquals(Instant.parse("2026-07-27T07:00:00Z"), port.addWorkingMinutes("CN_STANDARD", start, 180));
    }

    @Test
    void addsWorkingMinutesAcrossLunchBreak() {
        Instant due = calculator.addMinutes(
                Instant.parse("2026-07-27T03:00:00Z"),
                180,
                "WORKING_TIME",
                standardCalendar(Map.of()));

        assertEquals(
                Instant.parse("2026-07-27T07:00:00Z"),
                due);
    }

    @Test
    void skipsWeekendAndHolidayException() {
        WorkCalendarSnapshot calendar = standardCalendar(Map.of(
                LocalDate.of(2026, 8, 3),
                new WorkCalendarSnapshot.ExceptionDay(
                        "NON_WORKING",
                        "测试假日",
                        List.of())));

        Instant due = calculator.addMinutes(
                Instant.parse("2026-07-31T09:00:00Z"),
                120,
                "WORKING_TIME",
                calendar);

        assertEquals(
                Instant.parse("2026-08-04T02:00:00Z"),
                due);
    }

    @Test
    void usesActualElapsedTimeAcrossDaylightSavingGap() {
        WorkCalendarSnapshot calendar = new WorkCalendarSnapshot(
                "US_SUNDAY",
                "美国周日测试",
                1,
                "America/New_York",
                Map.of(
                        7,
                        List.of(new WorkCalendarSnapshot.Period(
                                60,
                                240))),
                Map.of());

        Instant due = calculator.addMinutes(
                Instant.parse("2026-03-08T06:00:00Z"),
                120,
                "WORKING_TIME",
                calendar);

        assertEquals(
                Instant.parse("2026-03-08T08:00:00Z"),
                due);
    }

    @Test
    void rejectsOverlappingPeriods() {
        WorkCalendarSnapshot calendar = new WorkCalendarSnapshot(
                "INVALID",
                "无效日历",
                1,
                "Asia/Shanghai",
                Map.of(
                        1,
                        List.of(
                                new WorkCalendarSnapshot.Period(540, 720),
                                new WorkCalendarSnapshot.Period(660, 780))),
                Map.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.validate(calendar));
    }

    private WorkCalendarSnapshot standardCalendar(
            Map<LocalDate, WorkCalendarSnapshot.ExceptionDay> exceptions) {
        List<WorkCalendarSnapshot.Period> periods = List.of(
                new WorkCalendarSnapshot.Period(540, 720),
                new WorkCalendarSnapshot.Period(780, 1080));
        return new WorkCalendarSnapshot(
                "CN_STANDARD",
                "中国标准工作日历",
                1,
                "Asia/Shanghai",
                Map.of(
                        1, periods,
                        2, periods,
                        3, periods,
                        4, periods,
                        5, periods),
                exceptions);
    }
}
