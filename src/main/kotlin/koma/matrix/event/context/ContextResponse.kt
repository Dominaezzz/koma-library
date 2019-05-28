package koma.matrix.event.context

import koma.matrix.event.room_message.RoomEvent
import koma.matrix.json.RawJson


data class ContextResponse(
        /**
         *A token that can be used to paginate backwards with.
         * Actually, I'm afraid it can be null
         */
        val start: String,
        val end: String,
        val events_before: List<RawJson<RoomEvent>>,
        val event: RawJson<RoomEvent>,
        val events_after: List<RawJson<RoomEvent>>,
        val state: List<Map<String, Any>>
)
