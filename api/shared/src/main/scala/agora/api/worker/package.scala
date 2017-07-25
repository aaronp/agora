package agora.api

import agora.api.exchange.WorkSubscription

package object worker {
  type SubscriptionKey = agora.api.SubscriptionKey
  type MatchId         = agora.api.MatchId

  // a work subscription match candidate for a given job
  type Candidate = (SubscriptionKey, WorkSubscription, Int)

  // represents the input to the worker selection criteria. Given a job and potential matches, return a Seq[Candidate]
  type CandidateSelection = Seq[Candidate]

}
