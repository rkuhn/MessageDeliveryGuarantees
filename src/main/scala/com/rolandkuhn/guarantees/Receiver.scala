package com.rolandkuhn.guarantees

import akka.persistence.PersistentActor
import java.time.LocalDateTime
import scala.util.control.NoStackTrace
import scala.util.Random
import akka.actor.Actor
import akka.actor.Props

object Receiver {
  case class Important(text: String, seq: Int, id: Long)
  case class Confirmed(seq: Int, id: Long)
  case class Appended(text: String)
  case object GetText
  case class Text(text: String)
}

class Receiver extends PersistentActor {
  import Receiver._
  
  val logger = context.actorOf(Props(new Logger), "logger")

  def log(msg: String) = println(s"[${LocalDateTime.now}]                                     recv: $msg")

  val rnd = new Random
  def fail() = if (rnd.nextDouble() < 0.2) throw new Exception("KABOOM!") with NoStackTrace

  override def persistenceId = "receiver"

  var nextSeq = 0L
  var words = List.empty[String]

  def receiveCommand = {
    case msg @ Important(text, seq, id) =>
      log(s"received $msg")
      sender() ! Confirmed(seq, id)
      words ::= text
      logger ! s"logging '$text'"
    case GetText => sender() ! Text(words.reverse.mkString(" "))
  }

  def receiveRecover = {
    case _ =>
  }

}

class Logger extends Actor {
  def receive = {
    case msg => println(msg)
  }
}
