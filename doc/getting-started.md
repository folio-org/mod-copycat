# Building, running, initializing and using mod-copycat

Mike Taylor, Index Data ApS.
mike@indexdata.com

<!-- md2toc -l 2 getting-started.md -->
* [Prerequisites](#prerequisites)
* [To build](#to-build)
* [To run](#to-run)
* [To initialize](#to-initialize)
* [To use](#to-use)

## Prerequisites

You need
[yaz4j](https://github.com/indexdata/yaz4j)
and
[YAZ](https://www.indexdata.com/yaz)
before you can compile and install mod-copycat.

Installing YAZ is easy on Ubuntu:

	term1$ sudo apt install libyaz5

yaz4j is distributed in https://maven.indexdata.com APT repo.
If you are using Ubuntu, it is not necessary to compile and install yaz4j.

Steps for install yaz4j (not necessary on Ubuntu amd64).
In the directory next to mod-copycat checkout, install as follows:

	term1$ sudo apt install yaz libyaz-dev swig
	term1$ git clone https://github.com/indexdata/yaz4j.git
	term1$ cd yaz4j
	term1$ git checkout v1.6.0
	term1$ mvn -B -Pbundle install

This installs `yaz4j-1.6.0.jar` into the local Maven repository.

Because `bundle` profile was specified, the jar file includes a shared object.
If running on same platform, that's fine.
If not, you'll have to ship a shared object separately and install it in the path.
The shared object is installed in `target/native` in all cases (whether bundle is specified or not).

## To build

Once the yaz4j library is available, mod-copycat itself can be built:

	term1$ cd mod-copycat
	term1$ mvn install

## To run

You must have PostgreSQL running. Set environment appropriately:

	$ export DB_HOST=localhost
	$ export DB_PORT=5432
	$ export DB_USERNAME=username
	$ export DB_PASSWORD=password
	$ export DB_DATABASE=database

With these set, you can run:

	$ java -jar target/mod-copycat-fat.jar

## To initialize

In another terminal:

	$ curl -d'{"module_to":"foo","parameters":[{"key":"loadReference","value":"true"}]}' \
		-HX-Okapi-Tenant:diku -HContent-Type:application/json \
		-HX-Okapi-Url:http://localhost:8081 http://localhost:8081/_/tenant

This will initialize mod-copycat for tenant `diku` and load reference-data.

## To use

List all profiles:

    $ curl -HX-Okapi-Tenant:diku http://localhost:8081/copycat/profiles
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

We have two profiles as part of reference data, one for OCLC Worldcat and
another for Library of Congress LCDB.

OCLC Worldcat requires credentials to be given before it can be used.
Property `authentication` must be set for this profile before it can be used.

In this example, we will import a record from Library of Congress.

Use your favorite HTTP tool. Here okapi-curl is used:

    $ git clone https://github.com/MikeTaylor/okapi-curl

Set up tenant and URL for the remote backend by editing `.okapi`:

    $ vi ~/.okapi

Example values:

    $ cat ~/.okapi
    OKAPI_URL=https://folio-snapshot-okapi.dev.folio.org
    OKAPI_TENANT=diku

Login:

    $ okapi-curl login

Login will store token also in `~/.okapi`.

Obtain User ID for the user (that you used in login):

    $ okapi-curl /users?query=username%3D%3Duser | jq .users[0].id

Finally, we're ready to make an import. Replace userid - no quotes. We
import LC number `2004436018 `- title `Ole Lukøie`.

	$ okapi-curl /copycat/imports -HX-Okapi-User-Id:userid \
        -d'{"externalIdentifier":"2004436018","profileId":"8594713d-4525-4cc7-b138-a07db4692c37"}'
    profileId":"8594713d-4525-4cc7-b138-a07db4692c37"}'
    {
      "externalIdentifier" : "2004436018",
      "internalIdentifier" : "fa6856d2-83cb-41fb-a5fa-0a32505c2cb4",
      "profileId" : "8594713d-4525-4cc7-b138-a07db4692c37"
    }

This reports that the the LCDB record with LCCN 2004436018 has been created
as a FOLIO instance with UUID fa6856d2-83cb-41fb-a5fa-0a32505c2cb4."
