docker run \
-p 30002:27017 \
--name mongo2 \
--net my-mongo-cluster \
mongo mongod --replSet my-mongo-set