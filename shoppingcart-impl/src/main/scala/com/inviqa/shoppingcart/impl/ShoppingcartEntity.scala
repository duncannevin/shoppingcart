package com.inviqa.shoppingcart.impl

import java.time.LocalDateTime

import akka.Done
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, PersistentEntity}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import play.api.libs.json.{Format, Json}

import scala.collection.immutable.Seq

class ShoppingcartEntity extends PersistentEntity {

  override type Command = ShoppingcartCommand[_]
  override type Event = ShoppingcartEvent
  override type State = ShoppingcartState

  /**
    * The initial state. This is used if there is no snapshotted state to be found.
    */
  override def initialState: ShoppingcartState = ShoppingcartState(List.empty[String])

  /**
    * An entity can define different behaviours for different states, so the behaviour
    * is a function of the current state to a set of actions.
    */
  override def behavior: Behavior = {
    case ShoppingcartState(_) => Actions()
    .onCommand[AddToCartCommand, Done] {
      case (AddToCartCommand(product), context, state) =>
        context.thenPersist(
          AddedToCartEvent(product)
        ) { _ =>
          context.reply(Done)
        }
    }.onCommand[RemoveFromCartCommand, Done] {
      case (RemoveFromCartCommand(product), context, state) =>
        context.thenPersist(
          RemoveFromCartEvent(product)
        ) { _ =>
          context.reply(Done)
        }
    }.onReadOnlyCommand[ShowCartCommand.type, List[String]] {
      case (ShowCartCommand, context, state) => context.reply(state.products)
    }.onEvent {
      case (AddedToCartEvent(product), state) =>
        ShoppingcartState(product :: state.products)
      case (RemoveFromCartEvent(product), state) =>
        ShoppingcartState(state.products.filterNot(_ == product))
    }
  }
}

final case class AddToCartCommand(product: String) extends ShoppingcartCommand[Done]

case object ShowCartCommand extends ShoppingcartCommand[List[String]]

case class RemoveFromCartCommand(product: String) extends ShoppingcartCommand[Done]

case class AddedToCartEvent(product: String) extends ShoppingcartEvent

case class RemoveFromCartEvent(product: String) extends ShoppingcartEvent

/**
  * The current state held by the persistent entity.
  */
case class ShoppingcartState(products: List[String])

object ShoppingcartState {
  implicit val format: Format[ShoppingcartState] = Json.format
}

/**
  * This interface defines all the events that the ShoppingcartEntity supports.
  */
sealed trait ShoppingcartEvent extends AggregateEvent[ShoppingcartEvent] {
  def aggregateTag: AggregateEventTag[ShoppingcartEvent] = ShoppingcartEvent.Tag
}

object ShoppingcartEvent {
  val Tag: AggregateEventTag[ShoppingcartEvent] = AggregateEventTag[ShoppingcartEvent]
}

/**
  * This interface defines all the commands that the ShoppingcartEntity supports.
  */
sealed trait ShoppingcartCommand[R] extends ReplyType[R]

/**
  * Akka serialization, used by both persistence and remoting, needs to have
  * serializers registered for every type serialized or deserialized. While it's
  * possible to use any serializer you want for Akka messages, out of the box
  * Lagom provides support for JSON, via this registry abstraction.
  *
  * The serializers are registered here, and then provided to Lagom in the
  * application loader.
  */
object ShoppingcartSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer[ShoppingcartState]
  )
}
