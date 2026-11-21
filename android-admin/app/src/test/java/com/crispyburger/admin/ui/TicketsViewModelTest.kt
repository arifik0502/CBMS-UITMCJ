package com.crispyburger.admin.ui

import com.crispyburger.admin.data.TicketFilter
import com.crispyburger.admin.data.TicketStatus
import com.crispyburger.admin.data.testTicket
import com.crispyburger.admin.ui.home.ReceiptState
import com.crispyburger.admin.ui.home.TicketsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TicketsViewModelTest {
    private lateinit var api: FakeAdminApi

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        api = FakeAdminApi().apply {
            tickets = listOf(
                testTicket("1", "TK-0001", TicketStatus.OPEN, photo = "a/1.jpg", order = "CB-0001"),
                testTicket("2", "TK-0002", TicketStatus.RESOLVED),
                testTicket("3", "TK-0003", TicketStatus.OPEN)
            )
        }
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = TicketsViewModel(api) { null }

    @Test fun loadsTicketsAndCountsOpenOnes() {
        val vm = vm()
        vm.ensureLoaded()
        val s = vm.state.value
        assertEquals(3, s.tickets.size)
        assertEquals(2, s.openCount)
        assertEquals(listOf("1", "3"), s.visible.map { it.id })
        assertFalse(s.isLoading)
    }

    @Test fun ensureLoadedOnlyLoadsOnce() {
        val vm = vm()
        vm.ensureLoaded()
        vm.ensureLoaded()
        assertEquals(1, api.ticketCalls.count { it == "load" })
    }

    @Test fun filterSwitchesVisibleList() {
        val vm = vm()
        vm.ensureLoaded()
        vm.setFilter(TicketFilter.RESOLVED)
        assertEquals(listOf("2"), vm.state.value.visible.map { it.id })
    }

    @Test fun loadFailureShowsFriendlyError() {
        api.failTickets = RuntimeException("Unable to resolve host")
        val vm = vm()
        vm.ensureLoaded()
        assertTrue(vm.state.value.error!!.startsWith("No connection"))
    }

    @Test fun resolvingSendsResolvedClosesDetailAndReloads() {
        val vm = vm()
        vm.ensureLoaded()
        vm.select("1")
        api.ticketCalls.clear()
        vm.toggleStatus()
        val s = vm.state.value
        assertTrue(api.ticketCalls.contains("status:1:RESOLVED"))
        assertTrue(api.ticketCalls.contains("load"))
        assertNull(s.selectedId)
        assertEquals("TK-0001 marked resolved.", s.info)
        assertFalse(s.isWorking)
    }

    @Test fun reopeningASolvedTicketSendsOpen() {
        val vm = vm()
        vm.ensureLoaded()
        vm.select("2")
        vm.toggleStatus()
        assertTrue(api.ticketCalls.contains("status:2:OPEN"))
    }

    @Test fun failedStatusChangeKeepsDetailOpenWithMessage() {
        val vm = vm()
        vm.ensureLoaded()
        vm.select("1")
        api.ticketCalls.clear()
        api.failAction = RuntimeException("TICKET_NOT_FOUND")
        vm.toggleStatus()
        val s = vm.state.value
        assertEquals("1", s.selectedId)
        assertTrue(s.error!!.contains("could not be found"))
        assertFalse(api.ticketCalls.contains("load"))
    }

    @Test fun photoOnlyLoadsForTicketsWithAttachment() {
        val vm = vm()
        vm.ensureLoaded()
        vm.select("3")                       // no photo
        vm.viewPhoto()
        assertEquals(ReceiptState.None, vm.state.value.photo)
        vm.select("1")                       // has photo; downloader returns null -> Failed(url)
        vm.viewPhoto()
        val photo = vm.state.value.photo
        assertTrue(photo is ReceiptState.Failed)
        assertEquals("https://example.test/a/1.jpg", (photo as ReceiptState.Failed).url)
    }

    @Test fun resetClearsEverything() {
        val vm = vm()
        vm.ensureLoaded()
        vm.select("1")
        vm.reset()
        assertTrue(vm.state.value.tickets.isEmpty())
        assertNull(vm.state.value.selectedId)
    }
}
