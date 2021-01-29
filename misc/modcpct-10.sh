#!/bin/sh
H=http://localhost:9130
tenant=diku
# the instance that we'd like to create and update
instance=059441ed-0e9c-474d-94bd-980a68e3255d
curl -o /dev/null -s -Dout -HContent-Type:application/json -HX-Okapi-Tenant:$tenant -d'{"username":"diku_admin", "password": "admin"}' $H/authn/login 
T=`cat out|awk '/x-okapi-token/ {print $2}'`
echo $T
case $1 in
  "update")
	curl -o out -s -HX-Okapi-Token:$T "-HAccept:*/*" -HContent-Type:application/json -d'{"name":"indexdata", "url":"z3950.indexdata.com/marc", "externalIdQueryMap": "@attr 1=1016 $identifier", "internalIdEmbedPath":"999ff$i"}' $H/copycat/profiles
	id=`cat out |jq '.id'`
	curl -o out -s -HX-Okapi-Token:$T "-HAccept:*/*" -HContent-Type:application/json -d"{\"profileId\":$id, \"externalIdentifier\":\"bible\", \"internalIdentifier\":\"$instance\"}" $H/copycat/imports
	;;
  "create")
	curl -o out -s -HX-Okapi-Token:$T "-HAccept:*/*" -HContent-Type:application/json -d'{"name":"indexdata", "url":"z3950.indexdata.com/marc", "externalIdQueryMap": "@attr 1=1016 $identifier", "internalIdEmbedPath":"999ff$i"}' $H/copycat/profiles
	id=`cat out |jq '.id'`
	curl -o out -s -HX-Okapi-Token:$T "-HAccept:*/*" -HContent-Type:application/json -d"{\"profileId\":$id, \"externalIdentifier\":\"collins\"}" $H/copycat/imports
	;;
  "query")
	curl -s -HX-Okapi-Token:$T "-HAccept:*/*" -HContent-Type:application/json "$H/instance-storage/instances/$2"
	;;
  "cql")
	curl -s -HX-Okapi-Token:$T "-HAccept:*/*" -HContent-Type:application/json "$H/instance-storage/instances?query=$2"
	;;
  "jobProfiles")
	curl -s -HX-Okapi-Token:$T "-HAccept:*/*" -HContent-Type:application/json "$H/data-import-profiles/jobProfiles"
	;;
esac
