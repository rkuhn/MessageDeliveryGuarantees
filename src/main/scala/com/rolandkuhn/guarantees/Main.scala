package com.rolandkuhn.guarantees

import akka.actor.ActorSystem
import scala.concurrent.Await
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import akka.actor.Address
import akka.remote.testconductor.TestConductor
import akka.remote.rkextras._
import akka.pattern.ask
import scala.reflect.classTag
import akka.actor.RootActorPath
import akka.actor.Props
import scala.util.Failure
import scala.util.Success
import scala.concurrent.Future
import akka.util.Timeout
import akka.actor.Identify
import akka.actor.ActorIdentity

object Main extends App {

  def system(addr: Address) =
    ActorSystem(addr.system, ConfigFactory
      .parseString(s"""akka.remote.netty.tcp{hostname="${addr.host.get}"\nport=${addr.port.get}}""")
      .withFallback(ConfigFactory.load()))

  val addressA = Address("akka.gremlin.tcp", "A", "127.0.0.1", 6661)
  val addressB = Address("akka.gremlin.tcp", "B", "127.0.0.1", 6662)

  val sysA = system(addressA)
  val sysB = system(addressB)

  implicit val timeout = Timeout(10.seconds)
  import sysA.dispatcher

  val receiver = sysB.actorOf(Props(new Receiver), "receiver")

  // make sure that remoting works
  val recvPath = RootActorPath(addressB) / "user" / "receiver"
  val recv =
    Await.result(sysA.actorSelection(recvPath) ? Identify(42) collect {
      case ActorIdentity(42, Some(ref)) => ref
    }, 1.second)

  val sender = sysA.actorOf(Props(new Sender(recv)), "sender")

  Await.result(TestConductor(sysA).transport.managementCommand(DropIt(addressB, 0.2)), 5.seconds)

  val futures =
    "The quick brown fox jumps over the lazy dog"
      .split(' ').toList
      .map(word =>
        sender ? Sender.Send(word) flatMap {
          case s: Sender.Sent => sender ? Sender.AwaitConfirmation(s.confirmationId)
        } map {
          case _ => s"confirmed: $word"
        } recover {
          case ex: Throwable => s"missing                 ($word)"
        })

  Future.sequence(futures) map (_.mkString("\n  ", "\n  ", "\n")) flatMap { x =>
    println(x)
    receiver ? Receiver.GetText
  } onComplete { x =>
    x match {
      case Success(msg) => println(s"final result: $msg")
      case Failure(ex)  => println(s"retrieval failed: $ex")
    }
    sysB.shutdown()
    sysA.shutdown()
  }
}
