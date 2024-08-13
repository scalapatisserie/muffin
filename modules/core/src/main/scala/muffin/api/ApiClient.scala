package muffin.api

import java.time.*

import cats.effect.Concurrent
import cats.syntax.all.given
import fs2.*

import muffin.codec.*
import muffin.http.*
import muffin.http.Method.Get
import muffin.model.*

trait ApiClient[F[_], To[_], From[_]] {
  def command(name: String): String

  def dialog(name: String): String

  def actions(name: String): String

  def postToDirect(
      userId: UserId,
      message: Option[String] = None,
      props: Props = Props.empty,
      fileIds: List[FileId] = Nil
  ): F[Post]

  def postToChat(
      userIds: List[UserId],
      message: Option[String] = None,
      props: Props = Props.empty,
      fileIds: List[FileId] = Nil
  ): F[Post]

  def postToChannel(
      channelId: ChannelId,
      message: Option[String] = None,
      props: Props = Props.empty,
      fileIds: List[FileId] = Nil
  ): F[Post]

  def postToThread(
      channelId: ChannelId,
      rootPostId: MessageId,
      message: Option[String] = None,
      props: Props = Props.empty
  ): F[Post]

  def channel(userIds: List[UserId]): F[ChannelInfo]

  def createEphemeralPost(userId: UserId, channelId: ChannelId, message: String): F[Post]

  def getPost(postId: MessageId): F[Post]

  def deletePost(postId: MessageId): F[Unit]

  def updatePost(
      postId: MessageId,
      message: Option[String] = None,
      props: Props = Props.empty
  ): F[Post]

  def patchPost(
      postId: MessageId,
      message: Option[String] = None,
      props: Props = Props.empty
  ): F[Post]

  def getPostsByIds(messageId: List[MessageId]): F[List[Post]]

  def performAction(postId: MessageId, actionId: String): F[Unit]

  ////////////////////////////////////////////////
  def openDialog(triggerId: String, url: String, dialog: Dialog): F[Unit]

  def submitDialog[T: To](
      url: String,
      channelId: ChannelId,
      teamId: TeamId,
      submission: Map[String, String],
      state: T,
      callbackId: Option[String],
      cancelled: Boolean = false
  ): F[Unit]

  def members(channelId: ChannelId): Stream[F, ChannelMember]

  def getChannelByName(teamId: TeamId, name: String): F[ChannelInfo]

  def createEmoji(req: CreateEmojiRequest): F[EmojiInfo]

  def getEmojis(sorting: EmojiSorting = EmojiSorting.None): Stream[F, EmojiInfo]

  def getEmojiById(emojiId: EmojiId): F[EmojiInfo]

  def deleteEmoji(emojiId: EmojiId): F[Unit]

  def getEmojiByName(name: String): F[EmojiInfo]

  def searchEmoji(term: String, prefixOnly: Option[String]): F[List[EmojiInfo]]

  def autocompleteEmoji(name: String): F[List[EmojiInfo]]

  def createReaction(userId: UserId, postId: MessageId, emojiName: String): F[ReactionInfo]

  def getReactions(postId: MessageId): F[List[ReactionInfo]]

  def removeReaction(userId: UserId, postId: MessageId, emojiName: String): F[Unit]

  def bulkReactions(messages: List[MessageId]): F[Map[String, List[ReactionInfo]]]

  //  def userTeams(userId: UserId): F[List[Team]] =
  //    http.request[Nothing, List[Team]](
  //      cfg.baseUrl + s"/users/$userId/teams",
  //      Method.Get,
  //      Body.Empty,
  //      Map("Authorization" -> s"Bearer ${cfg.auth}")
  //    )

  def users(
      inTeam: Option[TeamId] = None,
      notInTeam: Option[TeamId] = None,
      inChannel: Option[ChannelId] = None,
      notInChannel: Option[ChannelId] = None,
      active: Option[Boolean] = None
  ): Stream[F, User]

  def user(userId: UserId): F[User]

  def usersById(userIds: List[UserId]): F[List[User]]

  def userByUsername(user: String): F[User]

  def usersByUsername(users: List[String]): F[List[User]]

  //  Preferences
  def getUserPreferences(userId: UserId): F[List[Preference]]

  def getUserPreferences(userId: UserId, category: String): F[List[Preference]]

  def getUserPreference(userId: UserId, category: String, name: String): F[Preference]

  def saveUserPreference(userId: UserId, preferences: List[Preference]): F[Unit]

  def deleteUserPreference(userId: UserId, preferences: List[Preference]): F[Unit]
  //  Preferences

  // Status
  def getUserStatus(userId: UserId): F[UserStatus]

  def getUserStatuses(users: List[UserId]): F[List[UserStatus]]

  def updateUserStatus(userId: UserId, statusUser: StatusUser): F[Unit]

  def updateCustomStatus(userId: UserId, customStatus: CustomStatus): F[Unit]

  def unsetCustomStatus(userId: UserId): F[Unit]
  // Status

  // Insights
  def getTopReactions(teamId: TeamId, timeRange: TimeRange): Stream[F, ReactionInsight]

  def getTopReactions(userId: UserId, timeRange: TimeRange, teamId: Option[TeamId]): Stream[F, ReactionInsight]

  def getTopChannels(teamId: TeamId, timeRange: TimeRange): Stream[F, ChannelInsight]

  def getTopChannels(userId: UserId, timeRange: TimeRange, teamId: Option[TeamId]): Stream[F, ChannelInsight]
  // Insights

  //  Roles
  def getAllRoles: F[List[RoleInfo]]

  def getRoleById(id: String): F[RoleInfo]

  def getRoleByName(name: String): F[RoleInfo]

  def updateRole(id: String, permissions: List[String]): F[RoleInfo]

  def getRoles(names: List[String]): F[List[RoleInfo]]

  //  Roles
  // Files

  def file(id: FileId): F[Array[Byte]]

  def uploadFile(req: UploadFileRequest): F[UploadFileResponse]

  // Files

  def websocket(): F[WebsocketBuilder[F, To, From]]
}

object ApiClient {

  class Live[F[_]: Concurrent, To[_], From[_]](http: HttpClient[F, To, From], cfg: ClientConfig)(
      codec: CodecSupport[To, From]
  )(using ZoneId)
    extends ApiClient[F, To, From] {

    import codec.{*, given}

    def command(name: String): String = cfg.serviceUrl + s"/commands/$name"

    def dialog(name: String): String = cfg.serviceUrl + s"/dialogs/$name"

    def actions(name: String): String = cfg.serviceUrl + s"/actions/$name"

    private def botId: F[UserId] = userByUsername(cfg.botName).map(_.id)

    def postToDirect(
        userId: UserId,
        message: Option[String] = None,
        props: Props = Props.empty,
        fileIds: List[FileId] = Nil
    ): F[Post] =
      for {
        id   <- botId
        info <- channel(id :: userId :: Nil)
        res  <- postToChannel(info.id, message, props, fileIds)
      } yield res

    def postToChat(
        userIds: List[UserId],
        message: Option[String] = None,
        props: Props = Props.empty,
        fileIds: List[FileId] = Nil
    ): F[Post] =
      for {
        id   <- botId
        info <- channel(id :: userIds)
        res  <- postToChannel(info.id, message, props, fileIds)
      } yield res

    def postToChannel(
        channelId: ChannelId,
        message: Option[String] = None,
        props: Props = Props.empty,
        fileIds: List[FileId] = Nil
    ): F[Post] =
      http.request(
        cfg.baseUrl + "/posts",
        Method.Post,
        jsonRaw
          .field("channel_id", channelId)
          .field("message", message)
          .field("file_ids", fileIds)
          .field("props", props)
          .build,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def postToThread(
        channelId: ChannelId,
        rootPostId: MessageId,
        message: Option[String] = None,
        props: Props = Props.empty
    ): F[Post] =
      http.request(
        cfg.baseUrl + "/posts",
        Method.Post,
        jsonRaw
          .field("channel_id", channelId)
          .field("message", message)
          .field("props", props)
          .field("root_id", rootPostId)
          .build,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def channel(userIds: List[UserId]): F[ChannelInfo] =
      if (userIds.size == 2)
        http.request[List[UserId], ChannelInfo](
          cfg.baseUrl + "/channels/direct",
          Method.Post,
          Body.Json(userIds),
          Map("Authorization" -> s"Bearer ${cfg.auth}")
        )
      else
        http.request[List[UserId], ChannelInfo](
          cfg.baseUrl + "/channels/group",
          Method.Post,
          Body.Json(userIds),
          Map("Authorization" -> s"Bearer ${cfg.auth}")
        )

    def createEphemeralPost(userId: UserId, channelId: ChannelId, message: String): F[Post] =
      http.request[String, Post](
        cfg.baseUrl + "/posts/ephemeral",
        Method.Post,
        jsonRaw.field("user_id", userId).field("channel_id", channelId).field("message", message).build,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def getPost(postId: MessageId): F[Post] =
      http.request[Nothing, Post](
        cfg.baseUrl + s"/posts/$postId",
        Method.Get,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def deletePost(postId: MessageId): F[Unit] =
      http.request[Nothing, Unit](
        cfg.baseUrl + s"/posts/$postId",
        Method.Delete,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def updatePost(
        postId: MessageId,
        message: Option[String] = None,
        props: Props = Props.empty
    ): F[Post] =
      http.request(
        cfg.baseUrl + s"/posts/$postId",
        Method.Put,
        jsonRaw.field("id", postId).field("message", message).field("props", props).build,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def patchPost(
        postId: MessageId,
        message: Option[String] = None,
        props: Props = Props.empty
    ): F[Post] =
      http.request(
        cfg.baseUrl + s"/posts/$postId/patch",
        Method.Put,
        jsonRaw.field("message", message).field("props", props).build,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def getPostsByIds(messageId: List[MessageId]): F[List[Post]] =
      http.request[List[MessageId], List[Post]](
        cfg.baseUrl + s"/posts/ids",
        Method.Put,
        Body.Json(messageId),
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def performAction(postId: MessageId, actionId: String): F[Unit] =
      http.request[Nothing, Unit](
        cfg.baseUrl + s"/posts/$postId/actions/$actionId",
        Method.Post,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    ////////////////////////////////////////////////
    def openDialog(triggerId: String, url: String, dialog: Dialog): F[Unit] =
      http.request(
        cfg.baseUrl + "/actions/dialogs/open",
        Method.Post,
        jsonRaw.field("trigger_id", triggerId).field("url", url).field("dialog", dialog).build,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def submitDialog[T: To](
        url: String,
        channelId: ChannelId,
        teamId: TeamId,
        submission: Map[String, String],
        state: T,
        callbackId: Option[String],
        cancelled: Boolean = false
    ): F[Unit] =
      http.request(
        cfg.baseUrl + "/actions/dialogs/submit",
        Method.Post,
        jsonRaw
          .field("url", url)
          .field("channel_id", channelId)
          .field("team_id", teamId)
          .field("submission", submission)
          .field("callback_id", callbackId)
          .field("state", state)
          .field("cancelled", cancelled)
          .build,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def members(channelId: ChannelId): Stream[F, ChannelMember] = {
      def single(page: Int): F[List[ChannelMember]] =
        http.request[Nothing, List[ChannelMember]](
          cfg.baseUrl + s"/channels/$channelId/members",
          Method.Get,
          Body.Empty,
          Map("Authorization" -> s"Bearer ${cfg.auth}"),
          _.withParam("page", page)
            .withParam("per_page", cfg.perPage)
        )

      Stream
        .unfoldEval(0) { page =>
          single(page).map(list =>
            if (list.isEmpty)
              None
            else
              Some((list, page + 1))
          )
        }
        .flatMap(Stream.emits)
    }

    def getChannelByName(teamId: TeamId, name: String): F[ChannelInfo] =
      http.request[Nothing, ChannelInfo](
        cfg.baseUrl + s"/teams/$teamId/channels/name/$name",
        Method.Get,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def createEmoji(req: CreateEmojiRequest): F[EmojiInfo] =
      http.request[Nothing, EmojiInfo](
        cfg.baseUrl + s"/emoji",
        Method.Post,
        Body.Multipart(
          MultipartElement.FileElement("image", FilePayload.fromBytes(req.image)) ::
            MultipartElement.StringElement(
              "emoji",
              jsonRaw.field("creator_id", req.creatorId).field("name", req.emojiName).build.value
            ) :: Nil
        ),
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def getEmojis(sorting: EmojiSorting = EmojiSorting.None): Stream[F, EmojiInfo] = {
      def single(page: Int) =
        http.request[Nothing, List[EmojiInfo]](
          cfg.baseUrl + "/emoji",
          Method.Get,
          Body.Empty,
          Map("Authorization" -> s"Bearer ${cfg.auth}"),
          _.withParam("page", page)
            .withParam("per_page", cfg.perPage)
            .withParam("sort", sorting)
        )

      Stream
        .unfoldEval(0) { page =>
          single(page).map(list =>
            if (list.isEmpty)
              None
            else
              Some((list, page + 1))
          )
        }
        .flatMap(Stream.emits)
    }

    def getEmojiById(emojiId: EmojiId): F[EmojiInfo] =
      http.request[Nothing, EmojiInfo](
        cfg.baseUrl + s"/emoji/$emojiId",
        Method.Get,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def deleteEmoji(emojiId: EmojiId): F[Unit] =
      http.request[Nothing, Unit](
        cfg.baseUrl + s"/emoji/$emojiId",
        Method.Delete,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def getEmojiByName(name: String): F[EmojiInfo] =
      http.request[Nothing, EmojiInfo](
        cfg.baseUrl + s"/emoji/name/$name",
        Method.Get,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def searchEmoji(term: String, prefixOnly: Option[String]): F[List[EmojiInfo]] =
      http.request(
        cfg.baseUrl + s"/emoji/search",
        Method.Post,
        jsonRaw.field("term", term).field("prefix_only", prefixOnly).build,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def autocompleteEmoji(name: String): F[List[EmojiInfo]] =
      http.request[Nothing, List[EmojiInfo]](
        cfg.baseUrl + s"/emoji/autocomplete",
        Method.Get,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}"),
        _.withParam("name", name)
      )

    def createReaction(userId: UserId, postId: MessageId, emojiName: String): F[ReactionInfo] =
      http.request(
        cfg.baseUrl + "/reactions",
        Method.Post,
        jsonRaw.field("user_id", userId).field("post_id", postId).field("emoji_name", emojiName).build,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def getReactions(postId: MessageId): F[List[ReactionInfo]] =
      http.request[Nothing, List[ReactionInfo]](
        cfg.baseUrl + s"/posts/$postId/reactions",
        Method.Get,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def removeReaction(userId: UserId, postId: MessageId, emojiName: String): F[Unit] =
      http.request[Nothing, Unit](
        cfg.baseUrl + s"/users/$userId/posts/$postId/reactions/$emojiName",
        Method.Delete,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def bulkReactions(messages: List[MessageId]): F[Map[String, List[ReactionInfo]]] =
      http.request[List[MessageId], Map[String, List[ReactionInfo]]](
        cfg.baseUrl + s"/posts/ids/reactions",
        Method.Post,
        Body.Json(messages),
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    //  def userTeams(userId: UserId): F[List[Team]] =
    //    http.request[Nothing, List[Team]](
    //      cfg.baseUrl + s"/users/$userId/teams",
    //      Method.Get,
    //      Body.Empty,
    //      Map("Authorization" -> s"Bearer ${cfg.auth}")
    //    )

    def users(
        inTeam: Option[TeamId] = None,
        notInTeam: Option[TeamId] = None,
        inChannel: Option[ChannelId] = None,
        notInChannel: Option[ChannelId] = None,
        active: Option[Boolean] = None
    ): Stream[F, User] = {
      def single(page: Int): F[List[User]] =
        http.request[Nothing, List[User]](
          cfg.baseUrl +
            s"/users",
          Method.Get,
          Body.Empty,
          Map("Authorization" -> s"Bearer ${cfg.auth}"),
          _.withParam("page", page)
            .withParam("per_page", cfg.perPage)
            .withParam("in_team", inTeam)
            .withParam("not_in_team", notInTeam)
            .withParam("in_channel", inChannel)
            .withParam("not_in_channel", notInChannel)
            .withParam("active", active)
        )

      Stream
        .unfoldEval(0) { page =>
          single(page).map(list =>
            if (list.isEmpty)
              None
            else
              Some((list, page + 1))
          )
        }
        .flatMap(Stream.emits)
    }

    def user(userId: UserId): F[User] =
      http.request[Nothing, User](
        cfg.baseUrl + s"/users/$userId",
        Method.Get,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def usersById(userIds: List[UserId]): F[List[User]] =
      http.request[List[UserId], List[User]](
        cfg.baseUrl + s"/users/ids",
        Method.Post,
        Body.Json(userIds),
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def userByUsername(user: String): F[User] =
      http.request[Nothing, User](
        cfg.baseUrl + s"/users/username/$user",
        Method.Get,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def usersByUsername(users: List[String]): F[List[User]] =
      http.request[List[String], List[User]](
        cfg.baseUrl + s"/users/usernames",
        Method.Post,
        Body.Json(users),
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    //  Preferences
    def getUserPreferences(userId: UserId): F[List[Preference]] =
      http.request[Nothing, List[Preference]](
        cfg.baseUrl + s"/users/$userId/preferences",
        Method.Get,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def getUserPreferences(userId: UserId, category: String): F[List[Preference]] =
      http.request[Nothing, List[Preference]](
        cfg.baseUrl + s"/users/$userId/preferences/$category",
        Method.Get,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def getUserPreference(userId: UserId, category: String, name: String): F[Preference] =
      http.request[Nothing, Preference](
        cfg.baseUrl + s"/users/$userId/preferences/$category/name/$name",
        Method.Get,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def saveUserPreference(userId: UserId, preferences: List[Preference]): F[Unit] =
      http.request[List[Preference], Unit](
        cfg.baseUrl + s"/users/$userId/preferences",
        Method.Put,
        Body.Json(preferences),
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def deleteUserPreference(userId: UserId, preferences: List[Preference]): F[Unit] =
      http.request[List[Preference], Unit](
        cfg.baseUrl + s"/users/$userId/preferences/delete",
        Method.Post,
        Body.Json(preferences),
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )
    //  Preferences

    // Status
    def getUserStatus(userId: UserId): F[UserStatus] =
      http.request[Nothing, UserStatus](
        cfg.baseUrl + s"/users/$userId/status",
        Method.Get,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def getUserStatuses(users: List[UserId]): F[List[UserStatus]] =
      http.request[List[UserId], List[UserStatus]](
        cfg.baseUrl + s"/users/status/ids",
        Method.Post,
        Body.Json(users),
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def updateUserStatus(userId: UserId, statusUser: StatusUser): F[Unit] =
      http.request[UpdateUserStatusRequest, Unit](
        cfg.baseUrl + s"/users/$userId/status/custom",
        Method.Put,
        Body.Json(UpdateUserStatusRequest(userId, statusUser)),
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def updateCustomStatus(userId: UserId, customStatus: CustomStatus): F[Unit] =
      http.request[CustomStatus, Unit](
        cfg.baseUrl + s"/users/$userId/status/custom",
        Method.Put,
        Body.Json(customStatus),
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def unsetCustomStatus(userId: UserId): F[Unit] =
      http.request[Nothing, Unit](
        cfg.baseUrl + s"/users/$userId/status/custom",
        Method.Delete,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )
    // Status

    // Insights
    def getTopReactions(teamId: TeamId, timeRange: TimeRange): Stream[F, ReactionInsight] = {
      def single(page: Int) =
        http.request[Nothing, ListWrapper[ReactionInsight]](
          cfg.baseUrl + s"/teams/$teamId/top/reactions",
          Method.Get,
          Body.Empty,
          Map("Authorization" -> s"Bearer ${cfg.auth}"),
          _.withParam("time_range", timeRange)
            .withParam("page", page)
            .withParam("per_page", cfg.perPage)
        )

      Stream
        .unfoldEval(0) { page =>
          single(page).map(lw =>
            if (lw.hasNext)
              Some(lw.items -> (page + 1))
            else
              None
          )
        }
        .flatMap(Stream.emits)
    }

    def getTopReactions(userId: UserId, timeRange: TimeRange, teamId: Option[TeamId]): Stream[F, ReactionInsight] = {
      def single(page: Int) =
        http.request[Nothing, ListWrapper[ReactionInsight]](
          cfg.baseUrl +
            s"/users/$userId/top/reactions",
          Method.Get,
          Body.Empty,
          Map("Authorization" -> s"Bearer ${cfg.auth}"),
          _.withParam("time_range", timeRange)
            .withParam("page", page)
            .withParam("per_page", cfg.perPage)
            .withParam("team_id", teamId)
        )

      Stream
        .unfoldEval(0) { page =>
          single(page).map(lw =>
            if (lw.hasNext)
              Some(lw.items -> (page + 1))
            else
              None
          )
        }
        .flatMap(Stream.emits)
    }

    def getTopChannels(teamId: TeamId, timeRange: TimeRange): Stream[F, ChannelInsight] = {
      def single(page: Int) =
        http.request[Nothing, ListWrapper[ChannelInsight]](
          cfg.baseUrl + s"/teams/$teamId/top/channels",
          Method.Get,
          Body.Empty,
          Map("Authorization" -> s"Bearer ${cfg.auth}"),
          _.withParam("time_range", timeRange)
            .withParam("page", page)
            .withParam("per_page", cfg.perPage)
        )

      Stream
        .unfoldEval(0) { page =>
          single(page).map(lw =>
            if (lw.hasNext)
              Some(lw.items -> (page + 1))
            else
              None
          )
        }
        .flatMap(Stream.emits)
    }

    def getTopChannels(userId: UserId, timeRange: TimeRange, teamId: Option[TeamId]): Stream[F, ChannelInsight] = {
      def single(page: Int) =
        http.request[Nothing, ListWrapper[ChannelInsight]](
          cfg.baseUrl +
            s"/users/$userId/top/channels",
          Method.Get,
          Body.Empty,
          Map("Authorization" -> s"Bearer ${cfg.auth}"),
          _.withParam("time_range", timeRange)
            .withParam("page", page)
            .withParam("per_page", cfg.perPage)
            .withParam("team_id", teamId)
        )

      Stream
        .unfoldEval(0) { page =>
          single(page).map(lw =>
            if (lw.hasNext)
              Some(lw.items -> (page + 1))
            else
              None
          )
        }
        .flatMap(Stream.emits)
    }
    // Insights

    //  Roles
    def getAllRoles: F[List[RoleInfo]] =
      http.request[Nothing, List[RoleInfo]](
        cfg.baseUrl + s"/roles",
        Method.Get,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def getRoleById(id: String): F[RoleInfo] =
      http.request[Nothing, RoleInfo](
        cfg.baseUrl + s"/roles/$id",
        Method.Get,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def getRoleByName(name: String): F[RoleInfo] =
      http.request[Nothing, RoleInfo](
        cfg.baseUrl + s"/roles/name/$name",
        Method.Get,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def updateRole(id: String, permissions: List[String]): F[RoleInfo] =
      http.request[List[String], RoleInfo](
        cfg.baseUrl + s"/roles/$id/patch",
        Method.Patch,
        Body.Json(permissions),
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def getRoles(names: List[String]): F[List[RoleInfo]] =
      http.request[List[String], List[RoleInfo]](
        cfg.baseUrl + s"/roles/names",
        Method.Post,
        Body.Json(names),
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    //  Roles
    // Files

    def file(id: FileId): F[Array[Byte]] =
      http.requestRawData[Nothing](
        cfg.baseUrl + s"/files/$id",
        Method.Get,
        Body.Empty,
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    def uploadFile(req: UploadFileRequest): F[UploadFileResponse] =
      http.request[Nothing, UploadFileResponse](
        cfg.baseUrl + "/files",
        Method.Post,
        Body.Multipart(
          List(
            MultipartElement.FileElement("files", req.payload),
            MultipartElement.StringElement("channel_id", req.channelId.value)
          )
        ),
        Map("Authorization" -> s"Bearer ${cfg.auth}")
      )

    // Files
    // WebSocket
    /*
    Every call is a new websocket connection
     */
    def websocket(): F[WebsocketBuilder[F, To, From]] =
      Websocket
        .ConnectionBuilder
        .build(
          http,
          Map("Authorization" -> s"Bearer ${cfg.auth}"),
          codec,
          cfg.baseUrl,
          cfg.websocketConnection.retryPolicy.backoffSettings
        )
    // WebSocket

  }

}
