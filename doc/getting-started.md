 Building, running, initializing and using mod-copycat

## Prerequisites

You need
[yaz4j](https://github.com/indexdata/yaz4j)
and
[yaz](https://www.indexdata.com/yaz)
before you can compile and install mod-copycat.

	term1$ sudo apt install yaz
	term1$ git clone https://github.com/indexdata/yaz4j.git
	term1$ git checkout d7cd6967d297c92c179d9896b0150f7509f789f8
	term1$ cd yaz4j

The install below whould install `yaz4j-1.6-SNAPSHOT.jar` in your Maven repository.

	term1$ mvn -B install

This also produces a native DLL/shared object file.. This needs to be available when
using the yaz4j jar. Google for `java.library.path` to see where it could be
installed on your platform. On Ubuntu/Debian amd64 architecture, it suffices to
copy like this:

	term1$ sudo cp unix/target/libyaz4j.so /usr/lib/x86_64-linux-gnu/

## To build mod-copycat

	term1$ cd mod-copycat
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

See https://github.com/folio-org/folio-ansible/blob/master/doc/index.md#running-backend-modules-on-your-host-system
and https://github.com/folio-org/mod-graphql/blob/master/doc/developing-with-a-vagrant-box.md

