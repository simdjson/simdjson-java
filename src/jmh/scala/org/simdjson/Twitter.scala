package org.simdjson

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

case class User(default_profile: Boolean, screen_name: String)

case class Status(user: User)

case class Twitter(statuses: Array[Status])

object Twitter {
  val codec: JsonValueCodec[Twitter] = JsonCodecMaker.make
}
