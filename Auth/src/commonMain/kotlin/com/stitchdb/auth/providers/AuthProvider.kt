package com.stitchdb.auth.providers

import com.stitchdb.StitchClient
import com.stitchdb.auth.user.UserSession

/**
 * An authentication provider
 */
interface AuthProvider<C, R> {

    /**
     * Used to login a user
     * @param stitchdbClient The StitchClient to import the session into
     * @param onSuccess The callback to call when the login was successful
     * @param config The configuration for the login
     */
    suspend fun login(
        stitchdbClient: StitchClient,
        onSuccess: suspend (UserSession) -> Unit,
        redirectUrl: String? = null,
        config: (C.() -> Unit)? = null
    )

    /**
     * Used to sign up a user.
     * @param stitchdbClient The StitchClient to import the session into
     * @param onSuccess The callback to call when the login was successful
     * @param config The configuration for the login
     * @return The result of the sign up or null, if autoconfirm is enabled
     */
    suspend fun signUp(
        stitchdbClient: StitchClient,
        onSuccess: suspend (UserSession) -> Unit,
        redirectUrl: String? = null,
        config: (C.() -> Unit)? = null
    ): R?

}