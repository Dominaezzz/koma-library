package koma.matrix

import koma.*
import koma.KResultF
import koma.util.KResult as Result
import koma.controller.sync.longPollTimeout
import koma.matrix.event.EventId
import koma.matrix.event.context.ContextResponse
import koma.matrix.event.room_message.RoomEvent
import koma.matrix.event.room_message.RoomEventType
import koma.matrix.event.room_message.chat.M_Message
import koma.matrix.event.room_message.state.RoomAvatarContent
import koma.matrix.event.room_message.state.RoomCanonAliasContent
import koma.matrix.event.room_message.state.RoomNameContent
import koma.matrix.json.MoshiInstance
import koma.matrix.json.RawJson
import koma.matrix.media.parseMediaUrl
import koma.matrix.pagination.FetchDirection
import koma.matrix.pagination.RoomBatch
import koma.matrix.publicapi.rooms.RoomDirectoryQuery
import koma.matrix.room.admin.BanRoomResult
import koma.matrix.room.admin.CreateRoomResult
import koma.matrix.room.admin.CreateRoomSettings
import koma.matrix.room.admin.MemberBanishment
import koma.matrix.room.naming.ResolveRoomAliasResult
import koma.matrix.room.naming.RoomId
import koma.matrix.room.participation.LeaveRoomResult
import koma.matrix.room.participation.invite.InviteMemResult
import koma.matrix.room.participation.invite.InviteUserData
import koma.matrix.room.participation.join.JoinRoomResult
import koma.matrix.sync.SyncResponse
import koma.matrix.user.AvatarUrl
import koma.matrix.user.identity.DisplayName
import koma.network.client.okhttp.AppHttpClient
import koma.util.coroutine.adapter.retrofit.await
import koma.util.coroutine.adapter.retrofit.awaitMatrix
import koma.util.coroutine.adapter.retrofit.extractMatrix
import koma.util.flatMap
import koma.util.getOrThrow
import koma.util.onFailure
import mu.KotlinLogging
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

data class SendResult(
        val event_id: EventId
)

class UpdateAvatarResult()

/**
 * Api that requires access_token
 * the api only needs to be defined as an interface
 * retrofit/moshi handles the rest
 */
interface MatrixAccessApiDef {
    @POST("createRoom")
    fun createRoom(@Query("access_token") token: String,
                   @Body roomSettings: CreateRoomSettings): Call<CreateRoomResult>

    @POST("rooms/{roomId}/join")
    fun joinRoom(@Path("roomId") roomId: String,
                 @Query("access_token") token: String)
            : Call<JoinRoomResult>

    @POST("rooms/{roomId}/leave")
    fun leaveRoom(@Path("roomId") roomId: RoomId,
                  @Query("access_token") token: String)
            : Call<LeaveRoomResult>

    @GET("directory/room/{roomAlias}")
    fun resolveRoomAlias(@Path("roomAlias") roomAlias: String): Call<ResolveRoomAliasResult>

    @PUT("directory/room/{roomAlias}")
    fun putRoomAlias(@Path("roomAlias") roomAlias: String,
                     @Query("access_token") token: String,
                     @Body roomInfo: RoomInfo): Call<EmptyResult>

    @DELETE("directory/room/{roomAlias}")
    fun deleteRoomAlias(@Path("roomAlias") roomAlias: String,
                        @Query("access_token") token: String
    ): Call<EmptyResult>

    @GET("publicRooms")
    fun publicRooms(@Query("since") since: String? = null,
                    @Query("limit") limit: Int = 20
    ): Call<RoomBatch<DiscoveredRoom>>

    @POST("publicRooms")
    fun findPublicRooms(
            @Query("access_token") token: String,
            @Body query: RoomDirectoryQuery
    ): Call<RoomBatch<DiscoveredRoom>>



    @GET("rooms/{roomId}/messages")
    fun getMessages(
            @Path("roomId") roomId: RoomId,
            @Query("access_token") token: String,
            @Query("from") from: String,
            @Query("dir") dir: FetchDirection,
            // optional params
            @Query("limit") limit: Int = 100,
            @Query("to") to: String? = null
    ): Call<Chunked<RawJson<RoomEvent>>>

    @POST("rooms/{roomId}/invite")
    fun inviteUser(@Path("roomId") roomId: String,
                   @Query("access_token") token: String,
                   @Body invitation: InviteUserData
    ): Call<InviteMemResult>

    @POST("rooms/{roomId}/ban")
    fun banUser(@Path("roomId") roomId: String,
                @Query("access_token") token: String,
                @Body banishment: MemberBanishment
    ): Call<BanRoomResult>

    @PUT("rooms/{roomId}/send/{eventType}/{txnId}")
    fun sendMessageEvent(
            @Path("roomId") roomId: RoomId,
            @Path("eventType") eventType: RoomEventType,
            @Path("txnId") txnId: String,
            @Query("access_token") token: String,
            @Body message: M_Message): Call<SendResult>

    @PUT("rooms/{roomId}/state/{eventType}")
    fun sendStateEvent(
            @Path("roomId") roomId: RoomId,
            @Path("eventType") type: RoomEventType,
            @Query("access_token") token: String,
            @Body content: Any): Call<SendResult>

    @GET("rooms/{roomId}/state/{eventType}")
    fun getStateEvent(
            @Path("roomId") roomId: RoomId,
            @Path("eventType") type: RoomEventType,
            @Query("access_token") token: String
    ): Call<Map<String, Any>>

    @GET("rooms/{roomId}/context/{eventId}")
    fun getEventContext(@Path("roomId") roomId: RoomId,
                 @Path("eventId") eventId: EventId,
                        @Query("limit") limit: Int = 2,
                 @Query("access_token") token: String
    ): Call<ContextResponse>


    @GET("sync")
    fun getEvents(@Query("since") from: String? = null,
                  @Query("access_token") token: String,
                  @Query("full_state") full_state: Boolean = false,
                  @Query("timeout") timeout: Int = longPollTimeout * 1000,
                  @Query("filter") filter: String? = null)
            : Call<SyncResponse>

    @PUT("profile/{userId}/avatar_url")
    fun updateAvatar(@Path("userId") user_id: UserId,
                     @Query("access_token") token: String,
                     @Body avatarUrl: AvatarUrl): Call<UpdateAvatarResult>

    @GET("profile/{userId}/avatar_url")
    fun getAvatar(@Path("userId") user_id: UserId): Call<AvatarUrl>

    @PUT("profile/{userId}/displayname")
    fun updateDisplayName(@Path("userId") user_id: UserId,
                     @Query("access_token") token: String,
                     @Body body: DisplayName): Call<EmptyResult>

    @GET("profile/{userId}/displayname")
    fun getDisplayName(@Path("userId") user_id: String
    ): Call<DisplayName>
}

/**
 * usually at path _matrix/media/r0/
 */
internal interface MatrixMediaApiDef {
    @POST("upload")
    fun uploadMedia(@Header("Content-Type") type: String,
                    @Query("access_token") token: String,
                    @Body content: RequestBody
    ): Call<UploadResponse>
}

class MatrixApi(
        private val token: String,
        val userId: UserId,
        /***
         * homeserver base address such as https://matrix.org
         */
        val server: HttpUrl,
        apiPath: String = "_matrix/client/r0/",
        private val mediaPath: String = "_matrix/media/r0/",
        /**
         * share OkHttpClient as much as possible to conserve resources
         */
        http: AppHttpClient) {

    val service: MatrixAccessApiDef
    private val longPollService: MatrixAccessApiDef
    private val mediaService: MatrixMediaApiDef

    private var _lastTxnId = AtomicLong()
    private fun getTxnId(): String {
        val t = System.currentTimeMillis()
        val id = _lastTxnId.accumulateAndGet(t) { value, given ->
            if (given > value) given else value + 1
        }
        return id.toString()
    }

    suspend fun createRoom(settings: CreateRoomSettings): KResultF<CreateRoomResult> {
        return service.createRoom(token, settings).awaitMatrix()
    }

    suspend fun getRoomMessages(roomId: RoomId, from: String, direction: FetchDirection, to: String?=null
    ): KResultF<Chunked<RawJson<RoomEvent>>> {
        return service.getMessages(roomId, token, from, direction, to=to).awaitMatrix()
    }

    suspend fun joinRoom(roomid: RoomId): KResultF<JoinRoomResult> {
        return service.joinRoom(roomid.id, token).awaitMatrix()
    }

    suspend fun getEventContext(roomid: RoomId, eventId: EventId): KResultF<ContextResponse> {
        return service.getEventContext(roomid, eventId,token= token).awaitMatrix()
    }

    suspend fun uploadFile(file: File, contentType: MediaType): KResultF<UploadResponse> {
        val req = RequestBody.create(contentType, file)
        return mediaService.uploadMedia(contentType.toString(), token, req).awaitMatrix()
    }
    suspend fun uploadByteArray(contentType: MediaType, byteArray: ByteArray): KResultF<UploadResponse> {
        val req = RequestBody.create(contentType, byteArray)
        return mediaService.uploadMedia(contentType.toString(), token, req).awaitMatrix()
    }

    suspend fun inviteMember(
          room: RoomId,
          memId: UserId): KResultF<InviteMemResult> =
            service.inviteUser(room.id, token, InviteUserData(memId)).awaitMatrix()

    suspend fun updateAvatar(user_id: UserId, avatarUrl: AvatarUrl): KResultF<UpdateAvatarResult>
            = service.updateAvatar(user_id, token, avatarUrl).awaitMatrix()

    suspend fun updateDisplayName(newname: String): KResultF<EmptyResult>
            = service.updateDisplayName(
            this.userId, token,
            DisplayName(newname)).awaitMatrix()

    suspend fun getDisplayName(user: String): Result<DisplayName, Failure> {
        return service.getDisplayName(user).await().flatMap { it.extractMatrix() }
    }

    suspend fun setRoomIcon(roomId: RoomId, content: RoomAvatarContent):KResultF<SendResult>
            = service.sendStateEvent(roomId, RoomEventType.Avatar, token, content).awaitMatrix()

    suspend fun banMember(
            roomid: RoomId,
            memId: UserId
    ): KResultF<BanRoomResult> = service.banUser(roomid.id, token, MemberBanishment(memId)).awaitMatrix()

    suspend fun leavingRoom(roomid: RoomId): KResultF<LeaveRoomResult>
            = service.leaveRoom(roomid, token).awaitMatrix()

    suspend fun putRoomAlias(roomid: RoomId, alias: String): KResultF<EmptyResult>
            = service.putRoomAlias(alias, token, RoomInfo(roomid)).awaitMatrix()

    suspend fun deleteRoomAlias(alias: String): KResultF<EmptyResult>
            = service.deleteRoomAlias(alias, token).awaitMatrix()

    suspend fun setRoomCanonicalAlias(roomid: RoomId, canonicalAlias: RoomCanonAliasContent)
            = service.sendStateEvent(roomid, RoomEventType.CanonAlias, token, canonicalAlias).awaitMatrix()

    suspend fun setRoomName(roomid: RoomId, name: RoomNameContent)
            = service.sendStateEvent(roomid, RoomEventType.Name, token, name).awaitMatrix()

    suspend fun getRoomName(roomId: RoomId): Result<Optional<String>, Failure> {
        val r = service.getStateEvent(roomId, RoomEventType.Name, token).awaitMatrix()
        r.onFailure { e ->
            return if (e is HttpFailure && e.http_code == 404) {
                Result.success(Optional.empty())
            }else if (e is MatrixFailure && e.errcode == "M_NOT_FOUND") {
                Result.success(Optional.empty())
            } else {
                Result.failure(e)
            }
        }
        val n = r.getOrThrow().get("name") as String
        return Result.success(Optional.ofNullable(n))
    }
    suspend fun getRoomAvatar(roomId: RoomId): Result<Optional<String>, Failure> {
        val r = service.getStateEvent(roomId, RoomEventType.Avatar, token).awaitMatrix()
        if (r.isFailure) {
            val e = r.failureOrNull()!!
            return if (e is HttpFailure && e.http_code == 404) {
                Result.success(Optional.empty())
            } else if (e is MatrixFailure && e.errcode == "M_NOT_FOUND") {
                Result.success(Optional.empty())
            }   else{
                Result.failure(e)
            }
        } else {
            logger.debug { "got room avatar ${r.getOrNull()}" }
            return Result.success(Optional.ofNullable(r.getOrNull()!!.get("url") as String))
        }
    }
    suspend fun resolveRoomAlias(roomAlias: String): KResultF<ResolveRoomAliasResult> {
        val call: Call<ResolveRoomAliasResult> = service.resolveRoomAlias(roomAlias)
        return call.awaitMatrix()
    }

    suspend fun sendMessage(roomId: RoomId, message: M_Message
    ): Result<SendResult, Failure> {
        val tid = getTxnId()
        logger.info { "sending to room $roomId message $tid with content $message" }

        val res = service.sendMessageEvent(roomId, RoomEventType.Message, tid, token, message).awaitMatrix()

        return res
    }

    suspend fun findPublicRooms(query: RoomDirectoryQuery) = service.findPublicRooms(token, query).awaitMatrix()

    suspend fun asyncEvents(from: String?): Result<SyncResponse, Failure> {
        val syRes = longPollService.getEvents(from, token).awaitMatrix()
        return syRes
    }

    fun getMediaUrl(addr: String): Result<HttpUrl, KomaFailure> {
        return parseMediaUrl(addr, server, mediaPath)
    }

    init {
        val moshi = MoshiInstance.moshi
        val apiURL = server.newBuilder().addPathSegments(apiPath).build()
        val rb = Retrofit.Builder()
                .baseUrl(apiURL)
                .addConverterFactory(MoshiConverterFactory.create(moshi))

        service = rb.client(http.client).build()
                .create(MatrixAccessApiDef::class.java)

        // need to set a longer timeout for the sync api
        val longPollClient = http.builder.readTimeout(longPollTimeout.toLong() + 10, TimeUnit.SECONDS).build()
        longPollService = rb.client(longPollClient).build().create(MatrixAccessApiDef::class.java)

        val mu = server.newBuilder().addPathSegments(mediaPath).build()
        mediaService = Retrofit.Builder()
                .baseUrl(mu)
                .addConverterFactory(MoshiConverterFactory.create())
                .client(http.client)
                .build().create(MatrixMediaApiDef::class.java)
    }

}


data class AuthedUser(
        val access_token: String,
        val user_id: UserId)

data class UserPassword(
        val type: String = "m.login.password",
        // name only, without @ or :
        val user: String,
        val password: String
)
interface MatrixLoginApi {
    @POST("_matrix/client/r0/login")
    suspend fun login(@Body userpass: UserPassword): Call<AuthedUser>
}

suspend fun login(userpass: UserPassword, server: String, http: AppHttpClient):
        KResultF<AuthedUser> {
    val moshi = MoshiInstance.moshi
    val client = http.client
    val serverUrl = if (server.endsWith('/')) server else "$server/"
    val retrofit = Retrofit.Builder()
            .baseUrl(serverUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    val service = retrofit.create(MatrixLoginApi::class.java)
    val auth_call: Call<AuthedUser> = service.login(userpass)
    return auth_call.awaitMatrix()
}