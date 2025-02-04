/*
 * Copyright (c) 2022 Proton Technologies AG
 * This file is part of Proton AG and ProtonCore.
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

package me.proton.core.featureflag.data.testdata

import me.proton.core.featureflag.data.api.response.FeatureApiResponse
import me.proton.core.featureflag.data.entity.FeatureFlagEntity
import me.proton.core.featureflag.domain.entity.FeatureId

internal object FeatureFlagTestData {
    private const val RAW_FEATURE_ID = "featureId"
    private const val RAW_FEATURE_ID_1 = "featureId1"

    val featureId = FeatureId(RAW_FEATURE_ID)
    val featureId1 = FeatureId(RAW_FEATURE_ID_1)

    val enabledFeatureApiResponse = FeatureApiResponse(
        featureId.id,
        isGlobal = false,
        defaultValue = true,
        value = true
    )

    val disabledFeatureApiResponse = FeatureApiResponse(
        featureId1.id,
        isGlobal = false,
        defaultValue = false,
        value = false
    )

    val enabledFeatureEntity = FeatureFlagEntity(
        UserIdTestData.userId,
        featureId.id,
        isGlobal = false,
        defaultValue = true,
        value = true
    )

    val disabledFeatureEntity = FeatureFlagEntity(
        UserIdTestData.userId,
        featureId1.id,
        isGlobal = false,
        defaultValue = false,
        value = false
    )
}
