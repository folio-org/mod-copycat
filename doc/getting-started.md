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

Create the configuration file for a FOLIO core backend VM:

	$ vagrant init folio/snapshot-backend-core

Now start the VM for the first time. This will take some time, as it will need to download the VM image:

	$ vagrant up

Once the VM is running, there are several approaches that can be taken:
* [Use a "host-only" private network in VirtualBox](https://github.com/folio-org/folio-ansible/blob/master/doc/index.md#running-backend-modules-on-your-host-system) to enable Okapi on the VM to contact mod-copycat on the host.
* [Use an SSH tunnel](https://github.com/folio-org/mod-graphql/blob/master/doc/developing-with-a-vagrant-box.md#b-run-mod-graphql-in-the-host-box) to enable Okapi on the VM to contact mod-copycat on the host.
* [Run mod-copycat inside the Vagrant box](https://github.com/folio-org/mod-graphql/blob/master/doc/developing-with-a-vagrant-box.md#a-run-mod-graphql-inside-the-vagrant-box). The linked document talks about running mod-graphql in this way, but the approach is the same. Note that if you use this approach, then the instance of the module running inside the VM must be initialised and populated: it does not share the database with any instance running in the host machine.

Whichever of these approaches you use, you now need to tell Okapi about the copycat module: load the module, deploy it, and associated it with the `diku` tenant:

	curl -w '\n' -d @target/ModuleDescriptor.json http://localhost:9130/_/proxy/modules
	curl -w '\n' -d @target/DeploymentDescriptor.VM.json http://localhost:9130/_/discovery/modules
	curl -w '\n' -d '[{ "action": "enable", "id": "mod-copycat-1.0.0-SNAPSHOT" }]' http://localhost:9130/_/proxy/tenants/diku/install

Now login and you will be able to access mod-copycat via Okapi. Use the `X-Okapi-Token` from the response headers echoed by the `-D -` option to the login operation:

	curl -w '\n' -D - -X POST -d '{"username":"diku_admin","password":"********"}' -H "Content-type: application/json" -H "Accept: application/json" -H "x-okapi-tenant: diku" http://localhost:9130/authn/login
	curl -w '\n' -H "X-Okapi-Tenant: diku" -H "X-Okapi-Token: XXXXXXXX" -H "Accept: */*" -H "Content-Type: application/json" http://localhost:9130/copycat/target-profiles

