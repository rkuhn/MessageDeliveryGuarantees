package com.rolandkuhn.guarantees

import akka.persistence.PersistentActor
import java.time.LocalDateTime

object Receiver {
  case class Important(text: String, seq: Int, id: Long)
  case class Confirmed(seq: Int, id: Long)
}

class Receiver extends PersistentActor {
  import Receiver._
  
  def log(msg: String) = println(s"[${LocalDateTime.now}]                                     recv: $msg")

  override def persistenceId = "receiver"

  var nextSeq = 0L

  def receiveCommand = {
    case msg @ Important(text, seq, id) =>
      log(s"received $msg")
      if (seq < nextSeq) sender() ! Confirmed(seq, id)
      else if (seq == nextSeq) {
        persist(Confirmed(seq, id)) { c =>
          sender() ! c
          nextSeq = seq + 1
        }
      }
  }

  def receiveRecover = {
    case Confirmed(seq, id) => nextSeq = seq + 1
  }

}