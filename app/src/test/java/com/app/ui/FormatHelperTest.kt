package com.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FormatHelperTest {

    @Test
    fun evaluateExpression_basic() {
        assertEquals(500000.0, FormatHelper.evaluateExpression("500000"), 0.0)
        assertEquals(500000.0, FormatHelper.evaluateExpression(" 500000 "), 0.0)
    }

    @Test
    fun evaluateExpression_operators() {
        assertEquals(150000.0, FormatHelper.evaluateExpression("100000+50000"), 0.0)
        assertEquals(50000.0, FormatHelper.evaluateExpression("100000-50000"), 0.0)
        assertEquals(200000.0, FormatHelper.evaluateExpression("100000*2"), 0.0)
        assertEquals(50000.0, FormatHelper.evaluateExpression("100000/2"), 0.0)
    }

    @Test
    fun evaluateExpression_complex() {
        assertEquals(250000.0, FormatHelper.evaluateExpression("100000+50000*3"), 0.0)
        assertEquals(0.0, FormatHelper.evaluateExpression("invalid"), 0.0)
        assertEquals(40000.0, FormatHelper.evaluateExpression(" 100000 - 20000 * 3 "), 0.0)
    }

    @Test
    fun eventDateCalculations_startAndEndOfDay() {
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.AUGUST, 17, 14, 30, 45)
            set(java.util.Calendar.MILLISECOND, 500)
        }
        val timestamp = cal.timeInMillis

        val startOfDay = FormatHelper.getStartOfDay(timestamp)
        val endOfDay = FormatHelper.getEndOfDay(timestamp)

        val startCal = java.util.Calendar.getInstance().apply { timeInMillis = startOfDay }
        assertEquals(2026, startCal.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.AUGUST, startCal.get(java.util.Calendar.MONTH))
        assertEquals(17, startCal.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(0, startCal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(0, startCal.get(java.util.Calendar.MINUTE))
        assertEquals(0, startCal.get(java.util.Calendar.SECOND))
        assertEquals(0, startCal.get(java.util.Calendar.MILLISECOND))

        val endCal = java.util.Calendar.getInstance().apply { timeInMillis = endOfDay }
        assertEquals(2026, endCal.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.AUGUST, endCal.get(java.util.Calendar.MONTH))
        assertEquals(17, endCal.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(23, endCal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(59, endCal.get(java.util.Calendar.MINUTE))
        assertEquals(59, endCal.get(java.util.Calendar.SECOND))
        assertEquals(999, endCal.get(java.util.Calendar.MILLISECOND))
    }

    @Test
    fun eventDaysDifference() {
        val day1 = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.AUGUST, 17, 10, 0, 0) }.timeInMillis
        val day1Evening = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.AUGUST, 17, 22, 0, 0) }.timeInMillis
        val day2 = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.AUGUST, 18, 8, 0, 0) }.timeInMillis
        val day4 = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.AUGUST, 20, 15, 0, 0) }.timeInMillis

        // Same day
        assertEquals(0, FormatHelper.getDaysDifference(day1, day1Evening))
        // 1 day apart
        assertEquals(1, FormatHelper.getDaysDifference(day1, day2))
        // 3 days apart
        assertEquals(3, FormatHelper.getDaysDifference(day1, day4))
        // Past (negative)
        assertEquals(-1, FormatHelper.getDaysDifference(day2, day1))
    }

    @Test
    fun eventStatus_sameDayEvent() {
        val aug17 = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.AUGUST, 17, 12, 0, 0) }.timeInMillis
        val startDate = aug17
        val endDate = aug17

        val morningAug17 = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.AUGUST, 17, 6, 0, 0) }.timeInMillis
        val nightAug17 = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.AUGUST, 17, 23, 50, 0) }.timeInMillis
        val morningAug18 = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.AUGUST, 18, 0, 1, 0) }.timeInMillis

        // On Aug 17: Ongoing throughout the whole day
        org.junit.Assert.assertTrue(FormatHelper.isEventOngoing(startDate, endDate, morningAug17))
        org.junit.Assert.assertTrue(FormatHelper.isEventOngoing(startDate, endDate, nightAug17))
        org.junit.Assert.assertFalse(FormatHelper.isEventEnded(endDate, nightAug17))

        // On Aug 18: Ended
        org.junit.Assert.assertFalse(FormatHelper.isEventOngoing(startDate, endDate, morningAug18))
        org.junit.Assert.assertTrue(FormatHelper.isEventEnded(endDate, morningAug18))
    }

    @Test
    fun eventUtcLocalConversions() {
        val localCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.AUGUST, 17, 16, 45, 0)
        }
        val localMillis = localCal.timeInMillis

        val utcMillis = FormatHelper.localDateToUtcMillis(localMillis)
        val convertedLocalStart = FormatHelper.utcMillisToLocalStartOfDay(utcMillis)

        val resultCal = java.util.Calendar.getInstance().apply { timeInMillis = convertedLocalStart }
        assertEquals(2026, resultCal.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.AUGUST, resultCal.get(java.util.Calendar.MONTH))
        assertEquals(17, resultCal.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(0, resultCal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCal.get(java.util.Calendar.MINUTE))
        assertEquals(0, resultCal.get(java.util.Calendar.SECOND))
    }

    @Test
    fun eventStatusStyle_prioritizesEndedOverInactive() {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.AUGUST, 1, 0, 0, 0)
        val startDate = cal.timeInMillis
        cal.set(2026, java.util.Calendar.AUGUST, 10, 23, 59, 59)
        val endDate = cal.timeInMillis

        cal.set(2026, java.util.Calendar.AUGUST, 17, 12, 0, 0)
        val now = cal.timeInMillis

        val inactiveEndedEvent = com.app.data.Event(
            id = 1,
            name = "Thất nghiệp",
            description = "Nghỉ việc",
            startDate = startDate,
            endDate = endDate,
            isActive = false
        )

        val statusStyle = com.app.ui.components.getEventStatusStyle(inactiveEndedEvent, 0.0, now)
        assertEquals("Đã kết thúc", statusStyle.text)

        val priority = com.app.ui.components.getEventPriority(inactiveEndedEvent, 0.0, now)
        assertEquals(5, priority)

        val activeEndedEvent = inactiveEndedEvent.copy(isActive = true)
        val activeStatusStyle = com.app.ui.components.getEventStatusStyle(activeEndedEvent, 0.0, now)
        assertEquals("Đã kết thúc", activeStatusStyle.text)

        // Ongoing event that is inactive should be "Dừng"
        val ongoingInactiveEvent = com.app.data.Event(
            id = 2,
            name = "Hà Giang",
            description = "Đi phượt",
            startDate = startDate,
            endDate = null, // Ongoing
            isActive = false
        )
        val pausedStatusStyle = com.app.ui.components.getEventStatusStyle(ongoingInactiveEvent, 0.0, now)
        assertEquals("Dừng", pausedStatusStyle.text)
    }
}
