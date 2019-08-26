package StoresAndSerialization

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import com.typesafe.config.ConfigFactory

object LocalStores extends App {
  val localStoreActorSystem = ActorSystem("localStoresSystem", ConfigFactory.load().getConfig("localStores"))
  val simplePersistentActor = localStoreActorSystem.actorOf(Props[SimplePersistentActor], "simplePersistentActors")

  for(i <- 1 to 10) {
    simplePersistentActor ! s"I love akka: $i"
  }

  simplePersistentActor ! "print"
  simplePersistentActor ! "snap"

  for(i <- 11 to 20){
    simplePersistentActor ! s"I love akka: $i"
  }
}
