package com.stitchdb.plugins

import kotlinx.coroutines.flow.Flow

/**
 * Implemented by a plugin that holds the signed-in user's session (Auth), so core features like offline sync can send
 * the session token without depending on the Auth module.
 */
interface SessionTokenSource {

    /**
     * Returns the current access token, or null if no session is available
     */
    fun currentAccessTokenOrNull(): String?

    /**
     * The signed-in user's id (null while nobody is signed in): the current one first, then on every sign-in and
     * sign-out. Analytics sends the events waiting when it changes, so a batch belongs to one user. Null when the plugin
     * does not tell.
     */
    val signedInUserIds: Flow<String?>?
        get() = null

}
