package io.hydrosphere.mist.worker

import java.util.concurrent.Executors._

import akka.actor.Actor
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.pattern.ask
import io.hydrosphere.mist.Messages._
import io.hydrosphere.mist.{Constants, MistConfig}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Random, Success}

class CLINode extends Actor {

  val executionContext = ExecutionContext.fromExecutorService(newFixedThreadPool(MistConfig.Settings.threadNumber))

  private val cluster = Cluster(context.system)

  private val serverAddress = Random.shuffle[String, List](MistConfig.Akka.Worker.serverList).head + "/user/" + Constants.Actors.workerManagerName
  private val serverActor = cluster.system.actorSelection(serverAddress)
  private val messageArray = ArrayBuffer.empty[String]

  val nodeAddress = cluster.selfAddress

  override def preStart(): Unit = {
    cluster.subscribe(self, InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  def cliResponder[A](el: A, originalSender: akka.actor.ActorRef): Unit = {
    val future = serverActor.ask(el)(timeout = Constants.CLI.timeoutDuration)
    future.andThen {
      case Success(result: Map[String, Any]) => originalSender ! result
      case Success(result: String) => originalSender ! result
      case Success(result: List[Any]) => originalSender ! result
      case Failure(error: Throwable) => originalSender ! error
    }(ExecutionContext.global)
  }

  override def receive: Receive = {
    case StopWorker(message) => {
      serverActor ! new RemoveContext(message.substring(Constants.CLI.stopWorkerMsg.length).trim)
      sender ! s"Worker ${message.substring(Constants.CLI.stopWorkerMsg.length).trim} is scheduled for shutdown."
    }

    case StopJob(message) => {
      cliResponder(new StopJob(message), sender)
    }

    case StopAllContexts =>
      serverActor ! StopAllContexts
      sender ! Constants.CLI.stopAllWorkers

    case ListJobs =>
      cliResponder(ListJobs, sender)

    case ListWorkers =>
      cliResponder(ListWorkers, sender)

    case ListRouters =>
      cliResponder(ListRouters, sender)

    case MemberExited(member) =>
      if (member.address == cluster.selfAddress) {
        cluster.system.shutdown()
      }

    case MemberRemoved(member, prevStatus) =>
      if (member.address == cluster.selfAddress) {
        cluster.down(nodeAddress)
        sys.exit(0)
      }
  }
}
