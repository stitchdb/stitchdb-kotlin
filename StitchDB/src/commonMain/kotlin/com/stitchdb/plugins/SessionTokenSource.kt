package com.stitchdb.plugins

/**
 * Implemented by a plugin that holds the signed-in user's session (Auth), so core features like offline sync can send
 * the session token without depending on the Auth module.
 */
interface SessionTokenSource {

    /**
     * Returns the current access token, or null if no session is available
     */
    fun currentAccessTokenOrNull(): String?

}
