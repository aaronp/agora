# A reactive restful exchange


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
      +++         Dude, good news! Here's              +<--------- take {key:X, n:1} ----------+
       | <-----+ somebody who says they'll-------------+                                       |
       |              take your job!                   |                                       |
       |                                               |                                       |
                                                       +                                       |
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


![sbt test cucumber](https://travis-ci.org/aaronp/jabroni.svg?branch=release)


