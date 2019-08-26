package StoresAndSerialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object Cassandra extends App {
  val cassandraActorSystem = ActorSystem("cassandraSystem", ConfigFactory.load().getConfig("cassandraDemo"))
  val simplePersistentActor = cassandraActorSystem.actorOf(Props[SimplePersistentActor], "simplePersistentActors")

  for(i <- 1 to 10) {
    simplePersistentActor ! s"I love akka: $i"
  }

  simplePersistentActor ! "print"
  simplePersistentActor ! "snap"

  for(i <- 11 to 20){
    simplePersistentActor ! s"I love akka: $i"
  }
}
