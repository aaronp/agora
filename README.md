# A reactive exchange for producers/consumers

This project provides a means to distribute work items across workers using a work-pulling mechanism.

```scala
libraryDependencies += "com.github.aaronp" %% "jabroni-rest" % "0.0.1"
```

# Erm ... what do you mean?

It's an implementation of [Reactive Streams](http://www.reactive-streams.org/), except the publishers also supply data in their subscriptions.


Another way to think of it is as a lightweight pub/sub work model. 

Ultimately it works as a load balancer where both producers and consumers provide some criteria on the work they want to exchange. 

The beauty is that you don't have to share out-of-date info about the current load of a system. Workers just ask for work when they finish work, meaning that work automatically flows as fast as the workers can handle it.

Additionally, as you'll see below, both the clients (producers) and consumers (workers) can specify criteria to decide which messages get sent to whom. That way a single exchange can manage work of any type/shape, or multiple versions of messages, or simply as a 'topic' for a message queue.

# Can I see some examples?

No. Ha ... ok, fine. I mean, you're reading this as it's hosted on github. Just look at the chuffing code. 

J/k. Sort of.

## Worker Code Example

Let's spin up a REST service which handles 'add' requests. We can do this just as easily programmatically, and can get at
the individual mechanisms involved in a less magical way, but for now this example shows how this would work driven by the configuration.

```scala
object WorkerExample {

  import agora.rest.worker.WorkerConfig

  // an example request object
  case class Add(x: Int, y: Int)

  def main(a: Array[String]) = {

    // get a configuration from somewhere. The config is based on typesafe config w/ overrides from the user args
    val config = WorkerConfig(a)

    // use the convenience method 'startWorker' on the config to start an akka http service.
    // you could alternatively just get use the akka Route based on the config in your own existing REST service.
    val runningServerFuture  = config.startWorker()

    runningServerFuture.map { worker =>

      // ATM, the worker provides some basic routes (e.g. health, UI, ...), but it doesn't actually DO anything!
      // let's fix that by providing a handler which will pull 'Add' requests from an exchange:
      worker.service.addHandler[Add] { ctxt =>
        val add: Add = ctxt.request

        // supply the answer. By default this will ask for one more work item from the exchange
        ctxt.complete(add.x + add.y)
      }
    }
  }
}
```

Obviously this skims over a lot of machinery. The 'worker' above is going to ask for N work items (based on the configuration) from an 'exchange'. 

By default, workers can act as an 'exchange' themselves (so we've not had to explicitly start one up here -- although we could). 

So, If we configure our worker to only handle one request at a time, then it will ask for 1 'Add' request. Once it completes that request (by calling 'complete' on the context argument to the handler), it sends the reply to the client and asks for one more work item. 

It's worth noting too that the 'addHandler' calls can be done from the initial configuration or even on a running server as show above. We can even add/remove work handlers from within handlers, allowing for more complicated workflows (such as dependent jobs, sessions, state machines, etc). More on that later...


## Client side code example

For completeness, this is how you would send work to the above worker. Obviously it assumes the configurations (e.g. host/port) are pointing at the same place.

```scala
object ClientExample {

  import agora.rest.exchange.{ExchangeClient, ExchangeConfig}
  import agora.api.Implicits._
  import io.circe.generic.auto._
  import scala.concurrent.Await
  import scala.concurrent.duration._

  case class Add(x: Int, y: Int)

  def main(a: Array[String]) = {

    // create a configuration
    val config = ExchangeConfig(a)

    // use the convenience method to get a client to the exchange
    val exchangeClient: ExchangeClient = config.client

    // enqueue some work to be picked up. the 'asJob' is brought
    // in from the api.Implicits, as we're enqueue a 'SubmitJob'
    val responseFuture = exchangeClient.enqueue(Add(1, 2).asJob)

    // unmarshal the result
    val sumFuture = for {
      workerResponses <- responseFuture
      sum <- workerResponses.singleReply[Int]
    } yield {
      sum
    }

     val yourSum = Await.result(sumFuture, 10.seconds)
  }
}

```

# How do we control which jobs go to which workers?

## Matching work with workers

One of the elements glazed over above is the matching. Basically, asside from the work items themselves, both clients (work producers) and consumers expose some additional (generic) details to the exchange in the form of a json blob. They also both provide some matching criteria which for json blobs. 

This way clients (work producers) can decide what sort of workers can handle the work they're submitting, and workers can decide which work they can handle.


In the simple case, both can 'match all' and all work could get picked up by anybody. 
But both clients and workers can control what additional data is exposed, and what data they want to match:

### Clients Specifying Criteria for Workers

An example of a client specifying which worker could handle its request might be:
```scala
    val criteria = ("topic" === "foo") and ("cpus" gte 3)
    val job: SubmitJob = Add(1, 2).asJob.matching(criteria)
```


### Workers Specifying Criteria for Submitted Work

When jobs are submitted by clients (work producers), there's the work itelf and potentially some additional metadata 
about the work. 

This means that workers have two potential options for 'matching' a work item... they can specify both job criterial and submission criteria:

```scala
      case class AdditionDataClientsCanUse(iHaveThidManyCPUs : Int, runningOS : String)

      // criteria for the actual submit request -- e.g. only handle positive integers
      val jobCriteria = ("x" gte 0) and ("y" gte 0)

      // criteria for the additional data
      val criteriaForAdditionalSubmissionDetails = ("submitUser" === "bob")

      // create a work subscription with the above criteria
      val workSubscription: SubscriptionRequest = config.subscription.
        matchingJob(jobCriteria).
        matchingSubmission(criteriaForAdditionalSubmissionDetails)
      
      // and add our handler...
      worker.service.usingSubscription(workSubscription2).addHandler[Add] { ctxt =>
        ctxt.complete(ctxt.request.x + ctxt.request.y)
      }
```
        

### Clients Providing Additional Details for Workers to Match

The client can also choose to supply additional details about the request for workers to potentially match against.

They could use custom data structures (e.g. SomeClientData)

```scala
    case class SomeClientData(submittedBy : String, useVersion : String, retainLogsOnComplete : Boolean)

    val extraData = SomeClientData(Properties.userName, "super-software-1.2.3", true)
    
    val job: SubmitJob = Add(1, 2).asJob.withData(extraData)
```

or just key/value pairs using '+' or 'add'

```scala
 val job: SubmitJob = Add(1, 2).asJob + ("topic" -> "foo")
```


### Workers Specifying Additional Data for Client to Match On

This is much the same as clients, but instead of 'SubmitJob' requests, workers have work subscriptions.

```scala
      case class AdditionDataClientsCanUse(iHaveThidManyCPUs : Int, runningOS : String)
      
      val workSubscription = config.subscription.withData(AdditionDataClientsCanUse(2, "centos"))

      worker.service.usingSubscription(workSubscription).addHandler[Add] { ctxt =>
        ctxt.complete(ctxt.request.x + ctxt.request.y)
      }
```

### Worker Selection

In addition to matching criteria, work requests also provide separate selection criteria to tell it what to do with the match. This covers the scenario where multiple workers may be available to handle a job. 

A client could decide to send work out to all available workers, just one, or even choose one between the eligible workers based on some arbitrary json criteria:

```scala
val job: SubmitJob = Add(1, 2).asJob.withSelection(SelectIntMax("machine.cpus"))
```



# An Example Workflow


      Your Client Here                             Exchange                           Your Worker(s) Here

                      Submit Job
              { submissionDetails :{}, job: {} }       +                                       +
       +---------------------------------------------->+                                       |
       |                                               |                                       |
      +++                                              |           Subscribe                   |
      | |                                              |   { details :{}, jobMatcher: {} }     |
      | |                                              +<--------------------------------------+
      | |                                              |                                       |
      | |                                              +--------- subscription key 'X' ------->+
      | |                                              |                                       |
      | |                                              |                                       |
      | |                                              |                                       |
      +++         Dude, good news! Here's              +<--------- take {key:X, n:1} ----------+   // at this point the 1 work 
       | <-----+ somebody who says they'll-------------+                                       |   // item is decremented
       |              take your job!                   |                                       |
       |                                               |                                       |
       |                                               +                                       |
       |               I have it on good authority that if I send you request X                |
       |               (1) you can handle it ('cause you asked for it)                         |
       +-------------- (2) it won't overload you                                    ---------> |
      +-+              (3) if you mess it up I'll just give it back to the exchange            +---+
      | |                                              +                                       |   |
      | |                                              |                                       |   | Does work...
      | |                                              |                                       |   |
      +++                                              |                                       <---+
       | <------------------------------- Whatever Response To Job-----------------------------+
       |                                               |                                       |
       |                                               | <--------- take {key:X, n:1} ---------+
       |                                               +
       |
       +

# Some other stuff

Sorry - just thought I'd dump some other areas I meant to cover. This project is still in very heavy development!

I wanted to talk about:

## Project structure
The 'api' subproject is very dependency light, only holding the basic data structures.

The 'rest' is very opinionated. I didn't want to write it for any arbitrary web service or json library. I chose circe and akka http. How very controversial.

The 'exec' is an example project which makes use of the 'rest' on. It fixes the exhange type 'T' for a 'RunProcess' request... a request which can execute a command on a worker. This may or may not be a good idea, but seems typical for what people want to do (e.g. let's write Jenkins using agora!)

## The name
I'm still bad at names. I chose 'agora' because I was thinking 'marketplace'. All this is based around the concept of an exchange, and the things exchanged doesn't really matter (except in the 'rest' project case it just cares that we can view it as json and thus use some jpath criteria as a matcher). According to the 'api' though, it could match two things of any type.

## Scaling
Ah, so ... there's a miniraft package which will get refactored out at some point. But yeah, the protocol is agnostic, but the 'rest' does provide a raft implementation to be able to choose one 'leader' exchange amoungst a bunch of them. 

Also, the exchange queue itself doesn't even have to be (and probably shouldn't be!) persistent. The data is held in the participants of the system, which I think is a neat side-effect. We don't have to write down who's asking for what. In the failure cases (clients crashing/going away, workers crashing/going away), it all just works as expected...

If a client dies, well, we could detect that and offer a cleanup if necessary, but in the default case, the worst thing that happens is that some work gets done that doesn't need to.

If a worker dies, the exchange noticies and cancels its subscription, so no more work gets sent to that worker. And any work in-progress (in the rest implementation) was done via a redirect, so the client notices that its worker is gone and can just resubmit.

## Implementation - how we handle not resending large data

Just so I don't forgert to write this down -- in the rest implementation, the client request to the exchange would get a redirect in the case where the worker is not also the exchange itself. It then makes a subsequent request to those worker(s).

In the case where the work item is large (e.g. we don't want to sent multipart uploads to the exchange), the client has full control over that. The machinery is exposed to 'enque and dispatch' or even just submit async (e.g. not wait for a worker match).


