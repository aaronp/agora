docker run \
-p 30001:27017 \
--name mongo1 \
--net my-mongo-cluster \
mongo mongod --replSet my-mongo-set