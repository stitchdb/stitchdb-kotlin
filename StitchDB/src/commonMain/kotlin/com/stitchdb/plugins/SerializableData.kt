package com.stitchdb.plugins

import com.stitchdb.StitchSerializer
import com.stitchdb.annotations.StitchInternal

/**
 * An interface for data that can be serialized with [StitchSerializer]
 */
interface SerializableData {

    /**
     * The serializer used to serialize this data
     */
    @StitchInternal
    val serializer: StitchSerializer

}