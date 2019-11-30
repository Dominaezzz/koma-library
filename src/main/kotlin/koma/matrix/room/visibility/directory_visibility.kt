package koma.matrix.room.visibility

import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.ToJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RoomVisibility {
    @SerialName("public")
    @Json(name = "public") Public,
    @SerialName( "private")
    @Json(name = "private") Private;

    override fun toString(): String {
        return when (this) {
            RoomVisibility.Public -> "public"
            RoomVisibility.Private -> "private"
        }
    }
}

class RoomVisibilityCaseInsensitiveAdapter {
    @ToJson
    fun toJson(hv: RoomVisibility): String {
        return hv.toString()
    }

    @FromJson
    fun fromJson(str: String): RoomVisibility {
        return when (str.toLowerCase()) {
            "public" -> RoomVisibility.Public
            "private" -> RoomVisibility.Private
            else -> throw JsonDataException("room visibility not one of ${RoomVisibility.values()}")
        }
    }
}
