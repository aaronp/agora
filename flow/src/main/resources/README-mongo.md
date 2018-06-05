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