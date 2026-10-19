package com.stitchdb.auth.oauth

import com.stitchdb.auth.api.AuthenticatedStitchApi
import com.stitchdb.safeBody
import io.ktor.client.request.parameter
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Interface for the OAuth 2.1 authorization server methods.
 * Only relevant when the OAuth 2.1 server is enabled in StitchDB Auth.
 * Used to implement the authorization code flow on the consent page.
 */
interface OAuthApi {

    /**
     * Retrieves details about an OAuth authorization request.
     * Only relevant when the OAuth 2.1 server is enabled in StitchDB Auth.
     *
     * Returns [OAuthAuthorizationDetails] including client info, scopes, and user information.
     * If the response includes only a redirect_url field, it means consent was already given - the caller
     * should handle the redirect manually if needed, so a [OAuthRedirect] will be returned.
     * @param authorizationId The corresponding authorization id
     * @see OAuthAuthorizationDetailsResponse
     */
    suspend fun getAuthorizationDetails(
        authorizationId: String,
    ): OAuthAuthorizationDetailsResponse

    /**
     * Approves an OAuth authorization request.
     * Only relevant when the OAuth 2.1 server is enabled in StitchDB Auth.
     * @param authorizationId The corresponding authorization id
     * @see OAuthRedirect
     */
    suspend fun approveAuthorization(
        authorizationId: String
    ): OAuthRedirect

    /**
     * Denies an OAuth authorization request.
     * Only relevant when the OAuth 2.1 server is enabled in StitchDB Auth.
     * @param authorizationId The corresponding authorization id
     */
    suspend fun denyAuthorization(
        authorizationId: String
    ): OAuthRedirect

    /**
     * Lists all OAuth grants that the authenticated user has authorized.
     * Only relevant when the OAuth 2.1 server is enabled in StitchDB Auth.
     */
    suspend fun listAuthorizationGrants(): List<OAuthGrant>

    /**
     * Revokes a user's OAuth grant for a specific client.
     * Only relevant when the OAuth 2.1 server is enabled in StitchDB Auth.
     * @param clientId The corresponding client id
     */
    suspend fun revokeGrant(
        clientId: String
    )

}

internal class OAuthApiImpl(
    private val api: AuthenticatedStitchApi
) : OAuthApi {

    override suspend fun getAuthorizationDetails(authorizationId: String): OAuthAuthorizationDetailsResponse {
        return api.get("oauth/authorizations/$authorizationId").safeBody()
    }

    override suspend fun approveAuthorization(authorizationId: String): OAuthRedirect {
        return api.postJson("oauth/authorizations/$authorizationId/consent", buildJsonObject {
            put("action", "approve")
        }).safeBody()
    }

    override suspend fun denyAuthorization(authorizationId: String): OAuthRedirect {
        return api.postJson("oauth/authorizations/$authorizationId/consent", buildJsonObject {
            put("action", "deny")
        }).safeBody()
    }

    override suspend fun listAuthorizationGrants(): List<OAuthGrant> {
        return api.get("user/oauth/grants").safeBody()
    }

    override suspend fun revokeGrant(clientId: String) {
        api.delete("user/oauth/grants") {
            parameter("client_id", clientId)
        }
    }
}