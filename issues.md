Top Issues:
1) add 'matchBucket' to subscription details:
   
   // the paths used to put work subscriptions into 'buckets' so as not to process all subscriptions on each job
   matchBucket : List[JPath]

   new jobs will check the values based on their 'bucket', and new subscriptions will be indexed into all applicable
   buckets

2) add 'updateSubscriptionOnMatch' -- an atomic operation which will update all workers subscription data when matched.
   This is to support several use-cases:
   1) creating session data to always route to the same worker (e.g. exec workspaces)
   2) being able to update details of a subscription (e.g. increase/decrease a quantity, as in trading exchanges)

3) Exchange Profiling --
   The exchange routes put an exchange behind a single actor. We need some benchmarking in place, and then can refactor/
   improve on the throughput/latency of the exchange itself.