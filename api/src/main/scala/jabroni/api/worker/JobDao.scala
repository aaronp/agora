package jabroni.api.worker

import jabroni.domain.{Create, Read}


trait JobDao[Id, Job, Output, Error] extends
  Create[Id, (Job, RunDetails[Output, Error])] with
  Read[Id, (Job, RunDetails[Output, Error])]