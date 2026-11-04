package com.romuel.pianosolfege.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class StaffPitchMapperTest {
    @Test fun frereJacquesTrebleMelodyIsDoReMiDo() {
        val notes = listOf(-2, -1, 0, -2).map(StaffPitchMapper::trebleName)
        assertEquals(listOf("Do4", "Ré4", "Mi4", "Do4"), notes)
    }

    @Test fun frereJacquesBassPartIsDo3ThenSol3() {
        val notes = listOf(3, 7).map(StaffPitchMapper::bassName)
        assertEquals(listOf("Do3", "Sol3"), notes)
    }

    @Test fun ledgerAndSpaceNotesWorkInBothClefs() {
        assertEquals("Sol3", StaffPitchMapper.trebleName(-5))
        assertEquals("Mi4", StaffPitchMapper.trebleName(0))
        assertEquals("Fa4", StaffPitchMapper.trebleName(1))
        assertEquals("Sol2", StaffPitchMapper.bassName(0))
        assertEquals("Do3", StaffPitchMapper.bassName(3))
    }
}
