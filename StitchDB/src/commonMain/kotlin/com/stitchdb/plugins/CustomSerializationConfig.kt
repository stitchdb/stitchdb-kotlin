package com.stitchdb.plugins

import com.stitchdb.StitchClientBuilder
import com.stitchdb.StitchSerializer

/**
 * A configuration for a plugin, which allows to customize the serialization
 */
interface CustomSerializationConfig {

    /**
     * The serializer used for this module. Defaults to [StitchClientBuilder.defaultSerializer], when null.
     */
    var serializer: StitchSerializer?

}