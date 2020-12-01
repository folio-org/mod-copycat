# Building, running, initializing and using mod-copycat

## To build

	term1$ mvn install

## To run

	term1$ java -jar target/mod-copycat-fat.jar

## To initialize

In another terminal:

	term2$ curl -d'{"module_to":"foo"}' -HX-Okapi-Tenant:testlib "-HAccept:*/*" -HContent-Type:application/json http://localhost:8081/_/tenant
	[]
	term2$ 

While this is happening, expect to see worrying warnings on the server side:

	WARNING: An illegal reflective access operation has occurred
	WARNING: Illegal reflective access by org.postgresql.jdbc.TimestampUtils (file:/Users/mike/git/folio/other/mod-copycat/target/mod-copycat-fat.jar) to field java.util.TimeZone.defaultTimeZone
	WARNING: Please consider reporting this to the maintainers of org.postgresql.jdbc.TimestampUtils
	WARNING: Use --illegal-access=warn to enable warnings of further illegal reflective access operations
	WARNING: All illegal access operations will be denied in a future release

Apparently this is nothing to worry about.

## To use

Create a target profile:

	term2$ curl  -d'{"url":"z3950.indexdata.com/marc", "name":"ID MARC test"}' -HX-Okapi-Tenant:testlib "-HAccept:*/*" -HContent-Type:application/json http://localhost:8081/copycat/target-profiles
	{
	  "id" : "6a69ab96-51af-4827-bb7d-7a9cf2f1a8cf",
	  "name" : "ID MARC test",
	  "url" : "z3950.indexdata.com/marc"
	}
	term$

List all target profiles:

	term2$ curl   -HX-Okapi-Tenant:testlib "-HAccept:*/*" -HContent-Type:application/json http://localhost:8081/copycat/target-profiles
	{
	  "targetprofiles" : [ {
	    "id" : "6a69ab96-51af-4827-bb7d-7a9cf2f1a8cf",
	    "name" : "ID MARC test",
	    "url" : "z3950.indexdata.com/marc"
	  } ],
	  "totalRecords" : 1
	}
	term2$ 

Import a record based on an external identifier (OCLC number):

	term2$ curl -d'{"externalIdentifier":"isbn123", "targetProfileId":"1d0489ab-3989-4f0b-b535-725bebf21373"}'   -HX-Okapi-Tenant:testlib "-HAccept:*/*" -HContent-Type:application/json http://localhost:8081/copycat/imports
	Not implemented
	term2$ 

## Integrating into a FOLIO-backend Vagrant box

XXX to be written -- use snapshot-backend-core

