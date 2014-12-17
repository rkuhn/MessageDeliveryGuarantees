package com.rolandkuhn.slownetwork

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
import com.rolandkuhn.guarantees.Sender
import akka.actor.Actor
import akka.actor.ActorRef
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberEvent
import akka.actor.ActorLogging
import akka.cluster.ClusterEvent.ClusterDomainEvent
import akka.cluster.ClusterEvent.ClusterDomainEvent
import akka.cluster.ClusterEvent.MemberUp
import akka.util.ByteString

object Main extends App {

  def system(addr: Address) =
    ActorSystem(addr.system, ConfigFactory
      .parseString(s"""
        akka.actor.provider = akka.cluster.ClusterActorRefProvider
        akka.loglevel = DEBUG
        akka.remote.netty.tcp {
          hostname="${addr.host.get}"
          port=${addr.port.get}
          applied-adapters = [trttl, gremlin]
        }""")
      .withFallback(ConfigFactory.load()))

  val addressA = Address("akka.trttl.gremlin.tcp", "A", "127.0.0.1", 6661)
  val addressB = Address("akka.trttl.gremlin.tcp", "A", "127.0.0.1", 6662)

  val sysA = system(addressA)
  val sysB = system(addressB)

  try {
    implicit val timeout = Timeout(100.seconds)
    import sysA.dispatcher

    val receiver = sysB.actorOf(Props(new Receiver), "receiver")

    Cluster(sysA).join(addressA)
    Cluster(sysB).join(addressA)
    println(Await.result(receiver ? AwaitStartup, 15.seconds))

    val recvPath = RootActorPath(addressB) / "user" / "receiver"
    val recv =
      Await.result(sysA.actorSelection(recvPath) ? Identify(42) collect {
        case ActorIdentity(42, Some(ref)) => ref
      }, 1.second)

    {
      import akka.remote.transport.ThrottlerTransportAdapter._
      Await.result(TestConductor(sysA).transport.managementCommand(
        SetThrottle(
          addressB,
          Direction.Both,
          TokenBucket(
            capacity = 200000,
            tokensPerSecond = 125000.0, // 1MBit/s
            nanoTimeOfLastSend = 0,
            availableTokens = 0))), 15.seconds)
    }

    val sender = sysA.actorOf(Props(new Sender(recv)), "sender")
    println(Await.result(sender ? Send(1000), 1.minute))

  } finally {
    sysA.shutdown()
    sysB.shutdown()
  }
}

case object AwaitStartup
case object Started
case class Waiting(x: Int)
case object Done

class Receiver extends Actor with ActorLogging {

  log.error("receiver started up")

  Cluster(context.system).subscribe(self, classOf[ClusterDomainEvent])

  var inquiry: Option[ActorRef] = None
  var members = 0
  var received = 0
  var waiting: Option[(Int, ActorRef)] = None

  def receive = {
    case msg: ClusterDomainEvent =>
      log.error("cluster event: {}", msg)
      if (msg.isInstanceOf[MemberUp]) {
        members += 1
        if (members > 1 && inquiry.isDefined) inquiry.get ! Started
      }
    case AwaitStartup =>
      if (members > 1) sender() ! Started
      else inquiry = Some(sender())
    case Msg(_) =>
      received += 1
      log.info("received number {}", received)
      waiting match {
        case None                                   =>
        case Some((thr, client)) if received >= thr => client ! Done
      }
    case Waiting(x) =>
      if (x <= received) sender() ! Done
      else waiting = Some((x, sender()))
  }
}

case class Msg(arr: Array[Byte])
case class Send(x: Int)

class Sender(target: ActorRef) extends Actor with ActorLogging {
  val msg = Msg(new Array[Byte](100000))

  def receive = {
    case Send(x) if x > 0 =>
      target ! msg
      self ! Send(x - 1)
    case Send(_) =>
      log.error("done sending")

  }
}







