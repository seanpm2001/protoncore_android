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

package me.proton.core.test.android.api

import me.proton.core.network.data.protonApi.BaseRetrofitApi
import me.proton.core.network.domain.ApiManager
import me.proton.core.network.domain.ApiResult
import java.net.SocketTimeoutException

class TestApiManager<Api : BaseRetrofitApi>(private val api: Api) : ApiManager<Api> {
    override suspend fun <T> invoke(
        forceNoRetryOnConnectionErrors: Boolean,
        block: suspend Api.() -> T
    ): ApiResult<T> = try {
        val result = block.invoke(api)
        ApiResult.Success(result)
    } catch (throwable: Throwable) {
        when (throwable) {
            is SocketTimeoutException -> ApiResult.Error.Timeout(false, throwable)
            else -> ApiResult.Error.Parse(throwable)
        }
    }
}
