package com.stitchdb.plugins

import com.stitchdb.StitchClientBuilder
import com.stitchdb.StitchSerializer

/**
 * A plugin, which allows to customize the serialization
 */
interface CustomSerializationPlugin {

    /**
     * The serializer used for this module. Defaults to [StitchClientBuilder.defaultSerializer], when null.
     */
    val serializer: StitchSerializer

}