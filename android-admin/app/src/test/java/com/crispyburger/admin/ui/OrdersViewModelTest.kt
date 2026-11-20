package com.crispyburger.admin.ui

import com.crispyburger.admin.data.OrderFilter
import com.crispyburger.admin.data.OrderStatus
import com.crispyburger.admin.data.PaymentStatus
import com.crispyburger.admin.data.testOrder
import com.crispyburger.admin.ui.home.ItemsState
import com.crispyburger.admin.ui.home.OrderAction
import com.crispyburger.admin.ui.home.OrdersViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModelTest {
    private lateinit var api: FakeAdminApi

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        api = FakeAdminApi().apply {
            orders = listOf(
                testOrder(id = "1", orderStatus = OrderStatus.PREPARING),
                testOrder(id = "2", orderStatus = OrderStatus.DELIVERED)
            )
        }
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = OrdersViewModel(api) { null }

    @Test fun refreshLoadsOrdersAndServerCounts() {
        val vm = vm()
        vm.refresh()
        val s = vm.state.value
        assertEquals(2, s.orders.size)
        assertEquals(2, s.counts?.paymentReview)
        assertFalse(s.isLoading)
        assertNull(s.error)
        assertEquals(OrdersViewModel.PAGE_SIZE, api.lastLimit)
    }

    @Test fun loadFailureShowsFriendlyError() {
        api.failOrders = RuntimeException("FORBIDDEN")
        val vm = vm()
        vm.refresh()
        assertTrue(vm.state.value.error!!.contains("not authorized"))
        assertFalse(vm.state.value.isLoading)
    }

    @Test fun loadMoreIncreasesFetchLimit() {
        val vm = vm()
        vm.refresh()
        vm.loadMore()
        assertEquals(OrdersViewModel.PAGE_SIZE * 2, api.lastLimit)
    }

    @Test fun filterAndSearchStateUpdates() {
        val vm = vm()
        vm.setFilter(OrderFilter.ARCHIVE)
        vm.setSearch("abc")
        assertEquals(OrderFilter.ARCHIVE, vm.state.value.filter)
        assertEquals("abc", vm.state.value.search)
    }

    @Test fun selectLoadsItemsAndPayment() {
        api.items = emptyList()
        val vm = vm()
        vm.refresh()
        vm.select("1")
        val s = vm.state.value
        assertEquals("1", s.selectedOrder?.id)
        assertTrue(s.items is ItemsState.Loaded)
        assertFalse(s.paymentLoading)
    }

    @Test fun itemsUnavailableWhenSchemaDiffers() {
        api.items = null
        val vm = vm()
        vm.refresh()
        vm.select("1")
        assertEquals(ItemsState.Unavailable, vm.state.value.items)
    }

    @Test fun successfulActionCallsApiClosesDetailAndReloads() {
        val vm = vm()
        vm.refresh()
        vm.select("1")
        api.calls.clear()
        vm.perform(OrderAction.Advance(OrderStatus.READY))
        val s = vm.state.value
        assertTrue(api.calls.contains("status:1:READY"))
        assertTrue(api.calls.contains("loadRecentOrders"))
        assertNull(s.selectedOrderId)
        assertEquals("Order updated successfully.", s.info)
        assertFalse(s.isWorking)
    }

    @Test fun failedActionKeepsDetailOpenAndDoesNotReload() {
        val vm = vm()
        vm.refresh()
        vm.select("1")
        api.calls.clear()
        api.failAction = RuntimeException("payment_not_verified")
        vm.perform(OrderAction.Advance(OrderStatus.DELIVERED))
        val s = vm.state.value
        assertEquals("1", s.selectedOrderId)
        assertTrue(s.error!!.contains("Verify the DuitNow"))
        assertFalse(api.calls.contains("loadRecentOrders"))
        assertNull(s.info)
    }

    @Test fun reloadFailureAfterSuccessfulActionIsNotReportedAsActionFailure() {
        val vm = vm()
        vm.refresh()
        vm.select("1")
        api.failOrders = RuntimeException("Unable to resolve host")
        vm.perform(OrderAction.Verify)
        val s = vm.state.value
        assertEquals("Order updated successfully.", s.info.takeIf { api.calls.contains("verify:1") })
        assertTrue(s.error!!.startsWith("No connection"))
    }

    @Test fun pendingNotificationOrderOpensAfterLoad() {
        val vm = vm()
        vm.requestOpen("2")
        assertEquals("2", vm.state.value.selectedOrderId)
    }

    @Test fun resetClearsState() {
        val vm = vm()
        vm.refresh()
        vm.select("1")
        vm.reset()
        assertTrue(vm.state.value.orders.isEmpty())
        assertNull(vm.state.value.selectedOrderId)
        assertNotNull(vm.state)
    }

    @Test fun cashActionPassesOrderId() {
        val vm = vm()
        vm.refresh()
        vm.select("1")
        vm.perform(OrderAction.CollectCash)
        assertTrue(api.calls.contains("cash:1"))
    }

    @Test fun paymentStatusConstantsAreUsedForVerification() {
        assertEquals("PENDING_VERIFICATION", PaymentStatus.PENDING_VERIFICATION)
    }
}
