package akka.remote

package object rkextras {
  import akka.remote.transport.FailureInjectorTransportAdapter._
  
  def DropIt(addr: akka.actor.Address, probability: Double) =
    One(addr, Drop(probability, probability))
}