package agora.api

import agora.api.exchange.Candidate

package object worker {
  type SubscriptionKey = agora.api.SubscriptionKey
  type MatchId         = agora.api.MatchId

  // represents the input to the worker selection criteria. Given a job and potential matches, return a Seq[Candidate]
  type CandidateSelection = Seq[Candidate]
}
