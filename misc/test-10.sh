#!/bin/sh
set -x
H=http://localhost:9130
tenant=diku
# the instance that we'd like to create and update
instance=066340fb-6873-4e6a-8403-af79ce2717fb
curl -o /dev/null -s -Dout -HContent-Type:application/json -HX-Okapi-Tenant:$tenant -d'{"username":"diku_admin", "password": "admin"}' $H/authn/login 
T=`cat out|awk '/x-okapi-token/ {print $2}'`
echo $T
# User-Id
uid=82a6a3e4-8bea-5ec2-8898-748955bfef26

# instance Id
iid=30fcc8e7-a019-43f4-b642-2edc389f4501

# default profile
id1=c8f98545-898c-4f48-a494-3ab6736a3243

# 91f9 is the update profile
id2=91f9b8d6-d80e-4727-9783-73fb53e3c786
# d0eb is the create profile
#id2=d0ebb7b0-2f0f-11eb-adc1-0242ac120002

# create job execution
req="{\"jobProfileInfo\":{\"id\":\"$id1\",\"name\":\"Default job profile\",\"dataType\":\"MARC\"},\"userId\":\"$uid\",\"sourceType\":\"ONLINE\"}"
curl -o out -s -HX-Okapi-Token:$T "-HAccept:*/*" -HContent-Type:application/json -d"$req" $H/change-manager/jobExecutions
pid=`cat out |jq '.parentJobExecutionId'|sed s/\"//g`

# update jobprofile
req="{\"id\":\"$id2\",\"name\":\"Default Update Instance\",\"dataType\":\"MARC\"}"
curl -o out1 -s -HX-Okapi-Token:$T "-HAccept:*/*" -HContent-Type:application/json -XPUT -d"$req" $H/change-manager/jobExecutions/$pid/jobProfile

# post 1 record
marc=`cat marc1a.json|sed 's/"/\\\"/g'|sed s/INSTANCEID/$iid/g`
req="{\"recordsMetadata\":{\"last\":false,\"counter\":1,\"total\":1,\"contentType\":\"MARC_JSON\"},\"initialRecords\":[{\"record\":\"$marc\"}]}"
echo $req >req
curl -o out2 -s -HX-Okapi-Token:$T "-HAccept:*/*" -HContent-Type:application/json  -d"$req" $H/change-manager/jobExecutions/$pid/records

# finish it
req="{\"recordsMetadata\":{\"last\":true,\"counter\":1,\"total\":1,\"contentType\":\"MARC_JSON\"},\"initialRecords\":[]}"
curl -o out3 -s -HX-Okapi-Token:$T "-HAccept:*/*" -HContent-Type:application/json  -d"$req" $H/change-manager/jobExecutions/$pid/records

# delete it
curl -o out4 -s -HX-Okapi-Token:$T "-HAccept:*/*" -XDELETE  -d"$req" $H/change-manager/jobExecutions/$pid/records

curl -s -HX-Okapi-Token:$T "-HAccept:*/*" -HContent-Type:application/json "$H/instance-storage/instances/$iid"
