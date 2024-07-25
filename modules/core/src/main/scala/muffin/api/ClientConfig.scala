package muffin.api

import scala.concurrent.duration.FiniteDuration

case class ClientConfig(
    baseUrl: String,
    auth: String,
    botName: String,
    serviceUrl: String,
    websocketConnection: WebsocketConnectionConfig,
    perPage: Int = 60
)

case class WebsocketConnectionConfig(
    retryPolicy: RetryPolicy
)

case class RetryPolicy(
    backoffSettings: BackoffSettings
)

case class BackoffSettings(
    initialDelay: FiniteDuration,
    maxDelayThreshold: FiniteDuration,
    multiply: Int = 2
)
