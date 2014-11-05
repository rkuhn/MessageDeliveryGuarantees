package com.rolandkuhn.guarantees

import akka.persistence.PersistentActor
import akka.actor.ActorRef
import akka.persistence.AtLeastOnceDelivery
import java.time.LocalDateTime
import scala.concurrent.duration._
import akka.actor.ActorPath
import akka.actor.Identify
import akka.actor.ActorIdentity
import akka.actor.Stash
import akka.actor.ReceiveTimeout

object Sender {
  case class Send(text: String)
  case class Sent(text: String, confirmationId: Int)
  case class AwaitConfirmation(id: Int)
  case class Confirmed(id: Int)
  case class InternalConfirmation(seq: Int, id: Long)
}

class Sender(target: ActorRef) extends PersistentActor with AtLeastOnceDelivery with Stash {
  import Sender._
  import context.dispatcher

  def log(msg: String) = println(s"[${LocalDateTime.now}] send: $msg")

  override def persistenceId = "sender"
  override def redeliverInterval = 1.second

  var nextSeq = 0
  var outstanding = Map.empty[Int, Set[ActorRef]]

  def receiveCommand = {
    case Send(text) =>
      log(s"sending '$text' with seq=$nextSeq")
      persist(Sent(text, nextSeq)) { s =>
        sender() ! s
        send(text)
      }
    case AwaitConfirmation(seq) =>
      outstanding get seq match {
        case Some(set) => outstanding += seq -> (set + sender())
        case None      => sender() ! Confirmed(seq)
      }
    case rc @ Receiver.Confirmed(seq, id) =>
      log(rc.toString)
      persist(InternalConfirmation(seq, id)) { _ =>
        confirm(seq, id)
      }
  }

  def send(text: String): Unit = {
    deliver(target.path, Receiver.Important(text, nextSeq, _))
    outstanding += nextSeq -> Set.empty
    nextSeq += 1
  }

  def confirm(seq: Int, id: Long): Unit = {
    confirmDelivery(id)
    outstanding get seq match {
      case Some(set) =>
        val c = Confirmed(seq)
        set foreach (_ ! c)
        outstanding -= seq
      case None =>
    }
  }

  def receiveRecover = {
    case Sent(text, seq) =>
      send(text)
      assert(nextSeq == seq + 1)
    case InternalConfirmation(seq, id) =>
      confirm(seq, id)
      
  }
}