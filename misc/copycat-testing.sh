#!/bin/sh
H=http://localhost:9130
tenant=diku
curl -o /dev/null -s -Dout -HContent-Type:application/json -HX-Okapi-Tenant:$tenant -d'{"username":"diku_admin", "password": "admin"}' $H/authn/login 
T=`cat out|awk '/x-okapi-token/ {print $2}'`

# copycat profiles - part of mod-copycat reference-data
# pid=f26df83c-aa25-40b6-876e-96852c3d4fd4 # OCLC WorldCat
pid=8594713d-4525-4cc7-b138-a07db4692c37 # Library of Congress

# Z39.50 remote records
sid=2004436018 # Ole Lukøie at LoC
sid=93238366 # Steuerrecht für Handwerksbetriebe at LoC

# the instance that we'd like to create and update
iid=69640328-788e-43fc-9c3c-af39e243f3b7  # ABA Journal from mod-inventory-storage sample-data

case $1 in
  "update")
	curl -D err -o out -HX-Okapi-Token:$T -HContent-Type:application/json -d"{\"profileId\":\"$pid\",\"externalIdentifier\":\"$sid\",\"internalIdentifier\":\"$iid\"}" $H/copycat/imports
	;;
  "create")
	curl -D err -o out -HX-Okapi-Token:$T -HContent-Type:application/json -d"{\"profileId\":\"$pid\",\"externalIdentifier\":\"$sid\"}" $H/copycat/imports
	;;
  "query")
	if test -z "$2"; then
		curl -s -HX-Okapi-Token:$T "-HAccept:*/*" -HContent-Type:application/json "$H/instance-storage/instances"
	else
		curl -s -HX-Okapi-Token:$T "-HAccept:*/*" -HContent-Type:application/json "$H/instance-storage/instances?query=$2"
	fi
	;;
  "instance")
	curl -s -HX-Okapi-Token:$T "-HAccept:*/*" -HContent-Type:application/json "$H/instance-storage/instances/$iid"
	;;
  "jobProfiles")
	curl -s -HX-Okapi-Token:$T "-HAccept:*/*" -HContent-Type:application/json "$H/data-import-profiles/jobProfiles"
	;;
esac
