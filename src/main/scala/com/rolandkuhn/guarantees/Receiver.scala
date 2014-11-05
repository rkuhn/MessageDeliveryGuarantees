package com.rolandkuhn.guarantees

import akka.persistence.PersistentActor
import java.time.LocalDateTime
import scala.util.control.NoStackTrace
import scala.util.Random

object Receiver {
  case class Important(text: String, seq: Int, id: Long)
  case class Confirmed(seq: Int, id: Long)
  case class Appended(text: String)
  case object GetText
  case class Text(text: String)
}

class Receiver extends PersistentActor {
  import Receiver._

  def log(msg: String) = println(s"[${LocalDateTime.now}]                                     recv: $msg")

  val rnd = new Random
  def fail() = if (rnd.nextDouble() < 0.2) throw new Exception("KABOOM!") with NoStackTrace

  override def persistenceId = "receiver"

  var nextSeq = 0L
  var words = List.empty[String]

  def receiveCommand = {
    case msg @ Important(text, seq, id) =>
      log(s"received $msg")
      if (seq < nextSeq) sender() ! Confirmed(seq, id)
      else if (seq == nextSeq) {
        persist(Appended(text)) { a =>
          words ::= text
        }
        persist(Confirmed(seq, id)) { c =>
          sender() ! c
          nextSeq = seq + 1
        }
      }
    case GetText => sender() ! Text(words.reverse.mkString(" "))
  }

  def receiveRecover = {
    case c @ Confirmed(seq, id) =>
      log(s"replay $c")
      nextSeq = seq + 1
    case a @ Appended(text) =>
      log(s"replay $a")
      words ::= text
  }

}