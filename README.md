MessageDeliveryGuarantees
=========================

This repository hosts several different branches showing different implementations of a sender and a receiver that communicate over an artificially unreliable network connection.

In particular:

* the `starting-point` branch just sets up the minimal environment for starting the experimentation
* the `at-most-once` branch shows the inherent messaging semantics of Akka (not minimal in its implementation but instead minimizing the diff to the following two branches for easy comparison)
* the `at-least-once` branch uses the AtLeastOnceDelivery support trait and allows you to try different failure modes to observe duplication or out of order reception of messages
* the `exactly-once` branch then shows the equivalent of what is commonly called “guaranteed delivery”, but feel free to place `fail()` statements in strategic places to exhibit cases where this does not hold (just as in every other commercial implementation as well)
