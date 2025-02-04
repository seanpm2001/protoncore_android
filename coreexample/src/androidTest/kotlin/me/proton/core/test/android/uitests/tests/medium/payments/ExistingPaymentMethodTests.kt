/*
 * Copyright (c) 2021 Proton Technologies AG
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

package me.proton.core.test.android.uitests.tests.medium.payments

import me.proton.core.test.android.plugins.data.Card
import me.proton.core.test.android.plugins.data.Plan
import me.proton.core.test.android.plugins.data.User
import me.proton.core.test.android.robots.payments.ExistingPaymentMethodsRobot
import me.proton.core.test.android.robots.payments.ExistingPaymentMethodsRobot.PaymentMethodElement.paymentMethod
import me.proton.core.test.android.uitests.CoreexampleRobot
import me.proton.core.test.android.uitests.tests.BaseTest
import me.proton.core.test.android.uitests.tests.SmokeTest
import org.junit.Ignore
import org.junit.Test

class ExistingPaymentMethodTests : BaseTest() {

    companion object {
        val userWithCard: User = quark.seedUserWithCreditCard()
    }

    @Test
    @Ignore("Requires user with paypal account linked")
    fun existingPaypalMethodDisplayed() {
        val userWithPaypal = users.getUser { it.paypal.isNotEmpty() }

        login(userWithPaypal)

        CoreexampleRobot()
            .plansUpgrade()
            .upgradeToPlan<ExistingPaymentMethodsRobot>(Plan.Dev)
            .verify { paymentMethodDisplayed("PayPal", userWithPaypal.paypal) }
    }

    @Test
    @SmokeTest
    fun existingCreditCardMethodDisplayed() {

        login(userWithCard)

        CoreexampleRobot()
            .plansUpgrade()
            .upgradeToPlan<ExistingPaymentMethodsRobot>(Plan.Dev)
            .verify { paymentMethodDisplayed(Card.default.details, Card.default.name) }
    }

    @Test
    @Ignore("Requires user with paypal account linked")
    fun existingCreditCardAndPayPalDisplayed() {
        val user = users.getUser { it.paypal.isNotEmpty() && it.cards.isNotEmpty() && !it.isPaid }
        val card = user.cards[0]

        login(user)

        CoreexampleRobot()
            .plansUpgrade()
            .upgradeToPlan<ExistingPaymentMethodsRobot>(Plan.Dev)
            .verify {
                paymentMethodDisplayed(card.details, card.name)
                paymentMethodDisplayed("PayPal", user.paypal)
            }
    }

    @Test
    @Ignore("Requires user with paypal account linked")
    fun switchPaymentMethod() {
        val user = users.getUser { it.paypal.isNotEmpty() && it.cards.isNotEmpty() && !it.isPaid }

        CoreexampleRobot()
            .plansUpgrade()
            .upgradeToPlan<ExistingPaymentMethodsRobot>(Plan.Dev)
            .verify {
                paymentMethod(user.paypal).checkIsNotChecked()
                paymentMethod(user.cards[0].details).checkIsChecked()
            }

        ExistingPaymentMethodsRobot()
            .selectPaymentMethod(user.paypal)
            .verify {
                paymentMethod(user.paypal).checkIsChecked()
                paymentMethod(user.cards[0].details).checkIsNotChecked()
            }
    }
}
