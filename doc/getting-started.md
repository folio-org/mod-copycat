# Building, running, initializing and using mod-copycat

Mike Taylor, Index Data ApS.
mike@indexdata.com

<!-- md2toc -l 2 getting-started.md -->
* [Prerequisites](#prerequisites)
* [To build](#to-build)
* [To run](#to-run)
* [To initialize](#to-initialize)
* [To use](#to-use)
* [Integrating into a FOLIO-backend Vagrant box](#integrating-into-a-folio-backend-vagrant-box)
* [To unassociate, undeploy and remove a running module](#to-unassociate-undeploy-and-remove-a-running-module)
* [Adding permissions on the UI side](#adding-permissions-on-the-ui-side)

## Prerequisites

You need
[yaz4j](https://github.com/indexdata/yaz4j)
and
[YAZ](https://www.indexdata.com/yaz)
before you can compile and install mod-copycat.

Installing yaz is easy on Ubuntu:

	term1$ sudo apt install libyaz5

yaz4j is distribued in https://maven.indexdata.com APT repo.
If you are using Ubuntu, it is not necessary to compile and install yaz4j.

Steps for install yaz4j (not necessary on Ubuntu amd64).
In the directory next to mod-copycat checkout, install as follows:

	term1$ sudo apt install yaz libyaz-dev swig
	term1$ git clone https://github.com/indexdata/yaz4j.git
	term1$ cd yaz4j
	term1$ git checkout v1.6.0
	term1$ mvn -B -Pbundle install

This installs `yaz4j-1.6.0.jar` into the local Maven repository.

Because we used the `bundle` profile, the jar file includes a shared object.
If running on same platform as we compile, that's fine.
If not, you'll have to ship a shared object separately and install it in the path.
The shared object is installed in `target/native` in all cases (whether bundle is specified or not).

## To build

Once the yaz4j library is available, mod-copycat itself can be built:

	term1$ cd mod-copycat
	term1$ mvn install

## To run

You must have PostgresQL running. Set environment appropriately:

	term1$ export DB_HOST=localhost
	term1$ export DB_PORT=5432
	term1$ export DB_USERNAME=username
	term1$ export DB_PASSWORD=password
	term1$ export DB_DATABASE=database

With these set, you can run:

	term1$ java -jar target/mod-copycat-fat.jar

## To initialize

In another terminal:

	term2$ curl -d'{"module_to":"foo","parameters":[{"key":"loadReference","value":"true"}]}' \
		-HX-Okapi-Tenant:diku -HContent-Type:application/json \
		-HX-Okapi-Url:http://localhost:8081 http://localhost:8081/_/tenant

This will initialize mod-copycat for tenant `diku` and load reference-data.

## To use

List all profiles:

	term2$ curl -HX-Okapi-Tenant:diku http://localhost:8081/copycat/profiles
	{
	  "profiles" : [ {
	    "id" : "f26df83c-aa25-40b6-876e-96852c3d4fd4",
	    "name" : "OCLC WorldCat",
	    "url" : "zcat.oclc.org/OLUCWorldCat",
	    "externalIdQueryMap" : "@attr 1=1211 $identifier",
	    "internalIdEmbedPath" : "999ff$i",
	    "createJobProfileId" : "d0ebb7b0-2f0f-11eb-adc1-0242ac120002",
	    "updateJobProfileId" : "91f9b8d6-d80e-4727-9783-73fb53e3c786",
	    "externalIdentifierType" : "439bfbae-75bc-4f74-9fc7-b2a2d47ce3ef",
	    "enabled" : true
	  }, {
	    "id" : "8594713d-4525-4cc7-b138-a07db4692c37",
	    "name" : "Library of Congress",
	    "url" : "lx2.loc.gov:210/LCDB",
	    "externalIdQueryMap" : "@attr 1=9 $identifier",
	    "internalIdEmbedPath" : "999ff$i",
	    "createJobProfileId" : "d0ebb7b0-2f0f-11eb-adc1-0242ac120002",
	    "updateJobProfileId" : "91f9b8d6-d80e-4727-9783-73fb53e3c786",
	    "targetOptions" : {
	      "preferredRecordSyntax" : "usmarc"
	    },
	    "externalIdentifierType" : "c858e4f2-2b6b-4385-842b-60732ee14abb",
	    "enabled" : false
	  } ],
	  "totalRecords" : 2
	}

We have two profiles, one for OCLC Worldcat and one for Library of Congress LCDB.

Import a record based on OCLC number 253248524 from OCLC Worldcat. Replace okapiurl (below) with the
address of an Okapi in a Folio system with mod-copycat and mod-source-record-manager running. Also replace
token with a token obtained from that system.

	term2$ curl -d'{"externalIdentifier":"253248524", "profileId":"f26df83c-aa25-40b6-876e-96852c3d4fd4"}' \
		-HX-Okapi-Url:okapiurl -HX-Okapi-Token:token -HX-Okapi-Tenant:diku \
		-HContent-Type:application/json http://localhost:8081/copycat/imports

