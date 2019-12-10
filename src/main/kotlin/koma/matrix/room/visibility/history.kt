package koma.matrix.room.visibility

import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.ToJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class HistoryVisibility {
    @SerialName("invited")
    @Json(name = "invited") Invited,
    @SerialName( "joined")
    @Json(name = "joined") Joined,
    @SerialName("shared")
    @Json(name = "shared") Shared,
    @SerialName("world_readable")
    @Json(name = "world_readable") WorldReadable;

    companion object {
        fun fromString(hvstr: String): HistoryVisibility {
            val vis = when (hvstr) {
                "invited" -> HistoryVisibility.Invited
                "joined" -> HistoryVisibility.Joined
                "shared" -> HistoryVisibility.Shared
                "world_readable" -> HistoryVisibility.WorldReadable
                "Invited" -> HistoryVisibility.Invited
                "Joined" -> HistoryVisibility.Joined
                "Shared" -> HistoryVisibility.Shared
                "WorldReadable" -> HistoryVisibility.WorldReadable
                else -> throw JsonDataException("$hvstr is not one of ${HistoryVisibility.values()}")
            }
            return vis
        }
    }
}

class HistoryVisibilityCaseInsensitiveAdapter {
    @ToJson
    fun toJson(hv: HistoryVisibility): String {
        return when (hv) {
            HistoryVisibility.Invited -> "invited"
            HistoryVisibility.Shared -> "shared"
            HistoryVisibility.Joined -> "joined"
            HistoryVisibility.WorldReadable -> "world_readable"
        }
    }

    @FromJson
    fun fromJson(str: String): HistoryVisibility {
        return HistoryVisibility.fromString(str)
    }
}
