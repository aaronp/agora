docker run \
-p 30003:27017 \
--name mongo3 \
--net my-mongo-cluster \
mongo mongod --replSet my-mongo-set