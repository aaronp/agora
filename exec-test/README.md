The Exec-Test project servers as a fully containerised example of
running cucumber integration tests from within the container itself
(as opposed to simply connecting to the exposed ports on the host).

Having a separate test-project serves a number of purposes:

 * It serves as an example of what a 'real' setup might look like
 * It tests actual configurations against host/ports 
   (not everything is 'localhost' - and we can bind successfully e.g.
   using 0.0.0.0 instead of the literal localhost)
 * It exercises our configuration in a multi-host environment, in particular
   the exchange where the subscriptions come from different hosts

== testing ==
This project makes use of the docker-compose plugin ... except in this case,
Just running up the docker-compose.yml is the test itself, as the tests run
within the container.