This assumes a locally running mongo db

Typically start a mongo in docker, or install locally.

to create the test user see the [Mongo Docs][https://docs.mongodb.com/manual/reference/method/db.createUser/#db.createUser]


If you're running mongo in a [container][http://blog.arungupta.me/attach-shell-to-docker-container/], you could e.g.:


## Find your container
```
docker ps
```

## Connect a shell to it, e.g.

```
 docker exec -it d68a1694303b bash
```

## Create a user within that shell:

```
db.createUser(
   {
     user: "test_user",
     pwd: "password",
     roles: [ "readWrite" ]
   }
)
```

# Set up a replica set
follow [this][https://www.sohamkamani.com/blog/2016/06/30/docker-mongo-replica-set/] to find:

{
```
docker run \
-p 30001:27017 \
--name mongo1 \
--net my-mongo-cluster \
mongo mongod --replSet my-mongo-set



docker run \
-p 30002:27017 \
--name mongo2 \
--net my-mongo-cluster \
mongo mongod --replSet my-mongo-set



docker run \
-p 30003:27017 \
--name mongo3 \
--net my-mongo-cluster \
mongo mongod --replSet my-mongo-set
```

Then create the config:

```
docker exec -it mongo1 mongo

> db = (new Mongo('localhost:27017')).getDB('test')
test
> config = { "_id" : "my-mongo-set", "members" : [ { "_id" : 0, "host" : "mongo1:27017" }, { "_id" : 1, "host" : "mongo2:27017" }, { "_id" : 2, "host" : "mongo3:27017" } ] }
> rs.initiate(config)
```