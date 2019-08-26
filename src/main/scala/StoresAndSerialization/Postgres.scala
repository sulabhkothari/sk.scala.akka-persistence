package StoresAndSerialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object Postgres extends App {
  val postgresActorSystem = ActorSystem("postgresSystem", ConfigFactory.load().getConfig("postgresDemo"))
  val simplePersistentActor = postgresActorSystem.actorOf(Props[SimplePersistentActor], "simplePersistentActors")

  for(i <- 1 to 10) {
    simplePersistentActor ! s"I love akka: $i"
  }

  simplePersistentActor ! "print"
  simplePersistentActor ! "snap"

  for(i <- 11 to 20){
    simplePersistentActor ! s"I love akka: $i"
  }
}
