/*
 * Copyright (c) 2020 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.core.payment.presentation.viewmodel

import android.content.Context
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import me.proton.core.country.domain.entity.Country
import me.proton.core.country.domain.usecase.GetCountry
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.ApiException
import me.proton.core.network.domain.ApiResult
import me.proton.core.payment.domain.entity.Card
import me.proton.core.payment.domain.entity.Currency
import me.proton.core.payment.domain.entity.Details
import me.proton.core.payment.domain.entity.PaymentMethod
import me.proton.core.payment.domain.entity.PaymentMethodType
import me.proton.core.payment.domain.entity.PaymentType
import me.proton.core.payment.domain.entity.Subscription
import me.proton.core.payment.domain.entity.SubscriptionCycle
import me.proton.core.payment.domain.entity.SubscriptionStatus
import me.proton.core.payment.domain.usecase.GetAvailablePaymentMethods
import me.proton.core.payment.domain.usecase.GetCurrentSubscription
import me.proton.core.payment.domain.usecase.ValidateSubscriptionPlan
import me.proton.core.plan.domain.entity.PLAN_ADDON
import me.proton.core.plan.domain.entity.PLAN_PRODUCT
import me.proton.core.plan.domain.entity.Plan
import me.proton.core.presentation.utils.getUserMessage
import me.proton.core.test.android.ArchTest
import me.proton.core.test.kotlin.CoroutinesTest
import me.proton.core.test.kotlin.assertIs
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaymentOptionsViewModelTest : ArchTest, CoroutinesTest {

    // region mocks
    private val validateSubscription = mockk<ValidateSubscriptionPlan>(relaxed = true)
    private val billingViewModelHelper = mockk<BillingCommonViewModel>(relaxed = true)
    private val getCountryCode = mockk<GetCountry>(relaxed = true)
    private val getAvailablePaymentMethods = mockk<GetAvailablePaymentMethods>(relaxed = true)
    private val getCurrentSubscription = mockk<GetCurrentSubscription>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    // endregion

    // region test data
    private val testCCCountry = "test-country"
    private val testUserId = UserId("test-user-id")
    private val testCurrency = Currency.CHF
    private val testSubscriptionCycle = SubscriptionCycle.YEARLY
    private val testReadOnlyCard = Card.CardReadOnly(
        brand = "visa", last4 = "1234", expirationMonth = "01",
        expirationYear = "2021", name = "Test", country = "Test Country", zip = "123"
    )
    private val testPaymentMethodsList = listOf(
        PaymentMethod(
            "1",
            PaymentMethodType.CARD,
            Details.CardDetails(testReadOnlyCard)
        ),
        PaymentMethod(
            "2",
            PaymentMethodType.PAYPAL,
            Details.PayPalDetails(
                billingAgreementId = "3",
                payer = "test payer"
            )
        )
    )
    private val testSubscribedPlan = Plan(
        "test-subscribed-plan-id", 1, 12, "test-subscribed-plan-name", "test-plan", "EUR",
        2, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, true
    )
    private val testSubscription = Subscription(
        id = "test-subscription-id",
        invoiceId = "test-invoice-id",
        cycle = 12,
        periodStart = 1,
        periodEnd = 2,
        couponCode = null,
        currency = "EUR",
        amount = 5,
        plans = listOf(testSubscribedPlan)
    )

    // endregion
    private lateinit var viewModel: PaymentOptionsViewModel
    private val subscriptionStatus = SubscriptionStatus(
        1, 1, 1, 0, null, 0, Currency.EUR, SubscriptionCycle.MONTHLY, null
    )

    @Before
    fun beforeEveryTest() {
        coEvery { getCountryCode.invoke(any()) } returns Country(testCCCountry, "test-code-1")

        coEvery { validateSubscription.invoke(any(), any(), any(), any(), any()) } returns subscriptionStatus

        every { context.getString(any()) } returns "test-string"
        coEvery { getCurrentSubscription.invoke(testUserId) } returns testSubscription
        viewModel =
            PaymentOptionsViewModel(
                context,
                billingViewModelHelper,
                getAvailablePaymentMethods,
                getCurrentSubscription
            )
    }

    @Test
    fun `available payment methods success handled correctly`() = coroutinesTest {
        // GIVEN
        coEvery { getAvailablePaymentMethods.invoke(testUserId) } returns testPaymentMethodsList
        viewModel.availablePaymentMethodsState.test {
            // WHEN
            viewModel.getAvailablePaymentMethods(testUserId)
            // THEN
            assertIs<PaymentOptionsViewModel.State.Idle>(awaitItem())
            assertIs<PaymentOptionsViewModel.State.Processing>(awaitItem())
            val paymentMethodsStatus = awaitItem()
            assertTrue(paymentMethodsStatus is PaymentOptionsViewModel.State.Success.PaymentMethodsSuccess)
            assertEquals(2, paymentMethodsStatus.availablePaymentMethods.size)
        }
    }

    @Test
    fun `no available payment methods success handled correctly`() = coroutinesTest {
        // GIVEN
        coEvery { getAvailablePaymentMethods.invoke(testUserId) } returns emptyList()
        viewModel.availablePaymentMethodsState.test {
            // WHEN
            viewModel.getAvailablePaymentMethods(testUserId)
            // THEN
            coVerify(exactly = 1) { getCurrentSubscription.invoke(any()) }
            assertIs<PaymentOptionsViewModel.State.Idle>(awaitItem())
            assertIs<PaymentOptionsViewModel.State.Processing>(awaitItem())
            val paymentMethodsStatus = awaitItem()
            assertTrue(paymentMethodsStatus is PaymentOptionsViewModel.State.Success.PaymentMethodsSuccess)
            assertTrue(paymentMethodsStatus.availablePaymentMethods.isEmpty())
        }
    }

    @Test
    fun `available payment methods error handled correctly`() = coroutinesTest {
        // GIVEN
        coEvery { getAvailablePaymentMethods.invoke(testUserId) } throws ApiException(
            ApiResult.Error.Http(
                httpCode = 123,
                "http error",
                ApiResult.Error.ProtonData(
                    code = 1234,
                    error = "proton error"
                )
            )
        )
        viewModel.availablePaymentMethodsState.test {
            // WHEN
            viewModel.getAvailablePaymentMethods(testUserId)
            // THEN
            assertIs<PaymentOptionsViewModel.State.Idle>(awaitItem())
            assertIs<PaymentOptionsViewModel.State.Processing>(awaitItem())
            val paymentMethodsStatus = awaitItem()
            assertTrue(paymentMethodsStatus is PaymentOptionsViewModel.State.Error.General)
            assertEquals("proton error", paymentMethodsStatus.error.getUserMessage(mockk()))
        }
    }

    @Test
    fun `on subscribe checks existing plans`() = coroutinesTest {
        // GIVEN
        val testPlanId = "test-plan-id"
        val testPlanServices = 1
        val testPlanType = PLAN_PRODUCT
        val paymentType = PaymentType.PaymentMethod("test-payment-method-id")
        coEvery { getAvailablePaymentMethods.invoke(testUserId) } returns testPaymentMethodsList
        val viewModelSpy = spyk(viewModel, recordPrivateCalls = true)
        // WHEN
        viewModelSpy.getAvailablePaymentMethods(testUserId)
        viewModelSpy.subscribe(
            testUserId,
            testPlanId,
            testPlanServices,
            testPlanType,
            null,
            testCurrency,
            testSubscriptionCycle,
            paymentType
        )
        // THEN
        verify(exactly = 1) {
            billingViewModelHelper.subscribe(
                testUserId,
                listOf(testPlanId),
                any(),
                testCurrency,
                testSubscriptionCycle,
                paymentType
            )
        }
    }

    @Test
    fun `on subscribe checks existing plans but different products`() = coroutinesTest {
        // GIVEN
        val testPlanId = "test-plan-id"
        val testPlanServices = 1
        val testPlanType = PLAN_PRODUCT
        val paymentType = PaymentType.PaymentMethod("test-payment-method-id")
        coEvery { getCurrentSubscription.invoke(testUserId) } returns testSubscription.copy(
            plans = listOf(testSubscribedPlan.copy(services = 4))
        )
        coEvery { getAvailablePaymentMethods.invoke(testUserId) } returns testPaymentMethodsList
        val viewModelSpy = spyk(viewModel, recordPrivateCalls = true)
        // WHEN
        viewModelSpy.getAvailablePaymentMethods(testUserId)
        viewModelSpy.subscribe(
            testUserId,
            testPlanId,
            testPlanServices,
            testPlanType,
            null,
            testCurrency,
            testSubscriptionCycle,
            paymentType
        )
        // THEN
        verify(exactly = 1) {
            billingViewModelHelper.subscribe(
                testUserId,
                listOf("test-subscribed-plan-name", testPlanId),
                any(),
                testCurrency,
                testSubscriptionCycle,
                paymentType
            )
        }
    }

    @Test
    fun `on subscribe checks existing plans add on`() = coroutinesTest {
        // GIVEN
        val testPlanId = "test-plan-id"
        val testPlanServices = 1
        val testPlanType = PLAN_ADDON
        val paymentType = PaymentType.PaymentMethod("test-payment-method-id")
        coEvery { getAvailablePaymentMethods.invoke(testUserId) } returns testPaymentMethodsList
        val viewModelSpy = spyk(viewModel, recordPrivateCalls = true)
        // WHEN
        viewModelSpy.getAvailablePaymentMethods(testUserId)
        viewModelSpy.subscribe(
            testUserId,
            testPlanId,
            testPlanServices,
            testPlanType,
            null,
            testCurrency,
            testSubscriptionCycle,
            paymentType
        )
        // THEN
        verify(exactly = 1) {
            billingViewModelHelper.subscribe(
                testUserId,
                listOf("test-subscribed-plan-name", testPlanId),
                any(),
                testCurrency,
                testSubscriptionCycle,
                paymentType
            )
        }
    }

    @Test
    fun `subscribe pass the call to billing subscribe`() = coroutinesTest {
        // GIVEN
        val testPlanId = "test-plan-id"
        val testPlanServices = 1
        val testPlanType = PLAN_PRODUCT
        val paymentType = PaymentType.PaymentMethod("test-payment-method-id")
        // WHEN
        viewModel.subscribe(
            testUserId,
            testPlanId,
            testPlanServices,
            testPlanType,
            null,
            testCurrency,
            testSubscriptionCycle,
            paymentType
        )
        // THEN
        verify(exactly = 1) {
            billingViewModelHelper.subscribe(
                testUserId,
                listOf("test-plan-id"),
                null,
                testCurrency,
                testSubscriptionCycle,
                paymentType
            )
        }
    }

    @Test
    fun `validate plan pass the call to billing validate plan`() = coroutinesTest {
        // GIVEN
        val testPlanId = "test-plan-id"
        val testPlanServices = 1
        val testPlanType = PLAN_PRODUCT
        // WHEN
        viewModel.validatePlan(
            testUserId,
            testPlanId,
            testPlanServices,
            testPlanType,
            null,
            testCurrency,
            testSubscriptionCycle
        )
        // THEN
        verify(exactly = 1) {
            billingViewModelHelper.validatePlan(
                testUserId,
                listOf("test-plan-id"),
                null,
                testCurrency,
                testSubscriptionCycle
            )
        }
    }

    @Test
    fun `on3dsapproved pass the call to billing on3dsapproved`() = coroutinesTest {
        // GIVEN
        val testPlanId = "test-plan-id"
        val testPlanServices = 1
        val testPlanType = PLAN_PRODUCT
        val testAmount = 5L
        val testToken = "test-token"
        // WHEN
        viewModel.onThreeDSTokenApproved(
            testUserId,
            testPlanId,
            testPlanServices,
            testPlanType,
            null,
            testAmount,
            testCurrency,
            testSubscriptionCycle,
            testToken
        )
        // THEN
        verify(exactly = 1) {
            billingViewModelHelper.onThreeDSTokenApproved(
                testUserId,
                listOf("test-plan-id"),
                null,
                testAmount,
                testCurrency,
                testSubscriptionCycle,
                testToken
            )
        }
    }
}
