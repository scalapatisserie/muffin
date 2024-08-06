package muffin.model.websocket

import scala.concurrent.Future
import scala.util.Try

import muffin.api.ApiTestSupport
import muffin.model.websocket.domain.*

class EntityTypeParsingTest() extends ApiTestSupport {

  private val integration = "parsing"

  Feature(s"EntityType parsing $integration") {
    Scenario(s"Parse from snake case, correct raw types $integration") {
      val rawTypes = List(
        "added_to_team",
        "authentication_challenge",
        "channel_converted",
        "channel_created",
        "channel_deleted",
        "channel_member_updated",
        "channel_updated",
        "channel_viewed",
        "config_changed",
        "delete_team",
        "direct_added",
        "emoji_added",
        "ephemeral_message",
        "group_added",
        "hello",
        "leave_team",
        "license_changed",
        "memberrole_updated",
        "new_user",
        "plugin_disabled",
        "plugin_enabled",
        "plugin_statuses_changed",
        "post_deleted",
        "post_edited",
        "post_unread",
        "posted",
        "preference_changed",
        "preferences_changed",
        "preferences_deleted",
        "reaction_added",
        "reaction_removed",
        "response",
        "role_updated",
        "status_change",
        "typing",
        "update_team",
        "user_added",
        "user_removed",
        "user_role_updated",
        "user_updated",
        "dialog_opened",
        "thread_updated",
        "thread_follow_changed",
        "thread_read_changed"
      )

      val result: List[EventType] = rawTypes.map(EventType.fromSnakeCase)

      Future.successful(assert(result.length == EventType.values.length))
    }
    Scenario(s"Parse from snake case, incorrect kebab case raw types $integration") {
      val rawTypes = List(
        "added-to-team"
      )

      val result = rawTypes.map(tpe => Try(EventType.fromSnakeCase(tpe)))

      Future.successful(assert(result.head.isFailure))
    }
  }

}
