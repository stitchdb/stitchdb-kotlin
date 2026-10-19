package com.stitchdb.auth

import com.stitchdb.StitchClient
import com.stitchdb.annotations.StitchExperimental
import com.stitchdb.auth.user.UserSession

/**
 * Configuration for plugins depending on the Auth plugin
 */
interface AuthDependentPluginConfig {

    /**
     * Whether to require a valid [UserSession] in the [Auth] plugin to make any request with this plugin. The [StitchClient.stitchdbKey] cannot be used as fallback.
     */
    @StitchExperimental
    var requireValidSession: Boolean

}
